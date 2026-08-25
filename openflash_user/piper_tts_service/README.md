# Piper TTS Service

本服务为 OpenFlash 提供本地 Piper 美式英语语音合成. HTTP 接口供同机 Spring 后端调用.

## 运行环境

- Python: `normal` Conda 环境的 Python 3.12
- 引擎: Piper 1.6.0, CPU ONNX Runtime
- Voice: `en_US-libritts_r-medium`, speaker 0, 22,050Hz, 单声道

## 安装

```bash
conda run -n normal python -m pip install -r openflash_user/piper_tts_service/requirements.txt
conda run -n normal python openflash_user/piper_tts_service/prepare_runtime.py
```

voice 默认保存到 `${XDG_DATA_HOME:-$HOME/.local/share}/openflash/piper`, 不进入 Git 仓库. `prepare_runtime.py` 使用固定 Hugging Face revision 和 SHA-256, 并在数据目录创建指向所选 Python 环境的 `python` 链接.

## 启动

```bash
cd openflash_user/piper_tts_service
${XDG_DATA_HOME:-$HOME/.local/share}/openflash/piper/python/bin/python \
  -m uvicorn app:app --host 127.0.0.1 --port 8889
```

也可以通过 `openflash_user/start-dev.sh` 和其他用户端服务一起启动.

## 接口

```http
GET /health
```

```http
POST /synthesize
Content-Type: application/json

{
  "text": "classifier",
  "voice": "en_US-libritts_r-medium",
  "speed": 1.0,
  "accent": "american"
}
```

成功响应为 22,050Hz `audio/wav`. 服务固定使用 `en_US-libritts_r-medium` 的 speaker 0; `voice` 字段只为兼容 Spring 的统一请求格式保留.
