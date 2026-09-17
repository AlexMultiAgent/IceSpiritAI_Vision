#!/usr/bin/env python3
"""分辨率 ↔ OCR 耗时 ↔ 识别召回 的扫描（离线，同 Android 模型/推理栈）。

为什么需要它：眼镜按一次拍照到出声只要 ~2.3 s，其中 OCR 只占 ~0.3 s
（"CRC → 语音开始" 那段）。想再压就得动分辨率，而分辨率一降，
小字先糊 —— 所以这里把「耗时」和「认到的字少了多少」一起量出来。

实现上直接复用 `tools/ocr-audit66-fixtures.py`（同一份 det/rec ONNX 模型、
同样的前/后处理），只把 `det_limit_side_len` / `det_limit_type` 扫一遍。

用法：
  python tools/ocr-resolution-sweep.py --images 20
  python tools/ocr-resolution-sweep.py --images 8 --settings 960:max,640:max,480:max

输出：stdout 表格 + build/reports/ocr_resolution_sweep_<时间戳>.md

注意：这里的绝对耗时是**PC CPU** 的，和手机端不通用；但「相对趋势」与
「识别召回」是可迁移的（手机端 OCR 的 0.3 s 也是同一套模型的 CPU 推理）。
"""
from __future__ import annotations

import argparse
import importlib.util
import statistics
import sys
import time
from collections import Counter
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
FIXTURES_TOOL = PROJECT_ROOT / "tools" / "ocr-audit66-fixtures.py"
REPORT_DIR = PROJECT_ROOT / "build" / "reports"

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


def load_fixtures_tool():
    spec = importlib.util.spec_from_file_location("audit66_fixtures", FIXTURES_TOOL)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_settings(raw: str) -> list[tuple[int, str]]:
    out: list[tuple[int, str]] = []
    for item in raw.split(","):
        side, _, limit_type = item.strip().partition(":")
        out.append((int(side), (limit_type or "max").strip()))
    return out


def text_of(lines: list[tuple[str, float, float]]) -> str:
    return "".join(line[0] for line in lines)


