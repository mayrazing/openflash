import argparse
import hashlib
import os
from pathlib import Path
import shutil
import sys
import tarfile
from tempfile import TemporaryDirectory
from urllib.request import Request, urlopen


COSYVOICE_COMMIT = "074ca6dc9e80a2f424f1f74b48bdd7d3fea531cc"
COSYVOICE_ARCHIVE_URL = (
    f"https://github.com/FunAudioLLM/CosyVoice/archive/{COSYVOICE_COMMIT}.tar.gz"
)
COSYVOICE_ARCHIVE_SHA256 = "10c587f0853db27667a64a0a2dc0788b1c782cdab223682691db78702082a550"
MATCHA_COMMIT = "dd9105b34bf2be2230f4aa1e4769fb586a3c824e"
MATCHA_ARCHIVE_URL = (
    f"https://github.com/shivammehta25/Matcha-TTS/archive/{MATCHA_COMMIT}.tar.gz"
)
MATCHA_ARCHIVE_SHA256 = "59c43afae79110e4057eaa43f45b5558b556435da8fc85eab83f0c819ba3358d"
MODEL_REPOSITORY = "FunAudioLLM/Fun-CosyVoice3-0.5B-2512"
MODEL_REVISION = "29e01c4e8d000f4bcd70751be16fa94bf3d85a18"
MODEL_DIRECTORY_NAME = "Fun-CosyVoice3-0.5B"


COSYVOICE3_MODEL_ORIGINAL = """        self.model = CosyVoice3Model(configs['llm'], configs['flow'], configs['hift'], fp16)
        self.model.load('{}/llm.pt'.format(model_dir),
                        '{}/flow.pt'.format(model_dir),
                        '{}/hift.pt'.format(model_dir))
"""
COSYVOICE3_MODEL_PATCHED = COSYVOICE3_MODEL_ORIGINAL.replace("llm.pt", "llm.rl.pt")

FP16_LOAD_ORIGINAL = """    def load(self, llm_model, flow_model, hift_model):
        self.llm.load_state_dict(torch.load(llm_model, map_location=self.device, weights_only=True), strict=True)
        self.llm.to(self.device).eval()
        self.flow.load_state_dict(torch.load(flow_model, map_location=self.device, weights_only=True), strict=True)
        self.flow.to(self.device).eval()
        # in case hift_model is a hifigan model
        hift_state_dict = {k.replace('generator.', ''): v for k, v in torch.load(hift_model, map_location=self.device, weights_only=True).items()}
        self.hift.load_state_dict(hift_state_dict, strict=True)
        self.hift.to(self.device).eval()
"""
FP16_LOAD_PATCHED = """    def load(self, llm_model, flow_model, hift_model):
        self.llm.load_state_dict(torch.load(llm_model, map_location='cpu', weights_only=True), strict=True)
        if self.fp16:
            self.llm.half()
        self.llm.to(self.device).eval()
        self.flow.load_state_dict(torch.load(flow_model, map_location='cpu', weights_only=True), strict=True)
        if self.fp16:
            self.flow.half()
        self.flow.to(self.device).eval()
        # in case hift_model is a hifigan model
        hift_state_dict = {k.replace('generator.', ''): v for k, v in torch.load(hift_model, map_location='cpu', weights_only=True).items()}
        self.hift.load_state_dict(hift_state_dict, strict=True)
        if self.fp16:
            self.hift.half()
        self.hift.to(self.device).eval()
"""

CPU_TOKENIZER_ORIGINAL = """        self.speech_tokenizer_session = onnxruntime.InferenceSession(speech_tokenizer_model, sess_options=option,
                                                                     providers=[\"CUDAExecutionProvider\" if torch.cuda.is_available() else
                                                                                \"CPUExecutionProvider\"])
"""
CPU_TOKENIZER_PATCHED = """        self.speech_tokenizer_session = onnxruntime.InferenceSession(
            speech_tokenizer_model,
            sess_options=option,
            providers=[\"CPUExecutionProvider\"],
        )
"""


def default_data_dir() -> Path:
    """返回 CosyVoice3 源码、模型和 Python 环境链接的仓库外目录."""
    xdg_data_home = os.environ.get("XDG_DATA_HOME")
    data_home = Path(xdg_data_home) if xdg_data_home else Path.home() / ".local" / "share"
    return data_home / "openflash" / "cosyvoice3"


