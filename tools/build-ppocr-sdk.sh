#!/usr/bin/env bash
set -euo pipefail

# Build PaddleOCR official ppocr-sdk AAR (v3.7.0).
# Output: app/libs/ppocr-sdk.aar (gitignored).
# Run from project root.

REPO_DIR="tools/paddleocr"
REPO_URL="https://github.com/PaddlePaddle/PaddleOCR.git"
REPO_TAG="v3.7.0"
SDK_DIR="${REPO_DIR}/deploy/ppocr-android/ppocr-sdk"
AAR_OUT="app/libs/ppocr-sdk.aar"
TOPLEVEL=$(git rev-parse --show-toplevel)

if [ ! -d "$SDK_DIR" ]; then
    echo "[build-ppocr-sdk] Cloning PaddleOCR ${REPO_TAG} (sparse)..."
    git clone --depth 1 --branch "$REPO_TAG" --filter=blob:none --sparse "$REPO_URL" "$REPO_DIR"
    git -C "$REPO_DIR" sparse-checkout set deploy/ppocr-android
fi

echo "[build-ppocr-sdk] Building AAR..."
(
    cd "$SDK_DIR"
    ./gradlew :ppocr-sdk:assembleRelease --no-daemon

    AAR_BUILT=$(find . -name 'ppocr-sdk-release.aar' -path '*/outputs/aar/*' | head -1)
    if [ -z "$AAR_BUILT" ]; then
        echo "[build-ppocr-sdk] ERROR: AAR not found in build outputs" >&2
        exit 1
    fi

    mkdir -p "${TOPLEVEL}/app/libs"
    cp "$AAR_BUILT" "${TOPLEVEL}/${AAR_OUT}"
)

echo "[build-ppocr-sdk] Done. AAR at ${AAR_OUT}"
