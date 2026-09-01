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
    cd "${REPO_DIR}/deploy/ppocr-android"
    # P0-C001: best-effort 16KB alignment via cmake env vars. CMake honors
    # `CMAKE_SHARED_LINKER_FLAGS` / `CMAKE_C_FLAGS` / `CMAKE_CXX_FLAGS`
    # when the CMakeLists.txt doesn't explicitly override them via
    # `set(CMAKE_SHARED_LINKER_FLAGS ...)` or `target_link_options(... LINK_FLAGS ...)`.
    # PaddleOCR's upstream SDK builds its native libs through CMake
    # (deploy/ppocr-android/ppocr-sdk/src/main/cpp/CMakeLists.txt); at v3.7.0
    # the linker is configured through the standard CMake variable, so the
    # env-var hook below is sufficient. If a future SDK release moves to
    # per-target `target_link_options(... LINK_FLAGS ...)`, this hook silently
    # degrades and the post-build readelf gate below will fail loudly with
    # the offending .so path.
    #
    # See docs/smoke/2026-08-20-icevision-v6-upgrade.md for the underlying
    # 16KB page-size requirement (targetSdk 35 on Pixel 8 / Galaxy S25 /
    # Xiaomi 15 with Android 15+).
    export CMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 ${CMAKE_SHARED_LINKER_FLAGS:-}"
    export CMAKE_C_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 ${CMAKE_C_FLAGS:-}"
    export CMAKE_CXX_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 ${CMAKE_CXX_FLAGS:-}"
    ./gradlew :ppocr-sdk:assembleRelease --no-daemon

    AAR_BUILT=$(find . -name 'ppocr-sdk-release.aar' -path '*/outputs/aar/*' | head -1)
    if [ -z "$AAR_BUILT" ]; then
        echo "[build-ppocr-sdk] ERROR: AAR not found in build outputs" >&2
        exit 1
    fi

    mkdir -p "${TOPLEVEL}/app/libs"
    cp "$AAR_BUILT" "${TOPLEVEL}/${AAR_OUT}"
)

# P0-C001: 16KB native-lib alignment gate.
#
# targetSdk 35 (Android 15+) requires every PT_LOAD segment in the
# APK's native libs to be aligned to 16 KB (0x4000). On a 16 KB
# kernel device (Pixel 8 / Galaxy S25 / Xiaomi 15 with Android 15+),
# `System.loadLibrary("paddleocr_native")` against a 4 KB-aligned
# .so fails with `dlopen: "... has bad ELF segment alignment"` and
# the OCR engine silently throws `UnsatisfiedLinkError` on first
# use. Without this gate we'd ship APKs that boot fine on 4 KB
# kernels (POCO X6 Pro / nova 6 — the project's only smoke devices)
# and crash on 16 KB ones — a silent regression only catchable in
# the field.
#
# Extract the AAR, run `readelf -lW` on every arm64-v8a .so (the
# project's locked ABI per CLAUDE.md §命名一致性), and verify each
# PT_LOAD segment's `Align` column reads `0x4000`. If any segment
# is misaligned, abort with a clear error pointing to the fix
# (rebuild with -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384).
#
# `readelf` lives in NDK toolchains — fall back to system readelf
# (binutils) if NDK isn't on PATH. The output format is identical
# for our purposes (we only consume the `LOAD ... Align` column).
AAR_TMP=$(mktemp -d)
trap 'rm -rf "$AAR_TMP"' EXIT
unzip -q "${AAR_OUT}" -d "$AAR_TMP"

READELF=$(command -v readelf || true)
if [ -z "$READELF" ] && [ -n "${ANDROID_NDK_HOME:-}" ]; then
    NDK_READELF=$(find "${ANDROID_NDK_HOME}" -name 'llvm-readelf' 2>/dev/null | head -1 || true)
    if [ -n "$NDK_READELF" ]; then
        READELF="$NDK_READELF"
    fi
fi
if [ -z "$READELF" ]; then
    echo "[build-ppocr-sdk] WARN: readelf not found on PATH or in ANDROID_NDK_HOME." >&2
    echo "[build-ppocr-sdk]       Skipping 16KB alignment gate — verify manually before shipping." >&2
    echo "[build-ppocr-sdk] Done. AAR at ${AAR_OUT}"
    exit 0
fi

bad=0
while read -r so; do
    # readelf -lW shows program headers in wide format; `awk` extracts
    # the `Align` column from each `LOAD` row. 0x4000 = 16 KB, the
    # targetSdk-35 minimum alignment.
    aligns=$( "$READELF" -lW "$so" 2>/dev/null | awk '$1=="LOAD" {print $NF}' || true)
    for a in $aligns; do
        if [ "$a" != "0x4000" ]; then
            echo "[build-ppocr-sdk] BAD_ALIGN: $so LOAD segment align=$a (expected 0x4000)" >&2
            bad=1
        fi
    done
done < <(find "$AAR_TMP" -name '*.so' 2>/dev/null)

if [ "$bad" -ne 0 ]; then
    echo "" >&2
    echo "[build-ppocr-sdk] ERROR: native libs are not 16 KB-aligned." >&2
    echo "[build-ppocr-sdk]        Devices with 16 KB kernel" >&2
    echo "[build-ppocr-sdk]        (Android 15+ on Pixel 8 / Galaxy S25 / Xiaomi 15)" >&2
    echo "[build-ppocr-sdk]        will fail to System.loadLibrary these .so files." >&2
    echo "[build-ppocr-sdk]        Rebuild with:" >&2
    echo "[build-ppocr-sdk]          -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" >&2
    echo "[build-ppocr-sdk]        (the CMAKE_SHARED_LINKER_FLAGS hook above is best-effort" >&2
    echo "[build-ppocr-sdk]         — patch upstream CMakeLists.txt or its build.gradle if" >&2
    echo "[build-ppocr-sdk]         it uses per-target target_link_options LINK_FLAGS that" >&2
    echo "[build-ppocr-sdk]         override the CMAKE_SHARED_LINKER_FLAGS variable)." >&2
    exit 1
fi

echo "[build-ppocr-sdk] 16KB alignment verified"
echo "[build-ppocr-sdk] Done. AAR at ${AAR_OUT}"