def _replace_once(path: Path, original: str, replacement: str) -> None:
    """把固定上游片段替换为 8GB 推理片段, 已替换时保持不变."""
    source = path.read_text(encoding="utf-8")
    if replacement in source:
        return
    if source.count(original) != 1:
        raise RuntimeError(f"Upstream source no longer matches the tested patch: {path}")
    path.write_text(source.replace(original, replacement), encoding="utf-8")


def apply_runtime_patches(runtime_dir: Path) -> None:
    """应用 RL 权重、真 FP16 加载和 CPU tokenizer 三个固定补丁."""
    cli_dir = runtime_dir / "cosyvoice" / "cli"
    _replace_once(
        cli_dir / "cosyvoice.py",
        COSYVOICE3_MODEL_ORIGINAL,
        COSYVOICE3_MODEL_PATCHED,
    )
    _replace_once(cli_dir / "model.py", FP16_LOAD_ORIGINAL, FP16_LOAD_PATCHED)
    _replace_once(
        cli_dir / "frontend.py",
        CPU_TOKENIZER_ORIGINAL,
        CPU_TOKENIZER_PATCHED,
    )


def _download(url: str, destination: Path, expected_sha256: str) -> None:
    """下载固定归档并校验 SHA-256."""
    request = Request(url, headers={"User-Agent": "openflash-cosyvoice3-preparer"})
    digest = hashlib.sha256()
    with urlopen(request) as response, destination.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != expected_sha256:
        raise RuntimeError(f"Archive checksum mismatch: {url}")


def _extract_archive(archive: Path, destination: Path) -> None:
    """拒绝越界路径和链接后解压固定源码归档."""
    destination = destination.resolve()
    with tarfile.open(archive, "r:gz") as source:
        for member in source.getmembers():
            target = (destination / member.name).resolve()
            if not target.is_relative_to(destination) or member.issym() or member.islnk():
                raise RuntimeError(f"Unsafe archive member: {member.name}")
        source.extractall(destination)


def _install_archive(
    url: str,
    sha256: str,
    extracted_directory_name: str,
    destination: Path,
) -> None:
    """把一个固定源码归档安装到不存在的目标目录."""
    if destination.exists():
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix="openflash-cosyvoice3-") as temporary_directory:
        temporary_path = Path(temporary_directory)
        archive = temporary_path / "source.tar.gz"
        _download(url, archive, sha256)
        _extract_archive(archive, temporary_path)
        extracted = temporary_path / extracted_directory_name
        if not extracted.is_dir():
            raise RuntimeError(f"Archive root is missing: {extracted_directory_name}")
        shutil.move(str(extracted), destination)


def prepare(data_dir: Path) -> None:
    """准备可由 start-dev.sh 直接使用的固定 CosyVoice3 运行时."""
    runtime_dir = data_dir / "CosyVoice"
    _install_archive(
        COSYVOICE_ARCHIVE_URL,
        COSYVOICE_ARCHIVE_SHA256,
        f"CosyVoice-{COSYVOICE_COMMIT}",
        runtime_dir,
    )
    _install_archive(
        MATCHA_ARCHIVE_URL,
        MATCHA_ARCHIVE_SHA256,
        f"Matcha-TTS-{MATCHA_COMMIT}",
        runtime_dir / "third_party" / "Matcha-TTS",
    )
    apply_runtime_patches(runtime_dir)

    model_dir = runtime_dir / "pretrained_models" / MODEL_DIRECTORY_NAME
    if not (model_dir / "llm.rl.pt").is_file():
        from huggingface_hub import snapshot_download

        snapshot_download(
            repo_id=MODEL_REPOSITORY,
            revision=MODEL_REVISION,
            local_dir=model_dir,
        )

    python_link = data_dir / "python"
    current_environment = Path(sys.prefix).resolve()
    if python_link.is_symlink() and python_link.resolve() == current_environment:
        pass
    elif python_link.exists() or python_link.is_symlink():
        raise RuntimeError(
            f"Python link already points elsewhere: {python_link}. "
            "Remove it explicitly or set COSYVOICE3_PYTHON."
        )
    else:
        python_link.symlink_to(current_environment, target_is_directory=True)

    print(f"CosyVoice3 runtime ready: {runtime_dir}")
    print(f"CosyVoice3 model ready: {model_dir}")
    print(f"CosyVoice3 Python ready: {python_link / 'bin' / 'python'}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare the fixed OpenFlash CosyVoice3 runtime")
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=default_data_dir(),
        help="Repository-external runtime directory",
    )
    arguments = parser.parse_args()
    prepare(arguments.data_dir.expanduser().resolve())


if __name__ == "__main__":
    main()
