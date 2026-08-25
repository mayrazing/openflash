from io import BytesIO
import logging
import os
from pathlib import Path
import sys
from threading import Lock
from typing import Any, Literal

import numpy as np
import soundfile as sf
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from pronunciation import prepare_pronunciation


LOGGER = logging.getLogger(__name__)
SERVICE_DIR = Path(__file__).resolve().parent
XDG_DATA_HOME = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
DATA_DIR = Path(
    os.environ.get("COSYVOICE3_DATA_DIR", XDG_DATA_HOME / "openflash" / "cosyvoice3")
).expanduser().resolve()
RUNTIME_DIR = Path(
    os.environ.get("COSYVOICE3_RUNTIME_DIR", DATA_DIR / "CosyVoice")
).expanduser().resolve()
MODEL_DIR = Path(
    os.environ.get(
        "COSYVOICE3_MODEL_DIR",
        RUNTIME_DIR / "pretrained_models" / "Fun-CosyVoice3-0.5B",
    )
).resolve()
PROMPT_AUDIO = Path(
    os.environ.get(
        "COSYVOICE3_PROMPT_AUDIO",
        RUNTIME_DIR / "asset" / "english_reference_okay.wav",
    )
).expanduser().resolve()
CROSS_LINGUAL_PREFIX = "You are a helpful assistant.<|endofprompt|>"
SYNTHESIS_SEEDS = range(1, 6)
ENGINE_NAME = "cosyvoice3-rl-fp16"
ENDING_FRAME_MILLIS = 10
ENDING_WINDOW_MILLIS = 300
ENDING_DROP_WINDOW_MILLIS = 30
AUDIBLE_END_DBFS = -50.0
ABRUPT_DROP_DB = 20.0

app = FastAPI(title="Pick Word CosyVoice3 TTS")

_model: Any | None = None
_model_lock = Lock()
_synthesis_lock = Lock()


class SynthesizeRequest(BaseModel):
    text: str = Field(min_length=1, max_length=500)
    # 保留 voice 字段兼容现有 Spring 请求. 当前服务固定使用同一个短参考音频.
    voice: str = Field(default="default", min_length=1)
    speed: float = Field(default=1.0, gt=0.1, le=3.0)
    accent: Literal["american"] = "american"


def _add_runtime_to_python_path() -> None:
    """把本地 CosyVoice3 和 Matcha-TTS 源码加入当前进程导入路径."""
    for path in (RUNTIME_DIR, RUNTIME_DIR / "third_party" / "Matcha-TTS"):
        path_text = str(path)
        if path_text not in sys.path:
            sys.path.insert(0, path_text)


def _load_model() -> Any:
    """加载适合 8GB 显存的 CosyVoice3 RL FP16 模型."""
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is required for CosyVoice3 RL inference")
    if not (RUNTIME_DIR / "cosyvoice" / "cli" / "cosyvoice.py").is_file():
        raise RuntimeError(f"CosyVoice3 runtime is missing: {RUNTIME_DIR}")
    if not (MODEL_DIR / "llm.rl.pt").is_file():
        raise RuntimeError(f"CosyVoice3 RL model is missing: {MODEL_DIR}")
    if not PROMPT_AUDIO.is_file():
        raise RuntimeError(f"CosyVoice3 prompt audio is missing: {PROMPT_AUDIO}")

    _add_runtime_to_python_path()
    from cosyvoice.cli.cosyvoice import AutoModel

    return AutoModel(model_dir=str(MODEL_DIR), fp16=True)


def get_model() -> Any:
    """返回进程内唯一的已加载模型."""
    global _model
    if _model is None:
        with _model_lock:
            if _model is None:
                _model = _load_model()
    return _model


def _set_random_seed(seed: int) -> None:
    """固定 CosyVoice3 采样结果, 避免同一单词每次发音不同."""
    from cosyvoice.utils.common import set_all_random_seed

    set_all_random_seed(seed)


