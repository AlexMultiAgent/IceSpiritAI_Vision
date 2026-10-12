from license_tool import sign_payload, parse_code, issue, parse, CODE_PREFIX
import datetime
import pytest


@pytest.fixture
def keypair(tmp_path):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization
    sk = ec.generate_private_key(ec.SECP256R1())
    pk = sk.public_key()
    pem = sk.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    pem_path = tmp_path / "test.pem"
    pem_path.write_bytes(pem)
    return sk, pk, pem_path


def test_prefix_is_ics1():
    assert CODE_PREFIX == "ICS1"


def test_roundtrip_with_expire_date(keypair, tmp_path):
    sk, pk, _ = keypair
    code = issue(sk, name="acme", expire=20270630, ledger_dir=tmp_path, d="ad")
    parsed = parse(code)
    assert parsed.payload.v == 1
    assert parsed.payload.n == "acme"
    assert parsed.payload.e == 20270630
    assert parsed.payload.d == "ad"


def test_roundtrip_with_permanent(keypair, tmp_path):
    sk, pk, _ = keypair
    code = issue(sk, name="demo", expire=0, ledger_dir=tmp_path, d="ad")
    parsed = parse(code)
    assert parsed.payload.e == 0
    assert parsed.payload.d == "ad"


def test_rejects_mismatched_prefix():
    assert parse("XICS1.aGVsbG8.c2ln") is None


def test_rejects_wrong_segments():
    assert parse("ICS1.aGVsbG8") is None
    assert parse("ICS1.a.b.c") is None


def test_renew_rejects_shorter_expire(keypair, tmp_path):
    from license_tool import issue, renew, Ledger, ParseException
    sk, pk, _ = keypair
    code1 = issue(sk, name="acme", expire=20270630, ledger_dir=tmp_path, d="ad")
    code2 = renew(sk, name="acme", expire=20271231, ledger_dir=tmp_path, d="ad")
    assert code2 != code1
    with pytest.raises(ParseException) as ei:
        renew(sk, name="acme", expire=20270101, ledger_dir=tmp_path, d="ad")
    assert "已续到更晚" in str(ei.value) or "永久" in str(ei.value)


def test_renew_to_permanent(keypair, tmp_path):
    from license_tool import issue, renew
    sk, pk, _ = keypair
    issue(sk, name="acme", expire=20270630, ledger_dir=tmp_path, d="ad")
    code = renew(sk, name="acme", expire=0, ledger_dir=tmp_path, d="ad")
    parsed = parse(code)
    assert parsed.payload.e == 0
    assert parsed.payload.d == "ad"


def test_new_rejects_name_reuse(keypair, tmp_path):
    """V2: name 不再允许 new 两次(必须 renew)。同 V1 的 uid-reuse 约束。"""
    from license_tool import issue, new, ParseException
    sk, pk, _ = keypair
    issue(sk, name="acme", expire=20270630, ledger_dir=tmp_path, d="ad")
    with pytest.raises(ParseException) as ei:
        new(sk, name="acme", expire=20280101, ledger_dir=tmp_path, d="ad")
    assert "name" in str(ei.value) and "已存在" in str(ei.value)


def test_renew_rejects_when_department_differs(keypair, tmp_path):
    """renew d 与台账里 name 的 d 不一致 → 拒绝(跨处室续期被禁止)"""
    from license_tool import issue, renew, ParseException
    sk, pk, _ = keypair
    issue(sk, name="acme", expire=20270630, d="ad", ledger_dir=tmp_path)
    with pytest.raises(ParseException) as ei:
        renew(sk, "acme", expire=20271231, d="food", ledger_dir=tmp_path)
    msg = str(ei.value)
    assert "跨处室" in msg or "不能由" in msg


def test_renew_allows_same_department(keypair, tmp_path):
    """renew d 一致 → 通过"""
    from license_tool import issue, renew
    sk, pk, _ = keypair
    issue(sk, name="acme2", expire=20270630, d="ad", ledger_dir=tmp_path)
    code = renew(sk, "acme2", expire=20271231, d="ad", ledger_dir=tmp_path)
    parsed = parse(code)
    assert parsed.payload.d == "ad"
    assert parsed.payload.n == "acme2"


def test_renew_missing_name_raises(keypair, tmp_path):
    """台账里没有这个 name,renew 应当拒绝"""
    from license_tool import renew, ParseException
    sk, pk, _ = keypair
    with pytest.raises(ParseException) as ei:
        renew(sk, "ghost-user", expire=20271231, d="ad", ledger_dir=tmp_path)
    assert "不存在" in str(ei.value)


def test_ledger_persists_d_column(keypair, tmp_path):
    """台账 CSV 应当写出 d 列,旧文件可兼容读取(无 d → 视为默认)"""
    from license_tool import issue, Ledger
    sk, pk, _ = keypair
    issue(sk, name="acme", expire=20270630, ledger_dir=tmp_path, d="food")
    csv_path = tmp_path / "许可证台账.csv"
    text = csv_path.read_text(encoding="utf-8")
    assert "dept,d" in text.splitlines()[0] or text.splitlines()[0].endswith(",d"), \
        f"CSV header missing d column: {text.splitlines()[0]!r}"
    assert ",food" in text

    rows = Ledger(tmp_path).rows()
    food_rows = [r for r in rows if r.get("name") == "acme"]
    assert food_rows
    assert food_rows[-1]["d"] == "food"


def test_legacy_csv_without_d_column_reads_as_ad(tmp_path):
    """legacy CSV(无 d 列)应当被读出,d 自动 fallback 到默认值"""
    csv_path = tmp_path / "许可证台账.csv"
    csv_path.write_text(
        "ts,op,name,issued_expire,note\n"
        "2026-10-01T10:00:00,new,acme,20270630,\n",
        encoding="utf-8",
    )
    from license_tool import _read_ledger
    rows = _read_ledger(tmp_path)
    assert len(rows) == 1
    assert rows[0]["d"] == "ad"
