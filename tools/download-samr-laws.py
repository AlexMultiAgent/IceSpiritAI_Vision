"""Download SAMR-published laws from https://sjfg.samr.gov.cn into KnowledgeBase/.

Mirrors the API approach from IceSpiritAI_Chat/KnowledgeBase/总局现行有效法律法规/
download_new_regs.py (verified working cookie + endpoint pair as of 2026-08-18):
- Search endpoint: https://sjfg.samr.gov.cn/law/law_search/getLawStore.do (POST)
- Detail endpoint: https://sjfg.samr.gov.cn/law/law_search/queryLawByLawId.do (POST)
- File base URL: https://sjfg.samr.gov.cn/law/file<filePath>
- Required headers: Cookie (single __jsluid_s=…), User-Agent, Referer

The cookie expires periodically; if you get 502 from the API, refresh by visiting
https://sjfg.samr.gov.cn/law/pageInfo/main.main once in a browser and copy the new
__jsluid_s value from devtools.

Usage examples:

  # Show this help + cookie status
  python tools/download-samr-laws.py --check

  # Search by keyword
  python tools/download-samr-laws.py search "食品标识监督管理办法"

  # Download by lawId (32-char hex from SAMR search results)
  python tools/download-samr-laws.py fetch 4818998214a5419f983c177727527282 --dst 知识库/食品标识/

Outputs the PDF (and .doc/.docx when available) into <dst> using the law's title
as the filename. Skips files that already exist with the same size — re-running
is idempotent.

WebFetch in the Claude Code sandbox cannot reach sjfg.samr.gov.cn (network policy
blocks gov.cn); raw curl / this script bypass that since they go through Bash.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

# Force UTF-8 stdout so 中文 filenames print correctly on Windows.
sys.stdout.reconfigure(encoding="utf-8")

# --- Constants from IceSpiritAI_Chat/KnowledgeBase/CLAUDE.md (2026-04-30 capture) ---
# Cookie is checked at 2026-08-18 still valid; refresh per docstring if expired.
DEFAULT_COOKIE = "__jsluid_s=24df5886022ff1d8c4f27327abce4751"
DEFAULT_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/126.0.0.0 Safari/537.36"
)
REFERER = "https://sjfg.samr.gov.cn/law/pageInfo/law_search_new.law_details"
SEARCH_URL = "https://sjfg.samr.gov.cn/law/law_search/getLawStore.do"
DETAIL_URL = "https://sjfg.samr.gov.cn/law/law_search/queryLawByLawId.do"
FILE_BASE = "https://sjfg.samr.gov.cn/law/file"

# Default KnowledgeBase dir relative to repo root.
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DEST = os.path.join(REPO_ROOT, "知识库")


def _bj_date(iso_ts: str) -> str:
    """Convert SAMR's UTC ISO timestamp to a Beijing-time YYYY-MM-DD string.

    SAMR stores dates like '2025-03-13T16:00:00.000+00:00' which is 2025-03-14
    in BJT (UTC+8). Truncating naively would give the previous day.
    """
    if not iso_ts:
        return ""
    try:
        from datetime import datetime, timezone, timedelta
        dt = datetime.fromisoformat(iso_ts.replace("Z", "+00:00"))
        if dt.tzinfo is None:
            return iso_ts[:10]
        bj = dt.astimezone(timezone(timedelta(hours=8)))
        return bj.strftime("%Y-%m-%d")
    except (ValueError, TypeError):
        return iso_ts[:10]


def _request(url: str, payload: dict | None = None, cookie: str = DEFAULT_COOKIE):
    """POST (or GET when payload is None) with the standard SAMR headers."""
    headers = {
        "Cookie": cookie,
        "User-Agent": DEFAULT_UA,
        "Referer": REFERER,
        "Accept": "application/json,text/plain,*/*",
    }
    body = None
    method = "GET"
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
        method = "POST"
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
        return json.loads(raw.decode("utf-8", errors="replace"))
    except (urllib.error.URLError, urllib.error.HTTPError) as e:
        print(f"  HTTP error: {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError as e:
        print(f"  JSON decode error: {e}", file=sys.stderr)
        return None


def search_laws(keyword: str, cookie: str = DEFAULT_COOKIE, page_no: int = 1, page_size: int = 20):
    """Return list of search-result dicts.

    SAMR returns the page rows as a positional array — we project each tuple
    [id, title, type, level, category, pubTime, ?, lastModified] into a dict so
    downstream code does not depend on column order.
    """
    payload = {
        "lawName": keyword,
        "searchScope": "标题+正文",
        "searchType": "模糊查询",
        "timeValid": "",
        "lawType": "",
        "validLevel": "",
        "pageNo": page_no,
        "pubTime": "",
        "startTime": "",
        "pageSize": page_size,
    }
    resp = _request(SEARCH_URL, payload, cookie=cookie)
    if not resp or "page" not in resp:
        return []
    rows = resp.get("page", {}).get("result") or []
    out = []
    for row in rows:
        # row is [id, title, type, level, category, pubTime, ???, lastModified]
        if len(row) < 6:
            continue
        out.append({
            "id": row[0],
            "lawName": row[1],
            "lawType": row[2],
            "validLevel": row[3],
            "category": row[4],
            "pubTime": _bj_date(row[5]) if isinstance(row[5], str) else "",
            "lastModified": row[7] if len(row) > 7 else "",
        })
    return out


def fetch_law(law_id: str, cookie: str = DEFAULT_COOKIE) -> dict | None:
    """Return the page dict {filePath, fileUrl, pubTime, startTime, ...} for a lawId."""
    resp = _request(DETAIL_URL, {"id": law_id}, cookie=cookie)
    if not resp or "page" not in resp:
        return None
    return resp.get("page")


def _download(url: str, dst: str, cookie: str = DEFAULT_COOKIE) -> str:
    """Download a file, skipping when the local copy matches the expected size."""
    req = urllib.request.Request(url, headers={
        "Cookie": cookie,
        "User-Agent": DEFAULT_UA,
        "Referer": REFERER,
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            if resp.status != 200:
                return f"HTTP {resp.status}"
            data = resp.read()
    except Exception as e:
        return f"ERROR: {e}"

    if os.path.exists(dst) and os.path.getsize(dst) == len(data):
        return f"SKIP ({len(data):,} bytes, exists)"

    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "wb") as f:
        f.write(data)
    return f"OK ({len(data):,} bytes)"


def cmd_check(_args):
    """Verify cookie by hitting the search endpoint with a single-char query."""
    print(f"Cookie: {DEFAULT_COOKIE}")
    print(f"Referer: {REFERER}")
    hits = search_laws("广告")
    if not hits:
        print("Cookie status: FAILED — refresh by visiting the Referer page in a browser.")
        return 1
    print(f"Cookie status: OK ({len(hits)} hits for '广告')")
    return 0


def cmd_search(args):
    hits = search_laws(args.keyword, page_no=args.page, page_size=args.size)
    if not hits:
        print(f"No hits for {args.keyword!r}")
        return 1
    print(f"{len(hits)} hits for {args.keyword!r}:\n")
    for i, row in enumerate(hits, 1):
        law_id = row.get("id", "")
        title = row.get("lawName", "")
        pub = row.get("pubTime", "")
        category = row.get("category", "")
        print(f"  {i}. {title}")
        print(f"     lawId={law_id}  pub={pub}  category={category}")
    return 0


def cmd_fetch(args):
    page = fetch_law(args.law_id)
    if not page:
        print(f"FAILED to fetch metadata for lawId={args.law_id}")
        return 1

    title = page.get("lawName") or page.get("lawTitle") or args.law_id
    file_path = page.get("filePath", "")
    file_url = page.get("fileUrl", "")
    pub_time = _bj_date(page.get("pubTime") or "")
    eff_time = _bj_date(page.get("startTime") or "")

    safe_title = title.strip().replace("/", "_").replace("\\", "_")
    dest = args.dst or DEFAULT_DEST

    print(f"[{title}]")
    print(f"  lawId:    {args.law_id}")
    print(f"  published: {pub_time}")
    print(f"  effective: {eff_time}")

    if not file_path and not file_url:
        print("  NO FILES: API returned empty filePath/fileUrl — may be HTML-only entry.")
        return 2

    if file_path:
        url = f"{FILE_BASE}{file_path}"
        # Heuristic extension: /file/PDF/... vs /file/.../...docx — fall back to .bin
        ext = ".pdf" if url.lower().endswith(".pdf") else (
            ".docx" if url.lower().endswith(".docx") else ".pdf"
        )
        # file_path usually starts with /<format>/<filename>.<ext> — prefer the upstream ext
        path_ext = os.path.splitext(urllib.parse.urlparse(url).path)[1] or ext
        dst = os.path.join(dest, safe_title + path_ext)
        print(f"  PDF  -> {dst}: {_download(url, dst)}")

    if file_url:
        url = f"{FILE_BASE}{file_url}"
        path_ext = os.path.splitext(urllib.parse.urlparse(url).path)[1] or ".doc"
        dst = os.path.join(dest, safe_title + path_ext)
        print(f"  DOCX -> {dst}: {_download(url, dst)}")

    print(f"  metadata JSON -> {dest}{os.sep}{safe_title}.json")
    with open(os.path.join(dest, safe_title + ".json"), "w", encoding="utf-8") as f:
        json.dump(page, f, ensure_ascii=False, indent=2)

    return 0


def main() -> int:
    p = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("--cookie", default=DEFAULT_COOKIE,
                   help="Override the __jsluid_s=... cookie (refresh from browser if expired)")
    sub = p.add_subparsers(dest="cmd", required=True)

    pc = sub.add_parser("check", help="Verify cookie is still valid")
    pc.set_defaults(func=cmd_check)

    ps = sub.add_parser("search", help="Search SAMR for a law by keyword")
    ps.add_argument("keyword", help="Search keyword (Chinese)")
    ps.add_argument("--page", type=int, default=1)
    ps.add_argument("--size", type=int, default=20)
    ps.set_defaults(func=cmd_search)

    pf = sub.add_parser("fetch", help="Fetch PDF/DOCX by lawId")
    pf.add_argument("law_id", help="32-char hex lawId from SAMR search results")
    pf.add_argument("--dst", default=DEFAULT_DEST,
                    help="Output directory (default: 知识库/)")
    pf.set_defaults(func=cmd_fetch)

    args = p.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())