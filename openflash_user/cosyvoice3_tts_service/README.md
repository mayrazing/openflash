# CosyVoice3 TTS Service

本服务为 Pick Word 提供本地 CosyVoice3 0.5B RL 语音合成. HTTP 接口供同机 Spring 后端调用.

## 本机环境

- Python: 3.10
- GPU: RTX 4070 Laptop 8GB
- 模型: `Fun-CosyVoice3-0.5B-2512` 的 `llm.rl.pt`
- 推理: CUDA FP16, 语音 tokenizer 使用 CPU ONNX Runtime

## 安装

先安装依赖:

```bash
conda run -n py310 python -m pip install -r openflash_user/cosyvoice3_tts_service/requirements.txt
```

再下载并准备固定版本的 CosyVoice3 源码和模型:

```bash
conda run -n py310 python openflash_user/cosyvoice3_tts_service/prepare_runtime.py
```

运行时默认保存在 `${XDG_DATA_HOME:-$HOME/.local/share}/openflash/cosyvoice3`, 体积约 9GB, 不在 Git 项目内. `prepare_runtime.py` 固定了源码和模型版本, 并自动完成 3 个必要调整:

- CosyVoice3 加载 `llm.rl.pt`.
- LLM, Flow, HiFT 权重先在 CPU 加载并转 FP16, 再放入 GPU.
- speech tokenizer 固定使用 CPU ONNX Runtime, 避免额外占用显存.
- 不安装可选的 `wetext`, 避免英文单词服务启动时联网下载规范化资源.

## 启动

```bash
cd openflash_user/cosyvoice3_tts_service
${XDG_DATA_HOME:-$HOME/.local/share}/openflash/cosyvoice3/python/bin/python \
  -m uvicorn app:app --host 127.0.0.1 --port 8888
```

也可以通过项目的 `openflash_user/start-dev.sh` 一起启动.

## 接口

```http
GET /health
```

```http
POST /synthesize
Content-Type: application/json

{
  "text": "feed",
  "voice": "default",
  "speed": 1.0,
  "accent": "american"
}
```

成功响应为 24kHz `audio/wav`. `voice` 字段只为兼容旧调用保留, 当前音色和美式英语口音由固定参考音频决定. `accent` 当前只支持 `american`. 独立单词使用 CMU 默认美式音素, 并通过 CosyVoice3 `inference_cross_lingual` 合成. 服务最多尝试 5 个固定候选, 自动选择没有尾部能量断崖的一版.
