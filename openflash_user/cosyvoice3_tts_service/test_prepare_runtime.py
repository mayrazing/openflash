from pathlib import Path
import re
from tempfile import TemporaryDirectory
import unittest

try:
    from prepare_runtime import apply_runtime_patches
except ModuleNotFoundError:
    apply_runtime_patches = None


class PrepareRuntimeTest(unittest.TestCase):

    def test_start_dev_preflight_checks_pronunciation_dependency(self) -> None:
        start_dev = Path(__file__).resolve().parent.parent / "start-dev.sh"
        source = start_dev.read_text()

        self.assertRegex(
            source,
            re.compile(r'-c "import [^"]*\bcmudict\b[^"]*; raise SystemExit'),
        )

    def test_applies_rl_fp16_and_cpu_tokenizer_patches(self) -> None:
        self.assertIsNotNone(apply_runtime_patches, "CosyVoice3 runtime preparer is missing")
        with TemporaryDirectory() as temporary_directory:
            runtime_dir = Path(temporary_directory)
            cli_dir = runtime_dir / "cosyvoice" / "cli"
            cli_dir.mkdir(parents=True)
            (cli_dir / "cosyvoice.py").write_text(
                "        self.model = CosyVoice3Model(configs['llm'], configs['flow'], configs['hift'], fp16)\n"
                "        self.model.load('{}/llm.pt'.format(model_dir),\n"
                "                        '{}/flow.pt'.format(model_dir),\n"
                "                        '{}/hift.pt'.format(model_dir))\n"
            )
            (cli_dir / "model.py").write_text(
                "    def load(self, llm_model, flow_model, hift_model):\n"
                "        self.llm.load_state_dict(torch.load(llm_model, map_location=self.device, weights_only=True), strict=True)\n"
                "        self.llm.to(self.device).eval()\n"
                "        self.flow.load_state_dict(torch.load(flow_model, map_location=self.device, weights_only=True), strict=True)\n"
                "        self.flow.to(self.device).eval()\n"
                "        # in case hift_model is a hifigan model\n"
                "        hift_state_dict = {k.replace('generator.', ''): v for k, v in torch.load(hift_model, map_location=self.device, weights_only=True).items()}\n"
                "        self.hift.load_state_dict(hift_state_dict, strict=True)\n"
                "        self.hift.to(self.device).eval()\n"
            )
            (cli_dir / "frontend.py").write_text(
                "        self.speech_tokenizer_session = onnxruntime.InferenceSession(speech_tokenizer_model, sess_options=option,\n"
                "                                                                     providers=[\"CUDAExecutionProvider\" if torch.cuda.is_available() else\n"
                "                                                                                \"CPUExecutionProvider\"])\n"
            )

            apply_runtime_patches(runtime_dir)
            apply_runtime_patches(runtime_dir)

            cosyvoice_source = (cli_dir / "cosyvoice.py").read_text()
            model_source = (cli_dir / "model.py").read_text()
            frontend_source = (cli_dir / "frontend.py").read_text()
            self.assertIn("llm.rl.pt", cosyvoice_source)
            self.assertEqual(3, model_source.count(".half()"))
            self.assertEqual(3, model_source.count("map_location='cpu'"))
            self.assertIn('providers=["CPUExecutionProvider"]', frontend_source)
            self.assertNotIn("CUDAExecutionProvider", frontend_source)


if __name__ == "__main__":
    unittest.main()
