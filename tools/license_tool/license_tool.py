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
from pathlib import Path
from typing import Optional

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

CODE_PREFIX = "ICS1"
PAYLOAD_VERSION = 1


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
    u: str
    n: str
    e: int

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

    def current_expiry_for(self, uid: str) -> Optional[int]:
        return _current_expiry_for(self.ledger_dir, uid)

    def append(self, op: str, uid: str, name: str, expire: int, note: str = "") -> None:
        _append_ledger(self.ledger_dir, op, uid, name, expire, note)


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
        payload = Payload(v=int(d["v"]), u=str(d["u"]), n=str(d["n"]), e=int(d["e"]))
    except Exception:
        return None
    if payload.v != PAYLOAD_VERSION:
        return None
    if len(payload.u) != 6:
        return None
    if not payload.n.strip():
        return None
    if payload.e != 0 and (payload.e < 20250101 or payload.e > 20991231):
        return None
    return ParsedLicense(payload=payload, payload_bytes=payload_bytes, signature=signature)


def parse_code(code: str) -> Optional[ParsedLicense]:
    """parse 的语义清晰别名,供外部脚本与 GUI 共用。"""
    return parse(code)


def _format(payload_bytes: bytes, signature: bytes) -> str:
    return f"{CODE_PREFIX}.{_b64url_encode(payload_bytes)}.{_b64url_encode(signature)}"


def _ledger_path(ledger_dir: Path) -> Path:
    return ledger_dir / "许可证台账.csv"


def _read_ledger(ledger_dir: Path) -> list[dict]:
    p = _ledger_path(ledger_dir)
    if not p.exists():
        return []
    rows = []
    with p.open("r", encoding="utf-8") as f:
        header = f.readline().strip().split(",")
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            fields = line.split(",")
            rows.append(dict(zip(header, fields)))
    return rows


def _append_ledger(ledger_dir: Path, op: str, uid: str, name: str, expire: int, note: str = "") -> None:
    p = _ledger_path(ledger_dir)
    is_new = not p.exists()
    with p.open("a", encoding="utf-8", newline="\n") as f:
        if is_new:
            f.write("ts,op,uid,name,issued_expire,note\n")
        ts = datetime.datetime.now().isoformat(timespec="seconds")
        f.write(f"{ts},{op},{uid},{name},{expire},{note}\n")


def _current_expiry_for(ledger_dir: Path, uid: str) -> Optional[int]:
    rows = _read_ledger(ledger_dir)
    last = None
    for r in rows:
        if r.get("uid") != uid:
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


def new(sk, uid: str, name: str, expire: int, ledger_dir: Path, note: str = "") -> str:
    if len(uid) != 6:
        raise ParseException(f"uid 必须是 6 位,当前 {len(uid)}")
    current = _current_expiry_for(ledger_dir, uid)
    if current is not None:
        raise ParseException(f"uid={uid} 已存在(当前到期={current});请用 renew 而不是 new")
    return _issue(sk, "new", uid, name, expire, ledger_dir, note)


def renew(sk, uid: str, expire: int, ledger_dir: Path, note: str = "") -> str:
    if expire != 0:
        current = _current_expiry_for(ledger_dir, uid)
        if current is None:
            raise ParseException(f"uid={uid} 不存在;请用 new 而不是 renew")
        if current == 0:
            raise ParseException(f"uid={uid} 已是永久,无需续期")
        if expire <= current:
            raise ParseException(
                f"uid={uid} 已续到更晚({current});新到期 {expire} 不晚于当前,拒绝缩短"
            )
    return _issue(sk, "renew", uid, name=None, expire=expire, ledger_dir=ledger_dir, note=note)


def _issue(sk, op: str, uid: str, name: Optional[str], expire: int, ledger_dir: Path, note: str) -> str:
    if name is None:
        rows = _read_ledger(ledger_dir)
        for r in rows:
            if r.get("uid") == uid and r.get("op") in ("new", "renew"):
                name = r.get("name", "")
                break
    if name is None:
        raise ParseException(f"renew 找不到 uid={uid} 的历史 name")
    payload = Payload(v=PAYLOAD_VERSION, u=uid, n=name, e=expire)
    payload_bytes = _serialize_payload(payload)
    signature = _sign(sk, payload_bytes)
    _append_ledger(ledger_dir, op, uid, name, expire, note)
    return _format(payload_bytes, signature)


def issue(sk, uid: str, name: str, expire: int, ledger_dir: Optional[Path] = None, note: str = "") -> str:
    ledger_dir = ledger_dir or Path(os.getcwd())
    return new(sk, uid, name, expire, ledger_dir, note)


def sign_payload(sk, payload: Payload) -> str:
    """直接对一个 Payload 签发 ICS1 字符串(不写台账);GUI 高级模式 / 调试用。"""
    payload_bytes = _serialize_payload(payload)
    signature = _sign(sk, payload_bytes)
    return _format(payload_bytes, signature)


def info(code: str) -> str:
    p = parse(code)
    if p is None:
        raise ParseException("激活码格式错误")
    expire_str = "永久" if p.payload.is_permanent() else str(p.payload.e)
    return (
        f"uid     : {p.payload.u}\n"
        f"username: {p.payload.n}\n"
        f"expire  : {expire_str}\n"
        f"version : {p.payload.v}"
    )


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
    p_new.add_argument("--uid", required=True)
    p_new.add_argument("--name", required=True)
    p_new.add_argument("--expire", required=True, type=int, help="yyyyMMdd,0=永久")
    p_new.add_argument("--pem", required=True)
    p_new.add_argument("--ledger-dir", default=".")

    p_renew = sub.add_parser("renew")
    p_renew.add_argument("--uid", required=True)
    p_renew.add_argument("--expire", required=True, type=int)
    p_renew.add_argument("--pem", required=True)
    p_renew.add_argument("--ledger-dir", default=".")
    p_renew.add_argument("--note", default="")

    p_info = sub.add_parser("info")
    p_info.add_argument("--code", required=True)

    p_verify = sub.add_parser("verify")
    p_verify.add_argument("--code", required=True)
    p_verify.add_argument("--pem", required=True)

    args = parser.parse_args()

    if args.cmd == "new":
        sk = _load_private_key(args.pem)
        code = new(sk, args.uid, args.name, args.expire, Path(args.ledger_dir))
        print(code)
    elif args.cmd == "renew":
        sk = _load_private_key(args.pem)
        code = renew(sk, args.uid, args.expire, Path(args.ledger_dir), note=args.note)
        print(code)
    elif args.cmd == "info":
        print(info(args.code))
    elif args.cmd == "verify":
        sk = _load_private_key(args.pem)
        pk = sk.public_key()
        ok = verify(args.code, pk)
        print("OK" if ok else "FAIL")


if __name__ == "__main__":
    _cli()
