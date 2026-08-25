from io import BytesIO
import unittest
from unittest.mock import patch

import numpy as np
import soundfile as sf
import torch

import app as service


def natural_audio() -> torch.Tensor:
    return torch.cat(
        [
            torch.full((1, 1200), 0.1, dtype=torch.float32),
            torch.logspace(-1, -4, 3600, dtype=torch.float32).unsqueeze(0),
            torch.zeros((1, 2400), dtype=torch.float32),
        ],
        dim=1,
    )


def abrupt_audio(amplitude: float = 0.1) -> torch.Tensor:
    return torch.full((1, 2400), amplitude, dtype=torch.float32)


class RecordingModel:
    sample_rate = 24000

    def __init__(self, speeches=None, fail_on_calls=None) -> None:
        self.speeches = list(speeches or [natural_audio()])
        self.fail_on_calls = set(fail_on_calls or [])
        self.cross_lingual_texts = []
        self.zero_shot_texts = []
        self.instruct_texts = []
        self.text_frontends = []

    def inference_cross_lingual(
        self,
        text,
        prompt_audio,
        stream,
        speed,
        text_frontend=True,
    ):
        call_number = len(self.cross_lingual_texts) + 1
        self.cross_lingual_texts.append(text)
        self.text_frontends.append(text_frontend)
        if call_number in self.fail_on_calls:
            raise RuntimeError("candidate failed")
        speech = self.speeches[min(call_number - 1, len(self.speeches) - 1)]
        return [{"tts_speech": speech}]

    def inference_zero_shot(self, text, *args, **kwargs):
        self.zero_shot_texts.append(text)
        return [{"tts_speech": natural_audio()}]

    def inference_instruct2(self, text, *args, **kwargs):
        self.instruct_texts.append(text)
        return [{"tts_speech": natural_audio()}]


class SynthesisInputTest(unittest.TestCase):
    def test_sends_short_phrase_through_cross_lingual_path(self) -> None:
        model = RecordingModel()

        with (
            patch.object(service, "get_model", return_value=model),
            patch.object(service, "_set_random_seed", create=True) as set_random_seed,
        ):
            service._synthesize_wav("chat completion", 1.0)

        self.assertEqual(
            ["You are a helpful assistant.<|endofprompt|>chat completion"],
            model.cross_lingual_texts,
        )
        set_random_seed.assert_called_once_with(1)
        self.assertEqual([True], model.text_frontends)
        self.assertEqual([], model.zero_shot_texts)
        self.assertEqual([], model.instruct_texts)

    def test_sends_default_cmu_pronunciation_through_cross_lingual_path(self) -> None:
        model = RecordingModel()

        with (
            patch.object(service, "get_model", return_value=model),
            patch.object(service, "_set_random_seed", create=True),
        ):
            service._synthesize_wav("record", 1.0)

        self.assertEqual(
            ["You are a helpful assistant.<|endofprompt|>[R][AH0][K][AO1][R][D]"],
            model.cross_lingual_texts,
        )
        self.assertEqual([False], model.text_frontends)

    def test_retries_with_next_seed_until_ending_is_natural(self) -> None:
        model = RecordingModel([abrupt_audio(), natural_audio()])

        with (
            patch.object(service, "get_model", return_value=model),
            patch.object(service, "_set_random_seed", create=True) as set_random_seed,
        ):
            wav = service._synthesize_wav("completion", 1.0)

        self.assertEqual(2, len(model.cross_lingual_texts))
        self.assertEqual([unittest.mock.call(1), unittest.mock.call(2)], set_random_seed.call_args_list)
        audio, sample_rate = sf.read(BytesIO(wav), dtype="float32")
        self.assertEqual(24000, sample_rate)
        self.assertTrue((audio[-2400:] == 0).all())

    def test_keeps_best_candidate_when_later_generation_fails(self) -> None:
        model = RecordingModel([abrupt_audio(0.03)], fail_on_calls={2, 3, 4, 5})

        with (
            patch.object(service, "get_model", return_value=model),
            patch.object(service, "_set_random_seed", create=True),
            patch.object(service, "LOGGER", create=True),
        ):
            wav = service._synthesize_wav("completion", 1.0)

        audio, _ = sf.read(BytesIO(wav), dtype="float32")
        self.assertEqual(5, len(model.cross_lingual_texts))
        self.assertLess(float(audio.max()), 0.05)

    def test_detects_a_thirty_millisecond_staircase_drop(self) -> None:
        levels_db = (-20.0, -30.0, -41.0, -60.0, -90.0)
        frames = [
            np.full(240, 10.0 ** (level / 20.0), dtype=np.float32)
            for level in levels_db
        ]
        audio = np.concatenate([np.zeros(6000, dtype=np.float32), *frames])

        self.assertGreater(service._ending_abruptness(audio, 24000), 0.0)


if __name__ == "__main__":
    unittest.main()
