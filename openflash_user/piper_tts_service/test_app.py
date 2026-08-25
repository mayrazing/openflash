from io import BytesIO
import unittest
from unittest.mock import patch
import wave

from fastapi.testclient import TestClient

import app as app_module


class _FakeVoice:
    def __init__(self) -> None:
        self.calls = []

    def synthesize_wav(self, text, wav_file, syn_config) -> None:
        self.calls.append((text, syn_config.length_scale, syn_config.speaker_id))
        wav_file.setframerate(22050)
        wav_file.setsampwidth(2)
        wav_file.setnchannels(1)
        wav_file.writeframes(b"\x00\x00" * 32)


class PiperApiTest(unittest.TestCase):

    def setUp(self) -> None:
        self.client = TestClient(app_module.app)

    def test_health_reports_fixed_piper_voice(self) -> None:
        with patch.object(app_module, "get_voice", return_value=_FakeVoice()):
            response = self.client.get("/health")

        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual(
            {"status": "ok", "engine": "piper-1.6.0", "voice": "en_US-libritts_r-medium"},
            response.json(),
        )

    def test_synthesize_returns_22050hz_wav_and_maps_speed_to_length_scale(self) -> None:
        voice = _FakeVoice()
        with patch.object(app_module, "get_voice", return_value=voice):
            response = self.client.post(
                "/synthesize",
                json={
                    "text": "classifier",
                    "voice": "ignored-for-fixed-service",
                    "speed": 2.0,
                    "accent": "american",
                },
            )

        self.assertEqual(200, response.status_code, response.text)
        self.assertEqual("audio/wav", response.headers["content-type"])
        with wave.open(BytesIO(response.content), "rb") as wav_file:
            self.assertEqual(22050, wav_file.getframerate())
            self.assertEqual(1, wav_file.getnchannels())
            self.assertGreater(wav_file.getnframes(), 0)
        self.assertEqual([("classifier", 0.5, 0)], voice.calls)

    def test_synthesize_rejects_blank_text(self) -> None:
        response = self.client.post(
            "/synthesize",
            json={"text": "   ", "speed": 1.0, "accent": "american"},
        )

        self.assertEqual(400, response.status_code, response.text)
        self.assertEqual("text is required", response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
