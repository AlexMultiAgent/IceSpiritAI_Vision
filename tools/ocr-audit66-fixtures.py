#!/usr/bin/env python3
"""一次性录制 66 张违规案例图的 OCR 文本为 fixture。

输出:
  app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt × 66
  app/src/test/resources/fixtures/audit66_ocr/manifest.json

用法:
  python tools/ocr-audit66-fixtures.py            # 跑全部
  python tools/ocr-audit66-fixtures.py --only 49  # 只跑 49 号
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

# 强制 UTF-8 stdout(Windows cp936 默认会炸中文)
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CASES_DIR = PROJECT_ROOT / "违规案例"
FIXTURES_DIR = PROJECT_ROOT / "app" / "src" / "test" / "resources" / "fixtures" / "audit66_ocr"
LOG_PATH = PROJECT_ROOT / "build" / "reports" / "audit66_ocr_fixtures.log"

IMAGE_EXTS = (".jpg", ".jpeg", ".png")


def build_fixture_filename(stem: str) -> str:
    """原文件 stem(如 '01_碧桂园华美天樾_中国地产三强_绝对化与数据引用')
    → fixture 文件名 '01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt'"""
    # 取数字前缀
    i = 0
    while i < len(stem) and stem[i].isdigit():
        i += 1
    prefix = stem[:i]
    rest = stem[i:].lstrip("_")  # 去前缀后的下划线
    slug = rest.replace("_", "-")
    return f"{prefix}_{slug}.txt" if prefix else f"{slug}.txt"


def collect_image_files() -> list[Path]:
    files: list[Path] = []
    for p in sorted(CASES_DIR.iterdir()):
        if not p.is_file():
            continue
        if not p.name.lower().endswith(IMAGE_EXTS):
            continue
        if not p.name[0].isdigit():
            continue
        files.append(p)
    return files


def init_paddleocr():
    """paddleocr 3.7.0 API,失败时给清晰报错。"""
    try:
        from paddleocr import PaddleOCR  # type: ignore
    except ImportError:
        print("ERROR: paddleocr not installed. Run: pip install paddleocr==3.7.0", file=sys.stderr)
        sys.exit(2)
    print("[init] loading PP-OCRv6_small model (first run downloads to ~/.paddleocr/)...", file=sys.stderr)
    t0 = time.time()
    # 3.7.0 API: ocr_version 字段选 v6_small;若版本不支持则 fallback 到默认
    try:
        ocr = PaddleOCR(use_angle_cls=True, lang="ch", ocr_version="PP-OCRv6_small", show_log=False)
    except TypeError:
        # 老版本 paddleocr 不支持 ocr_version 字段,降级
        print("[init] WARNING: ocr_version param not supported, falling back to default model", file=sys.stderr)
        ocr = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    print(f"[init] model loaded in {time.time()-t0:.1f}s", file=sys.stderr)
    return ocr


def ocr_one(ocr, image_path: Path) -> list[tuple[str, float]]:
    """paddleocr 返回 [(box, (text, conf)), ...];统一为 [(text, y_top), ...] 按 y 排序"""
    raw = ocr.ocr(str(image_path), cls=True)
    out: list[tuple[str, float]] = []
    if not raw:
        return out
    # 3.7.0 嵌套结构 raw = [page];page = [(box, (text, conf)), ...]
    for page in raw:
        for item in page or []:
            if not item or len(item) < 2:
                continue
            box, payload = item[0], item[1]
            if not box or not payload:
                continue
            # payload 兼容 (text, conf) 或直接 text
            if isinstance(payload, (list, tuple)) and len(payload) >= 1:
                text = str(payload[0])
            else:
                text = str(payload)
            # box = [[x1,y1],[x2,y2],[x3,y3],[x4,y4]],y_top = box[0][1]
            try:
                y_top = float(box[0][1])
            except (TypeError, ValueError, IndexError):
                y_top = 0.0
            out.append((text.strip(), y_top))
    out.sort(key=lambda x: x[1])
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=str, default=None,
                        help="只跑指定编号(如 49),调试用")
    args = parser.parse_args()

    if not CASES_DIR.exists():
        print(f"ERROR: {CASES_DIR} 不存在", file=sys.stderr)
        sys.exit(2)

    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOG_PATH.write_text("", encoding="utf-8")

    images = collect_image_files()
    if args.only:
        images = [p for p in images if p.name.startswith(f"{args.only}_")]
    print(f"[scan] found {len(images)} image(s) in {CASES_DIR}", file=sys.stderr)

    if not images:
        print("ERROR: no images matched", file=sys.stderr)
        sys.exit(2)

    ocr = init_paddleocr()

    manifest = {
        "paddleocr_version": _safe_version("paddleocr"),
        "model_name": "PP-OCRv6_small",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "fixture_dir": str(FIXTURES_DIR.relative_to(PROJECT_ROOT)),
        "files": {},
    }

    successes = 0
    failures = 0
    for img in images:
        stem = img.stem
        fixture_name = build_fixture_filename(stem)
        fixture_path = FIXTURES_DIR / fixture_name
        print(f"[ocr] {img.name} -> {fixture_name}", file=sys.stderr)
        try:
            t0 = time.time()
            lines = ocr_one(ocr, img)
            text = "\n".join(t for t, _ in lines)
            fixture_path.write_text(text, encoding="utf-8")
            manifest["files"][img.name] = {
                "fixture": fixture_name,
                "lines": len(lines),
                "chars": len(text),
                "ms": int((time.time() - t0) * 1000),
            }
            successes += 1
            print(f"  -> {len(lines)} lines, {len(text)} chars, {int((time.time()-t0)*1000)}ms", file=sys.stderr)
        except Exception as e:  # noqa: BLE001
            print(f"  ERROR: {type(e).__name__}: {e}", file=sys.stderr)
            manifest["files"][img.name] = {"fixture": fixture_name, "error": f"{type(e).__name__}: {e}"}
            failures += 1

    (FIXTURES_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\n[done] {successes} success, {failures} fail", file=sys.stderr)
    print(f"[done] fixtures: {FIXTURES_DIR}", file=sys.stderr)
    print(f"[done] manifest: {FIXTURES_DIR / 'manifest.json'}", file=sys.stderr)
    if failures > 0:
        sys.exit(1)


def _safe_version(pkg: str) -> str:
    try:
        from importlib.metadata import version
        return version(pkg)
    except Exception:
        return "unknown"


if __name__ == "__main__":
    main()
