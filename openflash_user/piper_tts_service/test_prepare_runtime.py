from io import BytesIO
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import prepare_runtime
from prepare_runtime import download_file, prepare


class PrepareRuntimeTest(unittest.TestCase):

    def test_download_file_rejects_checksum_mismatch(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "voice.onnx"

            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                download_file(
                    "https://example.test/voice.onnx",
                    destination,
                    "0" * 64,
                    opener=lambda _request: BytesIO(b"voice"),
                )

            self.assertFalse(destination.exists())

    def test_prepare_downloads_voice_files_once_and_links_selected_python(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            data_dir = root / "piper"
            python_prefix = root / "normal"
            (python_prefix / "bin").mkdir(parents=True)
            (python_prefix / "bin" / "python").write_bytes(b"python")
            downloaded = []
            contents = {
                "en_US-libritts_r-medium.onnx": b"model",
                "en_US-libritts_r-medium.onnx.json": b"config",
            }
            voice_files = tuple(
                (name, hashlib.sha256(content).hexdigest())
                for name, content in contents.items()
            )

            def fake_download(url, destination, expected_sha256):
                downloaded.append((url, destination.name, expected_sha256))
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(contents[destination.name])

            with patch.object(prepare_runtime, "VOICE_FILES", voice_files):
                prepare(data_dir, current_environment=python_prefix, downloader=fake_download)
                prepare(data_dir, current_environment=python_prefix, downloader=fake_download)

            self.assertEqual(2, len(downloaded))
            self.assertTrue(all("/libritts_r/medium/" in url for url, _, _ in downloaded))
            self.assertTrue((data_dir / "en_US-libritts_r-medium.onnx").is_file())
            self.assertTrue((data_dir / "en_US-libritts_r-medium.onnx.json").is_file())
            self.assertTrue((data_dir / "python").is_symlink())
            self.assertEqual(python_prefix.resolve(), (data_dir / "python").resolve())

    def test_prepare_replaces_an_existing_voice_with_the_wrong_checksum(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            data_dir = Path(temporary_directory)
            destination = data_dir / "en_US-libritts_r-medium.onnx"
            destination.write_bytes(b"corrupt")
            expected = hashlib.sha256(b"valid").hexdigest()
            downloads = []

            def fake_download(url, target, expected_sha256):
                downloads.append((url, target, expected_sha256))
                target.write_bytes(b"valid")

            with patch.object(
                prepare_runtime,
                "VOICE_FILES",
                ((destination.name, expected),),
            ):
                prepare(
                    data_dir,
                    current_environment=data_dir / "normal",
                    downloader=fake_download,
                )

            self.assertEqual(b"valid", destination.read_bytes())
            self.assertEqual(1, len(downloads))


if __name__ == "__main__":
    unittest.main()
