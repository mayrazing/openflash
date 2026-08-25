from io import BytesIO
import re
import unittest
from unittest.mock import patch

import librosa
import numpy as np
import soundfile as sf
from fastapi.testclient import TestClient
import whisper

try:
    import app as app_module
    app = app_module.app
except ModuleNotFoundError:
    app_module = None
    app = None


class CosyVoice3ApiTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.client = TestClient(app) if app is not None else None

    def require_client(self) -> TestClient:
        self.assertIsNotNone(self.client, "CosyVoice3 FastAPI service is missing")
        return self.client

    def test_health_loads_real_rl_model(self) -> None:
        response = self.require_client().get("/health")

        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual({"status": "ok", "engine": "cosyvoice3-rl-fp16"}, response.json())

    def test_synthesize_accepts_legacy_payload_and_returns_24khz_wav(self) -> None:
        response = self.require_client().post(
            "/synthesize",
            json={"text": "feed", "voice": "af_heart", "speed": 1.0},
        )

        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual("audio/wav", response.headers["content-type"])
        audio, sample_rate = sf.read(BytesIO(response.content), dtype="float32")
        self.assertEqual(24000, sample_rate)
        self.assertGreater(audio.size, 0)
        self.assertTrue(np.isfinite(audio).all())

    def test_synthesize_rejects_blank_text(self) -> None:
        response = self.require_client().post(
            "/synthesize",
            json={"text": "   ", "voice": "af_heart", "speed": 1.0},
        )

        self.assertEqual(400, response.status_code, response.text)
        self.assertEqual("text is required", response.json()["detail"])

    def test_synthesize_ignores_removed_engine_override_and_uses_cosyvoice(self) -> None:
        self.assertIsNotNone(app_module, "CosyVoice3 FastAPI service is missing")
        with patch.object(app_module, "_synthesize_wav", return_value=b"cosyvoice-only") as synthesize:
            response = self.require_client().post(
                "/synthesize",
                json={
                    "text": "feed",
                    "voice": "default",
                    "speed": 1.0,
                    "accent": "american",
                    "engine": "espeak-ng",
                },
            )

        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual(b"cosyvoice-only", response.content)
        synthesize.assert_called_once_with("feed", 1.0, "american")

    def test_synthesize_keeps_regression_words_intelligible_with_natural_endings(self) -> None:
        recognizer = whisper.load_model("tiny.en", device="cpu")
        mismatches = []
        abrupt_endings = []
        cases = {
            "breath": {"breath"},
            "period": {"period"},
            "archive": {"archive", "arkive"},
            "Worcestershire": {"worcestershire", "worcestersure"},
            "jewelry": {"jewelry", "jewellery"},
            "regularly": {"regularly"},
            "completion": {"completion"},
            "nation": {"nation"},
            "chat completion": {"chatcompletion"},
        }
        for text, accepted_transcripts in cases.items():
            response = self.require_client().post(
                "/synthesize",
                json={"text": text, "voice": "default", "speed": 1.0, "accent": "american"},
            )
            self.assertEqual(200, response.status_code, response.text)
            audio, sample_rate = sf.read(BytesIO(response.content), dtype="float32")
            recognized = recognizer.transcribe(
                librosa.resample(audio, orig_sr=sample_rate, target_sr=16000),
                language="en",
                fp16=False,
                temperature=0,
                condition_on_previous_text=False,
            )["text"]
            normalized = re.sub(r"[^a-z]", "", recognized.lower())
            if normalized not in accepted_transcripts:
                mismatches.append(f"{text} -> {recognized.strip()}")

            frame_size = sample_rate // 100
            frame_count = audio.size // frame_size
            frames = audio[:frame_count * frame_size].reshape(frame_count, frame_size)
            rms = np.sqrt(np.mean(frames.astype(np.float64) ** 2, axis=1))
            levels = 20.0 * np.log10(np.maximum(rms, 1e-12))
            tail = levels[-30:]
            ends_audibly = levels[-1] > -50.0
            drops_abruptly = any(
                tail[index] > -50.0 and tail[index] - tail[index + 3] > 20.0
                for index in range(len(tail) - 3)
            )
            if ends_audibly or drops_abruptly:
                abrupt_endings.append(text)

        self.assertEqual([], mismatches)
        self.assertEqual([], abrupt_endings)


if __name__ == "__main__":
    unittest.main()