def _infer_cross_lingual_audio(
    model: Any,
    synthesis_text: str,
    speed: float,
    use_text_frontend: bool,
    accent: str,
    seed: int,
) -> np.ndarray:
    """使用固定美音参考和 cross-lingual 音素路径生成单声道波形."""
    if accent != "american":
        raise ValueError(f"Unsupported TTS accent: {accent}")
    _set_random_seed(seed)
    chunks = list(
        model.inference_cross_lingual(
            CROSS_LINGUAL_PREFIX + synthesis_text,
            str(PROMPT_AUDIO),
            stream=False,
            speed=speed,
            text_frontend=use_text_frontend,
        )
    )
    if not chunks:
        raise RuntimeError("CosyVoice3 returned empty audio")

    waveform = torch.cat([chunk["tts_speech"].detach().cpu() for chunk in chunks], dim=1)
    return waveform.squeeze(0).float().numpy()


def _ending_abruptness(audio: np.ndarray, sample_rate: int) -> float:
    """返回尾部可听电平和 30ms 累计能量断崖的最大超限值."""
    frame_size = max(1, sample_rate * ENDING_FRAME_MILLIS // 1000)
    drop_frame_count = max(1, ENDING_DROP_WINDOW_MILLIS // ENDING_FRAME_MILLIS)
    window_size = max(
        frame_size * (drop_frame_count + 1),
        sample_rate * ENDING_WINDOW_MILLIS // 1000,
    )
    tail = audio[-window_size:]
    frame_count = tail.size // frame_size
    if frame_count <= drop_frame_count:
        return float("inf")

    frames = tail[-frame_count * frame_size:].reshape(frame_count, frame_size).astype(np.float64)
    rms = np.sqrt(np.mean(frames * frames, axis=1))
    levels = 20.0 * np.log10(np.maximum(rms, 1e-12))
    score = max(0.0, float(levels[-1] - AUDIBLE_END_DBFS))
    for index in range(frame_count - drop_frame_count):
        before = levels[index]
        after = levels[index + drop_frame_count]
        if before > AUDIBLE_END_DBFS:
            score = max(score, float(before - after - ABRUPT_DROP_DB))
    return score


def _synthesize_wav(text: str, speed: float, accent: str = "american") -> bytes:
    """生成 24kHz WAV,并从固定候选中选择自然收尾的一版."""
    model = get_model()
    synthesis_text, use_text_frontend = prepare_pronunciation(text)
    with _synthesis_lock, torch.inference_mode():
        audio = None
        best_score = float("inf")
        last_error = None
        for seed in SYNTHESIS_SEEDS:
            try:
                candidate = _infer_cross_lingual_audio(
                    model,
                    synthesis_text,
                    speed,
                    use_text_frontend,
                    accent,
                    seed,
                )
            except Exception as exc:
                last_error = exc
                LOGGER.warning("CosyVoice3 candidate generation failed: seed=%s", seed, exc_info=True)
                continue
            score = _ending_abruptness(candidate, model.sample_rate)
            if score < best_score:
                audio = candidate
                best_score = score
            if score <= 0.0:
                break
        if audio is None:
            raise RuntimeError("CosyVoice3 returned no usable audio") from last_error

    wav = BytesIO()
    sf.write(wav, audio, model.sample_rate, format="WAV")
    return wav.getvalue()


@app.get("/health")
def health() -> dict[str, str]:
    try:
        get_model()
    except Exception as exc:
        raise HTTPException(status_code=503, detail="cosyvoice3 is not ready") from exc
    return {"status": "ok", "engine": ENGINE_NAME}


@app.post("/synthesize")
def synthesize(request: SynthesizeRequest) -> Response:
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is required")

    try:
        audio = _synthesize_wav(text, request.speed, request.accent)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="cosyvoice3 synthesis failed") from exc
    return Response(content=audio, media_type="audio/wav")