def recall_vs_reference(reference: str, candidate: str) -> float:
    """参考文本按字符多重集合被候选覆盖的比例（1.0 = 一个字都没丢）。"""
    if not reference:
        return 1.0
    ref = Counter(reference)
    cand = Counter(candidate)
    covered = sum(min(count, cand[ch]) for ch, count in ref.items())
    return covered / sum(ref.values())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--images", type=int, default=20, help="取前 N 张案例图（按文件名排序）")
    parser.add_argument(
        "--settings",
        type=str,
        default="1280:min,960:max,768:max,640:max,480:max,360:max",
        help="逗号分隔的 <limit_side_len>:<limit_type>",
    )
    parser.add_argument(
        "--reference",
        type=str,
        default="960:max",
        help="作为「识别召回」基准的设置（默认 = Android 当前配置）",
    )
    parser.add_argument(
        "--downscale-width",
        type=int,
        default=0,
        help=(
            "先把每张图缩到这个宽度再跑（模拟眼镜那种小尺寸照片）。"
            "0 = 用原图。用来回答「小图 + 不放大」会不会明显掉识别"
        ),
    )
    args = parser.parse_args()

    fixtures = load_fixtures_tool()
    images = fixtures.collect_image_files()[: args.images]
    if not images:
        print("没有找到案例图（违规案例/*.jpg）", file=sys.stderr)
        return 1

    if args.downscale_width > 0:
        import cv2

        scaled_dir = PROJECT_ROOT / "build" / "tmp" / f"downscaled_{args.downscale_width}"
        scaled_dir.mkdir(parents=True, exist_ok=True)
        scaled: list[Path] = []
        for image in images:
            img = fixtures.load_image_bgr(image)
            if img is None:
                continue
            height, width = img.shape[:2]
            if width > args.downscale_width:
                ratio = args.downscale_width / float(width)
                img = cv2.resize(
                    img,
                    (args.downscale_width, max(1, int(round(height * ratio)))),
                    interpolation=cv2.INTER_AREA,
                )
            out = scaled_dir / image.name
            # cv2.imwrite() silently fails on non-ASCII paths on Windows (the
            # case images all have Chinese names), so encode + write the bytes.
            ok, buffer = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
            if not ok:
                raise RuntimeError(f"JPEG encode failed for {image.name}")
            out.write_bytes(buffer.tobytes())
            scaled.append(out)
        images = scaled
        print(f"已把图片缩到宽 {args.downscale_width}px（JPEG q80）→ {scaled_dir}")

    settings = parse_settings(args.settings)
    ref_side, ref_type = parse_settings(args.reference)[0]
    print(f"图片 {len(images)} 张，设置 {settings}，基准 {ref_side}:{ref_type}")

    ocr_ctx = fixtures.init_onnx_ocr()
    base_rec_cfg = dict(fixtures.REC_CFG)

    results: dict[str, dict] = {}
    texts: dict[str, dict[str, str]] = {}
    for side, limit_type in settings:
        key = f"{side}:{limit_type}"
        det_cfg = dict(fixtures.DET_CFG)
        det_cfg["det_limit_side_len"] = side
        det_cfg["det_limit_type"] = limit_type
        texts[key] = {}
        times: list[float] = []
        lines_found: list[int] = []
        for image in images:
            start = time.perf_counter()
            lines = fixtures.ocr_one(ocr_ctx, image, det_cfg, base_rec_cfg)
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            times.append(elapsed_ms)
            lines_found.append(len(lines))
            texts[key][image.name] = text_of(lines)
        results[key] = {"times": times, "lines": lines_found}
        print(
            f"  {key:>9}  中位 {statistics.median(times):7.1f} ms  "
            f"均值 {statistics.fmean(times):7.1f} ms  行数中位 {statistics.median(lines_found):5.1f}"
        )

    # 召回：以基准设置为 1.0
    reference_key = f"{ref_side}:{ref_type}"
    reference_texts = texts.get(reference_key, {})
    print("\n相对基准的字符召回（1.000 = 与基准认到的字一样多）：")
    recalls: dict[str, float] = {}
    for side, limit_type in settings:
        key = f"{side}:{limit_type}"
        if not reference_texts:
            recalls[key] = float("nan")
            continue
        per_image = [
            recall_vs_reference(reference_texts[name], texts[key].get(name, ""))
            for name in reference_texts
        ]
        recalls[key] = statistics.fmean(per_image)
        print(f"  {key:>9}  召回 {recalls[key]:.3f}")

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    report = REPORT_DIR / f"ocr_resolution_sweep_{stamp}.md"
    with report.open("w", encoding="utf-8") as fh:
        fh.write("# OCR 分辨率扫描（离线，同 Android 模型/栈）\n\n")
        fh.write(f"- 图片：`违规案例/` 前 {len(images)} 张（{images[0].name} … {images[-1].name}）\n")
        fh.write(f"- 基准（召回 1.0）：`{reference_key}`\n")
        fh.write(f"- 生成时间：{datetime.now().isoformat(timespec='seconds')}\n\n")
        fh.write("| 设置 | 耗时中位 | 耗时均值 | 行数中位 | 字符召回(相对基准) |\n|---|---|---|---|---|\n")
        for side, limit_type in settings:
            key = f"{side}:{limit_type}"
            r = results[key]
            fh.write(
                f"| `{key}` | {statistics.median(r['times']):.1f} ms | "
                f"{statistics.fmean(r['times']):.1f} ms | "
                f"{statistics.median(r['lines']):.1f} | {recalls[key]:.3f} |\n"
            )
        fh.write("\n> 绝对耗时是 PC CPU 的；手机端同为 CPU 推理，趋势可迁移。\n")
        fh.write("> 召回=按字符多重集合覆盖基准文本的比例，1.000 表示一个字都没少。\n")
    print(f"\n报告：{report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
