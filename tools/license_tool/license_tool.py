"""
冰灵锐目 激活码签发工具 — 纯函数层
CLI 子命令由 license_tool_gui.py 包装;UI 调 subprocess 即可。
"""
from __future__ import annotations
import base64
import dataclasses
import datetime
import json
import os
import sys
from pathlib import Path
from typing import Optional

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

CODE_PREFIX = "ICS1"
PAYLOAD_VERSION = 1
DEFAULT_DEPARTMENT = "ad"


class LicenseError(Exception):
    pass


class ParseException(LicenseError):
    pass


@dataclasses.dataclass
class ParsedLicense:
    payload: "Payload"
    payload_bytes: bytes
    signature: bytes


@dataclasses.dataclass
class Payload:
    v: int
    n: str
    e: int
    d: str = DEFAULT_DEPARTMENT

    def is_permanent(self) -> bool:
        return self.e == 0

    def is_expired(self, today: int) -> bool:
        if self.is_permanent():
            return False
        return today > self.e


class Ledger:
    """记录新发 / 续期的台账;目前 _read_ledger / _append_ledger 提供底层读写,
    本类只暴露路径供 GUI 显示,不强制调用方走它(纯函数 new/renew 也接受 ledger_dir)。"""

    def __init__(self, ledger_dir: Path):
        self.ledger_dir = Path(ledger_dir)
        self.path = self.ledger_dir / "许可证台账.csv"

    def rows(self) -> list[dict]:
        return _read_ledger(self.ledger_dir)

    def current_expiry_for(self, name: str) -> Optional[int]:
        return _current_expiry_for(self.ledger_dir, name)

    def append(self, op: str, name: str, expire: int, note: str = "", d: str = DEFAULT_DEPARTMENT) -> None:
        _append_ledger(self.ledger_dir, op, name, expire, note, d)


