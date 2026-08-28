#!/usr/bin/env python3
"""一次性录制 66 张违规案例图的 OCR 文本为 fixture。

输出:
  app/src/test/resources/fixtures/audit66_ocr/<NN>_<slug>.txt × 66
  app/src/test/resources/fixtures/audit66_ocr/manifest.json

路径:**onnxruntime + cv2 + numpy**(匹配 Android `ice_ocr_rules` profile 的
ONNX Runtime + PaddleOCR SDK 配置 — 与 Android 端同模型同推理栈,无 runtime
差异)。paddlepaddle 原生推理路径在本机因 PIR/OneDNN bug 崩,这里不依赖
paddleocr / paddlepaddle。

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

import cv2
import numpy as np
import onnxruntime as ort
import yaml

# 强制 UTF-8 stdout(Windows cp936 默认会炸中文)
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CASES_DIR = PROJECT_ROOT / "违规案例"
FIXTURES_DIR = PROJECT_ROOT / "app" / "src" / "test" / "resources" / "fixtures" / "audit66_ocr"
LOG_PATH = PROJECT_ROOT / "build" / "reports" / "audit66_ocr_fixtures.log"

IMAGE_EXTS = (".jpg", ".jpeg", ".png")

# det / rec config(Android `PaddleOcrEngine.kt` v0.1.36 配置 + ONNX 模型)
DET_CFG = {
    "det_limit_side_len": 1280,
    "det_limit_type": "min",       # min(H,W) ≤ 1280 时保持比例 resize
    "det_thresh": 0.3,             # DB 二值化阈值(Android 覆盖 inference.yml 0.2)
    "det_box_thresh": 0.5,         # box 平均分阈值(Android 覆盖 inference.yml 0.45)
    "det_unclip_ratio": 1.6,       # 扩 box 系数(Android 覆盖 inference.yml 1.4)
    "rec_batch_size": 6,           # Android Phase 1 default
    "rec_score_thresh": 0.5,       # rec 置信度门控(过滤噪声)
    "max_candidates": 1000,        # det boxes top-K(Android 无显式,按 inference.yml 3000 收紧)
}
REC_CFG = {
    "rec_image_shape": [3, 48, 320],  # (C, H, W_max) 与 inference.yml RecResizeImg 一致
}

# Normalize(与 `models/det/inference.yml` + `models/rec/inference.yml` 的
# NormalizeImage transform 一致:mean/std ImageNet,scale=1/255,order=hwc)
NORM_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
NORM_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


def build_fixture_filename(stem: str) -> str:
    """原文件 stem(如 '01_碧桂园华美天樾_中国地产三强_绝对化与数据引用')
    → fixture 文件名 '01_碧桂园华美天樾-中国地产三强-绝对化与数据引用.txt'"""
    i = 0
    while i < len(stem) and stem[i].isdigit():
        i += 1
    prefix = stem[:i]
    rest = stem[i:].lstrip("_")
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


def init_onnx_ocr() -> dict:
    """加载 `app/src/main/assets/models/{det,rec}/inference.onnx` 与 rec 的
    character_dict。返回 context dict,后续 ocr_one() 复用 ONNX sessions。"""
    det_path = PROJECT_ROOT / "app/src/main/assets/models/det/inference.onnx"
    rec_path = PROJECT_ROOT / "app/src/main/assets/models/rec/inference.onnx"
    rec_cfg_path = PROJECT_ROOT / "app/src/main/assets/models/rec/inference.yml"

    if not det_path.exists():
        sys.exit(f"ERROR: det ONNX not found: {det_path}")
    if not rec_path.exists():
        sys.exit(f"ERROR: rec ONNX not found: {rec_path}")
    if not rec_cfg_path.exists():
        sys.exit(f"ERROR: rec inference.yml not found: {rec_cfg_path}")

    print("[init] loading ONNX models (CPU) ...", file=sys.stderr)
    t0 = time.time()
    sess_opts = ort.SessionOptions()
    sess_opts.intra_op_num_threads = 4   # 与 Android EngineConfig(numThreads=4) 一致
    sess_opts.inter_op_num_threads = 2
    det_sess = ort.InferenceSession(
        str(det_path), sess_opts, providers=["CPUExecutionProvider"]
    )
    rec_sess = ort.InferenceSession(
        str(rec_path), sess_opts, providers=["CPUExecutionProvider"]
    )

    with open(rec_cfg_path, "r", encoding="utf-8") as f:
        rec_cfg = yaml.safe_load(f)
    char_dict = rec_cfg["PostProcess"]["character_dict"]
    if not isinstance(char_dict, list) or not char_dict:
        sys.exit(f"ERROR: rec inference.yml character_dict 不是非空 list: {type(char_dict)}")

    det_in = det_sess.get_inputs()[0]
    rec_in = rec_sess.get_inputs()[0]
    print(
        f"[init] models loaded in {time.time()-t0:.1f}s "
        f"(det input={det_in.name} {det_in.shape}, "
        f"rec input={rec_in.name} {rec_in.shape}, "
        f"dict_size={len(char_dict)})",
        file=sys.stderr,
    )
    return {"det": det_sess, "rec": rec_sess, "dict": char_dict}


def load_image_bgr(image_path: Path) -> np.ndarray | None:
    """读图为 BGR uint8 ndarray。cv2.imread 在 Windows 上对含中文路径失败,
    这里走 np.frombuffer + cv2.imdecode 兜底 unicode 路径。"""
    try:
        with open(image_path, "rb") as f:
            data = f.read()
    except OSError as e:
        print(f"[load_image] open failed: {image_path}: {e}", file=sys.stderr)
        return None
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return img  # None 表示解码失败


def det_preprocess(img: np.ndarray, det_cfg: dict) -> tuple[np.ndarray, tuple[int, int], tuple[int, int]]:
    """det 预处理:resize(BGR) → NormalizeImage(scale 1/255, mean/std ImageNet, order=hwc) →
    ToCHWImage → pad 到 32 倍数。返回 (CHW float32 (1,3,H',W'), (src_h, src_w), (new_h, new_w))。"""
    src_h, src_w = img.shape[:2]
    det_limit_side_len = det_cfg["det_limit_side_len"]
    det_limit_type = det_cfg["det_limit_type"]
    if det_limit_type == "min":
        ratio = det_limit_side_len / float(min(src_h, src_w))
    else:
        ratio = det_limit_side_len / float(max(src_h, src_w))
    ratio = min(ratio, 1.0)  # 不放大
    new_h = int(round(src_h * ratio / 32) * 32)
    new_w = int(round(src_w * ratio / 32) * 32)
    if new_h <= 0 or new_w <= 0:
        # ratio 太小被 round 到 0 — 至少保留 32 像素
        new_h = max(new_h, 32)
        new_w = max(new_w, 32)

    resized = cv2.resize(img, (new_w, new_h))
    norm = resized.astype(np.float32) / 255.0
    norm = (norm - NORM_MEAN) / NORM_STD
    chw = norm.transpose(2, 0, 1)[None, ...]  # (1, 3, H, W)
    return chw.astype(np.float32), (src_h, src_w), (new_h, new_w)


def db_postprocess(
    pred: np.ndarray,
    resize_shape: tuple[int, int],
    src_shape: tuple[int, int],
    thresh: float,
    box_thresh: float,
    unclip_ratio: float,
    max_candidates: int,
) -> list[list[list[float]]]:
    """DB 后处理(简化版):sigmoid-threshold → findContours → 简单 unclip。

    输入:pred 是 det 模型输出 (1, 1, H', W') 的概率图(去 batch/channel dim)。
    返回:list of 4-pt boxes(in 原图坐标 [[x1,y1], ...])。

    与 PP-OCR 原版 DBPostProcess 的差别:不预测 offset/coefficient 通道、
    不做 adaptive thresholding、用 boundingRect 代替最小外接 quad 做 unclip 扩张。
    对广告招牌大文本足够(main text 提取)。
    """
    rs_h, rs_w = resize_shape
    src_h, src_w = src_shape
    scale_x = src_w / rs_w
    scale_y = src_h / rs_h

    # sigmoid 已经隐含在 det 模型输出里(export 时已融合)。这里用原始概率图。
    pred_bin = (pred > thresh).astype(np.uint8) * 255
    contours, _ = cv2.findContours(pred_bin, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    boxes: list[list[list[float]]] = []
    scores: list[float] = []
    for cnt in contours:
        if cnt.shape[0] < 4:
            continue
        # box 平均 score(用 mask 取 box 内像素的概率均值)
        mask = np.zeros_like(pred_bin)
        cv2.drawContours(mask, [cnt], -1, 255, -1)
        region = pred[mask == 255]
        if region.size == 0:
            continue
        score = float(region.mean())
        if score < box_thresh:
            continue
        # 用 boundingRect 做 unclip 扩张:distance = area * (ratio - 1) / perimeter
        area = cv2.contourArea(cnt)
        peri = cv2.arcLength(cnt, True)
        if peri <= 0:
            continue
        distance = area * (unclip_ratio - 1.0) / peri
        rx, ry, rw, rh = cv2.boundingRect(cnt)
        x = max(0, int(rx - distance))
        y = max(0, int(ry - distance))
        w = int(rw + 2 * distance)
        h = int(rh + 2 * distance)
        box = [
            [x * scale_x,            y * scale_y],
            [(x + w) * scale_x,      y * scale_y],
            [(x + w) * scale_x,      (y + h) * scale_y],
            [x * scale_x,            (y + h) * scale_y],
        ]
        boxes.append(box)
        scores.append(score)

    # 按 score 降序,top max_candidates
    if len(boxes) > max_candidates:
        order = np.argsort(scores)[::-1][:max_candidates]
        boxes = [boxes[i] for i in order]
    return boxes


def crop_and_resize_rec(
    img: np.ndarray, box_pts: list[list[float]], rec_image_shape: list[int]
) -> np.ndarray | None:
    """从原图按 box 4 点的 boundingRect 裁出 → resize 到 (H=48, W<=320) →
    pad 到 320 → NormalizeImage → CHW。返回 float32 ndarray (3, 48, 320)
    或 None 表示空 / 非法。"""
    c, h_target, w_max = rec_image_shape
    pts = np.array(box_pts, dtype=np.float32)
    if pts.ndim != 2 or pts.shape[0] < 3:
        return None
    rx, ry, rw, rh = cv2.boundingRect(pts.astype(np.int32))
    if rw <= 0 or rh <= 0:
        return None
    rx = max(0, rx); ry = max(0, ry)
    crop = img[ry:ry + rh, rx:rx + rw]
    if crop is None or crop.size == 0:
        return None
    # 保持比例 resize:固定 H = h_target = 48,计算 W
    scale = h_target / crop.shape[0]
    new_w = min(int(crop.shape[1] * scale), w_max)
    if new_w <= 0:
        return None
    resized = cv2.resize(crop, (new_w, h_target))
    # pad 到 w_max
    if new_w < w_max:
        pad = np.zeros((h_target, w_max - new_w, 3), dtype=np.uint8)
        resized = np.concatenate([resized, pad], axis=1)
    # normalize
    norm = resized.astype(np.float32) / 255.0
    norm = (norm - NORM_MEAN) / NORM_STD
    chw = norm.transpose(2, 0, 1)  # (3, 48, W)
    return chw.astype(np.float32)


def ctc_decode(probs: np.ndarray, char_dict: list[str]) -> tuple[str, float]:
    """CTC greedy decode。

    输入:probs shape (T, D)。PP-OCRv6_small_rec ONNX 导出**已含 softmax** —
    实测 t=3 logits max=1.0 / min=0.0 / sum=1.0,所以传进来就是概率分布,
    不需要再 softmax 一遍(否则 peak 1.0 会被 1/N 稀释成 ~5e-5)。

    经验观察:
    - blank = index 0
    - char 索引偏移 = +1(model_idx = dict_idx + 1),即 model_idx 1 对应 dict[0]
    (已被 case 49 "购物车" 实测验证:model argmax = 14041/8844/14484,dict 中
    "购/物/车" 分别在 14040/8843/14483)

    返回:(text, mean_confidence)。text 为拼接后的字符串,confidence 为
    各 character 概率的均值(空串时 0.0)。
    """
    if probs.ndim != 2 or probs.shape[0] == 0:
        return "", 0.0
    indices = probs.argmax(axis=-1)

    decoded_chars: list[str] = []
    decoded_probs: list[float] = []
    prev = -1
    for t, idx in enumerate(indices):
        if idx == 0:
            prev = idx
            continue
        if idx == prev:
            prev = idx
            continue
        # idx > 0 已是有效字符
        char_idx = idx - 1
        if 0 <= char_idx < len(char_dict):
            decoded_chars.append(char_dict[char_idx])
            decoded_probs.append(float(probs[t, idx]))
        prev = idx
    text = "".join(decoded_chars)
    score = float(np.mean(decoded_probs)) if decoded_probs else 0.0
    return text, score


def ocr_one(
    ocr_ctx: dict, image_path: Path, det_cfg: dict, rec_cfg: dict
) -> list[tuple[str, float, float]]:
    """对一张图跑 det + rec。返回 [(text, y_top, confidence), ...] 按 y_top 升序。"""
    img = load_image_bgr(image_path)
    if img is None:
        print(f"[ocr_one] decode failed: {image_path}", file=sys.stderr)
        return []

    # det
    chw, src_shape, resize_shape = det_preprocess(img, det_cfg)
    det_in_name = ocr_ctx["det"].get_inputs()[0].name
    det_out = ocr_ctx["det"].run(None, {det_in_name: chw})[0]
    if det_out.ndim != 4 or det_out.shape[1] != 1:
        print(f"[ocr_one] unexpected det_out shape: {det_out.shape}", file=sys.stderr)
        return []
    pred = det_out[0, 0]

    boxes = db_postprocess(
        pred,
        resize_shape=resize_shape,
        src_shape=src_shape,
        thresh=det_cfg["det_thresh"],
        box_thresh=det_cfg["det_box_thresh"],
        unclip_ratio=det_cfg["det_unclip_ratio"],
        max_candidates=det_cfg["max_candidates"],
    )
    if not boxes:
        return []

    # rec 批处理(同 batch 内 pad 到 max W)
    char_dict = ocr_ctx["dict"]
    rec_batch_size = det_cfg["rec_batch_size"]
    rec_score_thresh = det_cfg["rec_score_thresh"]
    rec_image_shape = rec_cfg["rec_image_shape"]
    rec_in_name = ocr_ctx["rec"].get_inputs()[0].name

    results: list[tuple[str, float, float]] = []
    for batch_start in range(0, len(boxes), rec_batch_size):
        batch_boxes = boxes[batch_start:batch_start + rec_batch_size]
        rec_inputs: list[np.ndarray] = []
        rec_meta: list[list[list[float]]] = []
        for box_pts in batch_boxes:
            crop = crop_and_resize_rec(img, box_pts, rec_image_shape)
            if crop is None:
                continue
            rec_inputs.append(crop)
            rec_meta.append(box_pts)
        if not rec_inputs:
            continue
        max_w = max(c.shape[2] for c in rec_inputs)
        padded = np.zeros(
            (len(rec_inputs), 3, rec_image_shape[1], max_w), dtype=np.float32
        )
        for i, c in enumerate(rec_inputs):
            padded[i, :, :, :c.shape[2]] = c
        rec_out = ocr_ctx["rec"].run(None, {rec_in_name: padded})[0]
        # rec_out shape: (B, T, dict_size+2)
        for i, logits in enumerate(rec_out):
            text, score = ctc_decode(logits, char_dict)
            if score < rec_score_thresh or not text.strip():
                continue
            box_pts = rec_meta[i]
            y_top = float(min(p[1] for p in box_pts))
            results.append((text, y_top, score))

    results.sort(key=lambda x: x[1])
    return results


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

    ocr_ctx = init_onnx_ocr()

    manifest = {
        "onnxruntime_version": _safe_version("onnxruntime"),
        "opencv_version": _safe_version("opencv-python-headless")
        or _safe_version("opencv-python")
        or _safe_version("opencv-contrib-python"),
        "model_name": "PP-OCRv6_small_det+PP-OCRv6_small_rec",
        "runtime_note": "ONNX Runtime CPU (matches Android ice_ocr_rules profile)",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "fixture_dir": str(FIXTURES_DIR.relative_to(PROJECT_ROOT)),
        "det_cfg": {k: v for k, v in DET_CFG.items()},
        "rec_cfg": {k: v for k, v in REC_CFG.items()},
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
            lines = ocr_one(ocr_ctx, img, DET_CFG, REC_CFG)
            text = "\n".join(t for t, _, _ in lines)
            fixture_path.write_text(text, encoding="utf-8")
            manifest["files"][img.name] = {
                "fixture": fixture_name,
                "lines": len(lines),
                "chars": len(text),
                "ms": int((time.time() - t0) * 1000),
            }
            successes += 1
            print(
                f"  -> {len(lines)} lines, {len(text)} chars, {int((time.time()-t0)*1000)}ms",
                file=sys.stderr,
            )
        except Exception as e:  # noqa: BLE001
            print(f"  ERROR: {type(e).__name__}: {e}", file=sys.stderr)
            manifest["files"][img.name] = {
                "fixture": fixture_name,
                "error": f"{type(e).__name__}: {e}",
            }
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
        return ""


if __name__ == "__main__":
    main()
