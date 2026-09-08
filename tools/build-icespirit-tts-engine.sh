#!/usr/bin/env bash
# 打包 icespirit-tts-engine 独立 APK(sherpa-onnx + matcha-zh-baker)。
# 占位实现:仅校验前置 + echo。
set -euo pipefail
bash "$(dirname "$0")/download-matcha-zh-baker.sh"
echo "[scaffold] TODO: 实现独立 Gradle 工程 engine/ 的 assembleRelease"
echo "[scaffold] 产出: build/outputs/apk/release/icespirit-tts-engine.apk"
