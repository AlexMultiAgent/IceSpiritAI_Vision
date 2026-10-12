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


def test_roundtrip_with_expire_date(keypair):
    sk, pk, _ = keypair
    code = issue(sk, uid="a1b2c3", name="acme", expire=20270630)
    parsed = parse(code)
    assert parsed.payload.v == 1
    assert parsed.payload.u == "a1b2c3"
    assert parsed.payload.n == "acme"
    assert parsed.payload.e == 20270630


def test_roundtrip_with_permanent(keypair):
    sk, pk, _ = keypair
    code = issue(sk, uid="x9y8z7", name="demo", expire=0)
    parsed = parse(code)
    assert parsed.payload.e == 0


def test_rejects_mismatched_prefix():
    assert parse("XICS1.aGVsbG8.c2ln") is None


def test_rejects_wrong_segments():
    assert parse("ICS1.aGVsbG8") is None
    assert parse("ICS1.a.b.c") is None


def test_renew_rejects_shorter_expire(keypair, tmp_path):
    from license_tool import issue, renew, Ledger, ParseException
    sk, pk, _ = keypair
    code1 = issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    code2 = renew(sk, uid="a1b2c3", expire=20271231, ledger_dir=tmp_path)
    assert code2 != code1
    with pytest.raises(ParseException) as ei:
        renew(sk, uid="a1b2c3", expire=20270101, ledger_dir=tmp_path)
    assert "已续到更晚" in str(ei.value) or "永久" in str(ei.value)


def test_renew_to_permanent(keypair, tmp_path):
    from license_tool import issue, renew
    sk, pk, _ = keypair
    issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    code = renew(sk, uid="a1b2c3", expire=0, ledger_dir=tmp_path)
    parsed = parse(code)
    assert parsed.payload.e == 0


def test_new_rejects_uid_reuse(keypair, tmp_path):
    from license_tool import issue, new, ParseException
    sk, pk, _ = keypair
    issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    with pytest.raises(ParseException) as ei:
        new(sk, uid="a1b2c3", name="other", expire=20280101, ledger_dir=tmp_path)
    assert "uid" in str(ei.value) and "已存在" in str(ei.value)


def test_info_does_not_require_private_key(keypair, tmp_path):
    from license_tool import issue, info
    sk, pk, _ = keypair
    code = issue(sk, uid="a1b2c3", name="acme", expire=20270630, ledger_dir=tmp_path)
    info_text = info(code)
    assert "a1b2c3" in info_text
    assert "acme" in info_text
    assert "20270630" in info_text
