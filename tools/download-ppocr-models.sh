#!/usr/bin/env bash
set -euo pipefail

# Download PP-OCRv5_mobile ONNX models into app/src/main/assets/models/.
# Run from project root.

MODEL_VARIANT="${1:-pp-ocrv5_mobile}"  # or pp-ocrv6_small / pp-ocrv6_tiny
HF_BASE="https://huggingface.co/PaddlePaddle"
BOS_BASE="https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0"

DET_DIR="app/src/main/assets/models/det"
REC_DIR="app/src/main/assets/models/rec"

mkdir -p "$DET_DIR" "$REC_DIR"

download() {
    local name="$1" hf_path="$2" bos_path="$3"
    local target="$4"

    if [ -f "$target" ]; then
        echo "[download] Skip existing: $target"
        return
    fi

    if curl -fsSL --max-time 60 "${HF_BASE}/${hf_path}" -o "$target"; then
        echo "[download] OK from HF: $target"
        return
    fi

    # HF failed — drop the partial file so the next run re-attempts cleanly.
    rm -f "$target"

    if [ -z "$bos_path" ]; then
        echo "[download] ERROR: ${name} not available on HF and no BOS fallback configured" >&2
        exit 1
    fi

    echo "[download] HF failed, trying BOS..."
    if curl -fsSL --max-time 120 "${BOS_BASE}/${bos_path}" -o "${target}.tar"; then
        tar -xf "${target}.tar" -C "$(dirname "$target")"
        rm -f "${target}.tar"
        echo "[download] OK from BOS: $target"
    else
        echo "[download] ERROR: both HF and BOS failed for $name" >&2
        exit 1
    fi
}

# Translate a model-variant slug into the on-disk suffix used by PaddlePaddle's
# naming scheme (pp-ocrv6_small -> PP-OCRV6_SMALL). Wrapped in a function so
# the intermediate variable stays local.
variant_suffix() {
    local variant="$1"
    echo "$variant" | tr 'a-z-' 'A-Z_'
}

case "$MODEL_VARIANT" in
    pp-ocrv5_mobile)
        download "PP-OCRv5_mobile_det" \
            "PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx" \
            "PP-OCRv5_mobile_det_onnx_infer.tar" \
            "${DET_DIR}/inference.onnx"

        download "PP-OCRv5_mobile_rec_model" \
            "PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" \
            "PP-OCRv5_mobile_rec_onnx_infer.tar" \
            "${REC_DIR}/inference.onnx"

        download "PP-OCRv5_mobile_rec_config" \
            "PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml" \
            "" \
            "${REC_DIR}/inference.yml"
        ;;
    pp-ocrv6_small|pp-ocrv6_tiny)
        SUFFIX=$(variant_suffix "$MODEL_VARIANT")
        download "${SUFFIX}_det" \
            "${SUFFIX}_det_onnx/resolve/main/inference.onnx" \
            "${SUFFIX}_det_onnx_infer.tar" \
            "${DET_DIR}/inference.onnx"

        download "${SUFFIX}_rec_model" \
            "${SUFFIX}_rec_onnx/resolve/main/inference.onnx" \
            "${SUFFIX}_rec_onnx_infer.tar" \
            "${REC_DIR}/inference.onnx"

        download "${SUFFIX}_rec_config" \
            "${SUFFIX}_rec_onnx/resolve/main/inference.yml" \
            "" \
            "${REC_DIR}/inference.yml"
        ;;
    *)
        echo "Unknown variant: $MODEL_VARIANT" >&2
        exit 2
        ;;
esac

echo "[download] All models staged under app/src/main/assets/models/"
