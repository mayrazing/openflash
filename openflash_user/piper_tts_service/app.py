from io import BytesIO
import os
from pathlib import Path
from threading import Lock
from typing import Literal
import wave

from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from piper import PiperVoice
from piper.config import SynthesisConfig
from pydantic import BaseModel, Field


XDG_DATA_HOME = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
DATA_DIR = Path(
    os.environ.get("PIPER_DATA_DIR", XDG_DATA_HOME / "openflash" / "piper")
).expanduser().resolve()
VOICE_NAME = "en_US-libritts_r-medium"
SPEAKER_ID = 0
MODEL_PATH = Path(
    os.environ.get("PIPER_MODEL_PATH", DATA_DIR / f"{VOICE_NAME}.onnx")
).expanduser().resolve()
CONFIG_PATH = Path(
    os.environ.get("PIPER_CONFIG_PATH", DATA_DIR / f"{VOICE_NAME}.onnx.json")
).expanduser().resolve()
ENGINE_NAME = "piper-1.6.0"

app = FastAPI(title="OpenFlash Piper TTS")

_voice: PiperVoice | None = None
_voice_lock = Lock()
_synthesis_lock = Lock()


class SynthesizeRequest(BaseModel):
    text: str = Field(min_length=1, max_length=500)
    # 保留 voice 字段兼容 Spring 的统一上游请求格式. 服务固定使用 LibriTTS-R speaker 0.
    voice: str = Field(default=VOICE_NAME, min_length=1)
    speed: float = Field(default=1.0, gt=0.1, le=3.0)
    accent: Literal["american"] = "american"


def _load_voice() -> PiperVoice:
    """加载固定的 en_US-libritts_r-medium ONNX voice."""
    if not MODEL_PATH.is_file() or not CONFIG_PATH.is_file():
        raise RuntimeError(f"Piper voice is missing: {MODEL_PATH}")
    return PiperVoice.load(MODEL_PATH, CONFIG_PATH, use_cuda=False)


def get_voice() -> PiperVoice:
    """返回进程内唯一的 Piper voice 实例."""
    global _voice
    if _voice is None:
        with _voice_lock:
            if _voice is None:
                _voice = _load_voice()
    return _voice


def _synthesize_wav(text: str, speed: float) -> bytes:
    """使用固定 voice 生成 16-bit 单声道 WAV."""
    voice = get_voice()
    synthesis_config = SynthesisConfig(
        speaker_id=SPEAKER_ID,
        length_scale=1.0 / speed,
    )
    output = BytesIO()
    with _synthesis_lock, wave.open(output, "wb") as wav_file:
        voice.synthesize_wav(text, wav_file, synthesis_config)
    return output.getvalue()


@app.get("/health")
def health() -> dict[str, str]:
    try:
        get_voice()
    except Exception as exc:
        raise HTTPException(status_code=503, detail="piper is not ready") from exc
    return {"status": "ok", "engine": ENGINE_NAME, "voice": VOICE_NAME}


@app.post("/synthesize")
def synthesize(request: SynthesizeRequest) -> Response:
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is required")
    try:
        audio = _synthesize_wav(text, request.speed)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="piper synthesis failed") from exc
    return Response(content=audio, media_type="audio/wav")
