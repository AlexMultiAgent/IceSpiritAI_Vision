#!/usr/bin/env bash
# 从 Gitea Model 仓库下 matcha-zh-baker 模型文件,给 build-icespirit-tts-engine.sh 用。
# 占位实现:仅 echo 期望路径。
set -euo pipefail
MODEL_DIR="${ICESPIRIT_TTS_MODELS:-$HOME/.cache/icespirit-tts-models}"
mkdir -p "$MODEL_DIR"
echo "[scaffold] download matcha-zh-baker → $MODEL_DIR"
echo "[scaffold] TODO: 实现从 http://125.211.45.14:3000/giteaadmin/Model/releases/download/sherpa-onnx-matcha-zh-baker/{model-steps-3.onnx,vocos-22khz-univ.onnx,tokens.txt,configuration.json}"
