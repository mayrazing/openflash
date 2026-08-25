import argparse
import hashlib
import os
from pathlib import Path
import sys
from tempfile import NamedTemporaryFile
from typing import Callable
from urllib.request import Request, urlopen


VOICE_REVISION = "ea046e8458f6acd997706d6e6066a022b42f6fb1"
VOICE_NAME = "en_US-libritts_r-medium"
VOICE_BASE_URL = (
    "https://huggingface.co/rhasspy/piper-voices/resolve/"
    f"{VOICE_REVISION}/en/en_US/libritts_r/medium"
)
VOICE_FILES = (
    (
        f"{VOICE_NAME}.onnx",
        "10bb85e071d616fcf4071f369f1799d0491492ab3c5d552ec19fb548fac13195",
    ),
    (
        f"{VOICE_NAME}.onnx.json",
        "b471dc60d2d8335e819c393d196d6fbf792817f40051257b269878505bc9afb3",
    ),
)


def default_data_dir() -> Path:
    """返回 Piper voice 和 Python 环境链接的仓库外目录."""
    xdg_data_home = os.environ.get("XDG_DATA_HOME")
    data_home = Path(xdg_data_home) if xdg_data_home else Path.home() / ".local" / "share"
    return data_home / "openflash" / "piper"


def download_file(
    url: str,
    destination: Path,
    expected_sha256: str,
    opener=urlopen,
) -> None:
    """下载固定 voice 文件, 校验 SHA-256 后原子放入目标路径."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = Request(url, headers={"User-Agent": "openflash-piper-preparer"})
    temporary_path = None
    try:
        with NamedTemporaryFile(
            dir=destination.parent,
            prefix=f".{destination.name}.",
            suffix=".part",
            delete=False,
        ) as output:
            temporary_path = Path(output.name)
            digest = hashlib.sha256()
            with opener(request) as response:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
                    digest.update(chunk)
        if digest.hexdigest() != expected_sha256:
            raise RuntimeError(f"Piper voice checksum mismatch: {url}")
        temporary_path.replace(destination)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def verify_file_checksum(path: Path, expected_sha256: str) -> None:
    """校验已存在或刚下载的 voice 文件, 防止损坏文件被重复复用."""
    digest = hashlib.sha256()
    with path.open("rb") as input_file:
        while chunk := input_file.read(1024 * 1024):
            digest.update(chunk)
    if digest.hexdigest() != expected_sha256:
        raise RuntimeError(f"Piper voice checksum mismatch: {path}")


def prepare(
    data_dir: Path,
    current_environment: Path | None = None,
    downloader: Callable[[str, Path, str], None] = download_file,
) -> None:
    """准备 start-dev.sh 可直接使用的固定 Piper voice 和 Python 链接."""
    data_dir.mkdir(parents=True, exist_ok=True)
    for file_name, checksum in VOICE_FILES:
        destination = data_dir / file_name
        if destination.is_file():
            try:
                verify_file_checksum(destination, checksum)
            except RuntimeError:
                destination.unlink()
        if not destination.is_file():
            downloader(f"{VOICE_BASE_URL}/{file_name}?download=true", destination, checksum)
        verify_file_checksum(destination, checksum)

    python_link = data_dir / "python"
    selected_environment = (current_environment or Path(sys.prefix)).resolve()
    if python_link.is_symlink() and python_link.resolve() == selected_environment:
        pass
    elif python_link.exists() or python_link.is_symlink():
        raise RuntimeError(
            f"Python link already points elsewhere: {python_link}. "
            "Remove it explicitly or set PIPER_PYTHON."
        )
    else:
        python_link.symlink_to(selected_environment, target_is_directory=True)

    print(f"Piper voice ready: {data_dir / (VOICE_NAME + '.onnx')}")
    print(f"Piper Python ready: {python_link / 'bin' / 'python'}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare the fixed OpenFlash Piper voice")
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=default_data_dir(),
        help="Repository-external Piper runtime directory",
    )
    arguments = parser.parse_args()
    prepare(arguments.data_dir.expanduser().resolve())


if __name__ == "__main__":
    main()
