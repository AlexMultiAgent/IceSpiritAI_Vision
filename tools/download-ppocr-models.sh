#!/usr/bin/env bash
set -euo pipefail

# Download PP-OCR ONNX models into app/src/main/assets/models/.
# Run from project root.
#
# v6_small is the default since 2026-08-20 (A/B test: +12% lines / +5% conf
# / 5x rule-hit vs v5_mobile on 4 real ad posters). Override to v5 by
# passing `pp-ocrv5_mobile` as the arg if a regression rollback is needed.

MODEL_VARIANT="${1:-pp-ocrv6_small}"  # or pp-ocrv5_mobile / pp-ocrv6_tiny
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
        tar -xf "${target}.tar" --strip-components=1 -C "$(dirname "$target")"
        rm -f "${target}.tar"
        echo "[download] OK from BOS: $target"
    else
        echo "[download] ERROR: both HF and BOS failed for $name" >&2
        exit 1
    fi
}

# Run the per-variant download plan. Wrapped in a function so that
# branch-local variables (e.g. SUFFIX) stay out of global shell scope.
download_for_variant() {
    local variant="$1"
    local suffix
    case "$variant" in
        pp-ocrv5_mobile)
            download "PP-OCRv5_mobile_det" \
                "PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx" \
                "PP-OCRv5_mobile_det_onnx_infer.tar" \
                "${DET_DIR}/inference.onnx"

            download "PP-OCRv5_mobile_rec_model" \
                "PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" \
                "PP-OCRv5_mobile_rec_onnx_infer.tar" \
                "${REC_DIR}/inference.onnx"

            # The v5_mobile rec tarball already bundles inference.yml (verified),
            # so --strip-components=1 above drops the yml straight into ${REC_DIR}.
            # The separate HF-only download call was redundant and would fail
            # when HF is blocked (no BOS fallback for yml).
            ;;
        pp-ocrv6_small|pp-ocrv6_tiny)
            # PP-OCRv6 HF repos and BOS buckets are case-sensitive and use the
            # mixed-case slug `PP-OCRv6_<size>` (verified: both HF 302 and
            # BOS 200). `tr 'a-z-' 'A-Z_'` collapses the hyphen to `_`,
            # producing `PP_OCRV6_SMALL` which 401s on HF and 404s on BOS.
            suffix="PP-OCRv6_${variant#pp-ocrv6_}"
            download "${suffix}_det" \
                "${suffix}_det_onnx/resolve/main/inference.onnx" \
                "${suffix}_det_onnx_infer.tar" \
                "${DET_DIR}/inference.onnx"

            download "${suffix}_rec_model" \
                "${suffix}_rec_onnx/resolve/main/inference.onnx" \
                "${suffix}_rec_onnx_infer.tar" \
                "${REC_DIR}/inference.onnx"

            download "${suffix}_rec_config" \
                "${suffix}_rec_onnx/resolve/main/inference.yml" \
                "" \
                "${REC_DIR}/inference.yml"
            ;;
        *)
            echo "Unknown variant: $variant" >&2
            return 2
            ;;
    esac
}

download_for_variant "$MODEL_VARIANT"

echo "[download] All models staged under app/src/main/assets/models/"