def _b64url_encode(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def _b64url_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def _serialize_payload(p: Payload) -> bytes:
    return json.dumps(dataclasses.asdict(p), separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def _load_private_key(pem_path: str):
    with open(pem_path, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def _sign(sk, data: bytes) -> bytes:
    return sk.sign(data, ec.ECDSA(hashes.SHA256()))


def parse(code: str) -> Optional[ParsedLicense]:
    parts = code.split(".")
    if len(parts) != 3 or parts[0] != CODE_PREFIX:
        return None
    try:
        payload_bytes = _b64url_decode(parts[1])
        signature = _b64url_decode(parts[2])
    except Exception:
        return None
    try:
        d = json.loads(payload_bytes.decode("utf-8"))
        payload = Payload(
            v=int(d["v"]),
            n=str(d["n"]),
            e=int(d["e"]),
            d=str(d.get("d", DEFAULT_DEPARTMENT)),
        )
    except Exception:
        return None
    if payload.v != PAYLOAD_VERSION:
        return None
    if not payload.n.strip():
        return None
    if payload.e != 0 and (payload.e < 20250101 or payload.e > 20991231):
        return None
    if len(payload.d) > 8:
        return None
    return ParsedLicense(payload=payload, payload_bytes=payload_bytes, signature=signature)


def parse_code(code: str) -> Optional[ParsedLicense]:
    """parse 的语义清晰别名,供外部脚本与 GUI 共用。"""
    return parse(code)


def _format(payload_bytes: bytes, signature: bytes) -> str:
    return f"{CODE_PREFIX}.{_b64url_encode(payload_bytes)}.{_b64url_encode(signature)}"


def _ledger_path(ledger_dir: Path) -> Path:
    return ledger_dir / "许可证台账.csv"


# CSV 表头:中文,管理员一眼能看懂。Excel / 记事本开 BOM 头 UTF-8 后正常显示。
# V2 移除 uid 列(UID == name,签发器只按 name 追踪)。旧 V1 台账(7 列含 uid)不向后兼容 —
# 本地 test_state,无生产用户,直接重写。
_LEDGER_HEADER = "签发时间,操作,名称,到期日,备注,签发处室"


def _read_ledger(ledger_dir: Path) -> list[dict]:
    """读台账 csv(自动剥 BOM;legacy 文件缺 d 列视为默认)。"""
    p = _ledger_path(ledger_dir)
    if not p.exists():
        return []
    import csv as _csv
    with p.open("r", encoding="utf-8-sig", newline="") as f:  # utf-8-sig 自动剥 BOM
        reader = _csv.DictReader(f)
        rows = []
        for row in reader:
            # 字段名映射:CSV header 是中文("签发时间,操作,名称,到期日,备注,签发处室"),
            # 下游代码用英文 key。legacy 兼容(旧 V1 文件有 uid 列,或缺 d 列 → 视为默认)。
            mapped = {}
            for k, v in row.items():
                if k == "签发时间": mapped["ts"] = v
                elif k == "操作": mapped["op"] = v
                elif k == "名称": mapped["name"] = v
                elif k == "到期日": mapped["issued_expire"] = v
                elif k == "备注": mapped["note"] = v
                elif k == "签发处室": mapped["d"] = v
                elif k is None or k == "": pass  # DictReader 给无 header 列填 None,跳过
                else: mapped[k] = v  # legacy uid 等保留
            mapped.setdefault("d", DEFAULT_DEPARTMENT)
            rows.append(mapped)
    return rows


def _append_ledger(ledger_dir: Path, op: str, name: str, expire: int, note: str = "", d: str = DEFAULT_DEPARTMENT) -> None:
    p = _ledger_path(ledger_dir)
    is_new = not p.exists()
    with p.open("a", encoding="utf-8", newline="\n") as f:
        if is_new:
            # UTF-8 BOM:Windows 记事本/Excel 默认按 GBK 读中文会乱码;加 BOM 让它们识别 UTF-8。
            # (header 含中文「签发时间」;旧 legacy 文件无 BOM 已存在,读取兼容。)
            f.write("﻿" + _LEDGER_HEADER + "\n")
        ts = datetime.datetime.now().isoformat(timespec="seconds")
        f.write(f"{ts},{op},{name},{expire},{note},{d}\n")


def _current_expiry_for(ledger_dir: Path, name: str) -> Optional[int]:
    """按 name 查台账中最近一次(new / renew)签发 / 续期的 expire。"""
    rows = _read_ledger(ledger_dir)
    last = None
    for r in rows:
        if r.get("name") != name:
            continue
        op = r.get("op")
        if op not in ("new", "renew"):
            continue
        try:
            expire = int(r.get("issued_expire", "0"))
        except ValueError:
            continue
        if last is None:
            last = expire
        else:
            if expire > last:
                last = expire
    return last


def compute_expire_yyyymmdd(preset: str, today=None) -> int:
    """preset 字符串 → yyyymmdd int(0 = 永久)

    Accepted preset values:
    - "0" / "permanent" / "永久" → 0
    - "1m" / "1月" → 30 天后
    - "3m" / "3月" → 90 天后
    - "6m" / "半年" → 180 天后
    - "1y" / "1年" → 365 天后
    - "2y" / "2年" → 730 天后
    - "5y" / "5年" → 1825 天后
    - "<8 digits>" → int (raw yyyyMMdd passthrough for backward compat)
    """
    from datetime import date, timedelta
    if today is None:
        today = date.today()

    s = preset.strip()
    if s in ("0", "permanent", "永久"):
        return 0
    if s in ("1m", "1月"):
        return int((today + timedelta(days=30)).strftime("%Y%m%d"))
    if s in ("3m", "3月"):
        return int((today + timedelta(days=90)).strftime("%Y%m%d"))
    if s in ("6m", "半年"):
        return int((today + timedelta(days=180)).strftime("%Y%m%d"))
    if s in ("1y", "1年"):
        return int((today + timedelta(days=365)).strftime("%Y%m%d"))
    if s in ("2y", "2年"):
        return int((today + timedelta(days=730)).strftime("%Y%m%d"))
    if s in ("5y", "5年"):
        return int((today + timedelta(days=1825)).strftime("%Y%m%d"))
    # Raw 8-digit yyyyMMdd (backward compat for CLI scripts / 老用法)
    if len(s) == 8 and s.isdigit():
        return int(s)
    raise ParseException(
        f"无效到期日 preset={preset!r};允许: 0/1月/3月/半年/1年/2年/5年/永久 或 yyyyMMdd"
    )


def _current_d_for(ledger_dir: Path, name: str) -> Optional[str]:
    """返回台账中 name 最近一次(new / renew)签发所用的 d(部门 key)。
    用于 renew 跨处室拒签。"""
    rows = _read_ledger(ledger_dir)
    last: Optional[str] = None
    last_ts: Optional[str] = None
    for r in rows:
        if r.get("name") != name:
            continue
        op = r.get("op")
        if op not in ("new", "renew"):
            continue
        ts = r.get("ts") or ""
        if last_ts is None or ts >= last_ts:
            last_ts = ts
            last = r.get("d") or DEFAULT_DEPARTMENT
    return last


def new(sk, name: str, expire: int, ledger_dir: Path, d: str = DEFAULT_DEPARTMENT, note: str = "") -> str:
    """签发新码。若 name 已存在(已签过),抛 ParseException,要求用 renew。"""
    current = _current_expiry_for(ledger_dir, name)
    if current is not None:
        raise ParseException(f"name={name} 已存在(当前到期={current});请用 renew 而不是 new")
    return _issue(sk, "new", name, expire, ledger_dir, note, d)


def renew(sk, name: str, expire: int, ledger_dir: Path, d: str = DEFAULT_DEPARTMENT, note: str = "") -> str:
    """续期按 name 查 d。若 d 与台账里同 name 的最近签发记录不一致,拒绝(跨处室续期被禁止)。"""
    if _current_expiry_for(ledger_dir, name) is None:
        raise ParseException(f"name={name} 不存在;请用 new 而不是 renew")

    existing_d = _current_d_for(ledger_dir, name)
    if existing_d is not None and existing_d != d:
        raise ParseException(
            f"name={name} 由 {existing_d} 签发,不能由 {d} 续期(跨处室续期被禁止)"
        )

    if expire != 0:
        current = _current_expiry_for(ledger_dir, name)
        if current is None:
            raise ParseException(f"name={name} 不存在;请用 new 而不是 renew")
        if current == 0:
            raise ParseException(f"name={name} 已是永久,无需续期")
        if expire <= current:
            raise ParseException(
                f"name={name} 已续到更晚({current});新到期 {expire} 不晚于当前,拒绝缩短"
            )
    return _issue(sk, "renew", name, expire, ledger_dir, note, d)


def _issue(sk, op: str, name: str, expire: int, ledger_dir: Path, note: str, d: str) -> str:
    payload = Payload(v=PAYLOAD_VERSION, n=name, e=expire, d=d)
    payload_bytes = _serialize_payload(payload)
    signature = _sign(sk, payload_bytes)
    _append_ledger(ledger_dir, op, name, expire, note, d)
    return _format(payload_bytes, signature)


def issue(sk, name: str, expire: int, ledger_dir: Optional[Path] = None, d: str = DEFAULT_DEPARTMENT, note: str = "") -> str:
    ledger_dir = ledger_dir or Path(os.getcwd())
    return new(sk, name, expire, ledger_dir, d=d, note=note)


def sign_payload(sk, payload: Payload) -> str:
    """直接对一个 Payload 签发 ICS1 字符串(不写台账);GUI 高级模式 / 调试用。"""
    payload_bytes = _serialize_payload(payload)
    signature = _sign(sk, payload_bytes)
    return _format(payload_bytes, signature)


def verify(code: str, public_key) -> bool:
    p = parse(code)
    if p is None:
        raise ParseException("激活码格式错误")
    try:
        public_key.verify(p.signature, p.payload_bytes, ec.ECDSA(hashes.SHA256()))
        return True
    except Exception:
        return False


def _cli():
    import argparse
    parser = argparse.ArgumentParser(description="冰灵锐目 激活码签发工具")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_new = sub.add_parser("new")
    p_new.add_argument("--name", required=True)
    p_new.add_argument("--d", default=DEFAULT_DEPARTMENT, help=f"管理处室 key(默认 {DEFAULT_DEPARTMENT})")
    p_new.add_argument("--expire", required=True, help="preset (1月/3月/半年/1年/2年/5年/永久/0) or yyyyMMdd")
    p_new.add_argument("--pem", required=True)
    p_new.add_argument("--ledger-dir", default=".")
    p_new.add_argument("--note", default="")

    p_renew = sub.add_parser("renew")
    p_renew.add_argument("--name", required=True, help="按用户名续期(跨处室续期被禁止)")
    p_renew.add_argument("--d", default=DEFAULT_DEPARTMENT, help=f"管理处室 key(默认 {DEFAULT_DEPARTMENT})")
    p_renew.add_argument("--expire", required=True, help="preset (1月/3月/半年/1年/2年/5年/永久/0) or yyyyMMdd")
    p_renew.add_argument("--pem", required=True)
    p_renew.add_argument("--ledger-dir", default=".")
    p_renew.add_argument("--note", default="")

    p_verify = sub.add_parser("verify")
    p_verify.add_argument("--code", required=True)
    p_verify.add_argument("--pem", required=True)

    args = parser.parse_args()

    # Convert expire preset (string) to yyyymmdd int (used by new + renew)
    expire_int = None
    if args.cmd in ("new", "renew"):
        try:
            expire_int = compute_expire_yyyymmdd(args.expire)
        except ParseException as e:
            print(str(e))
            sys.exit(1)

    if args.cmd == "new":
        sk = _load_private_key(args.pem)
        code = new(sk, args.name, expire_int, Path(args.ledger_dir), d=args.d, note=args.note)
        print(code)
    elif args.cmd == "renew":
        sk = _load_private_key(args.pem)
        code = renew(sk, args.name, expire_int, Path(args.ledger_dir), d=args.d, note=args.note)
        print(code)
    elif args.cmd == "verify":
        sk = _load_private_key(args.pem)
        pk = sk.public_key()
        ok = verify(args.code, pk)
        print("OK" if ok else "FAIL")


if __name__ == "__main__":
    _cli()
