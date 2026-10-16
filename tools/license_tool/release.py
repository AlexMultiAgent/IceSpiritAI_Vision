r"""
PyInstaller 打包 license_tool_gui.py → 签发工具.exe

产物输出到 /d/icevision-license-tool/,与 release keystore 同盘。
license_tool.py 必须随 gui 一同打入 .exe 根目录(否则 in-process import 失败)。

自 v0.5.18 起(用户显式接受安全 trade-off),PEM 私钥也一并内嵌进 .exe。
任何拿到 .exe 副本的人都能反编译提取私钥 — 仅限市局广告处内部分发使用。

Python 环境注意(2026-10-16):
本机 PATH 上的 `python3` 通常是 Python 3.14(没有 PyInstaller);PyInstaller
只装在 Python 3.12(由 pip 装在 Python312\Lib\site-packages)。直接跑
`python3 release.py build` 会撞 No module named PyInstaller。如果当前
Python import 不上 PyInstaller,build() 直接退出并指引到 Python 3.12 路径。
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

# PyInstaller 3.12-only 路径(已知本机环境):如果当前 Python import 不上
# PyInstaller,build() 入口会 exit 并指引到这里。这是 advisory,不是硬绑:
# 未来给 3.13/3.14 装了 PyInstaller 一样能跑。
_PYINSTALLER_PYTHON_HINT = (
    r"C:\Users\37311\AppData\Local\Programs\Python\Python312\python.exe"
)

HERE = Path(__file__).parent.resolve()
SPEC = HERE / "license_tool_gui.py"
OUTPUT_DIR = Path("D:/icevision-license-tool").resolve()
SOURCE_PEM = Path("D:/keystores/icespiritai-license-private.pem")
# 临时内嵌文件,build 完即删(gitignored by tools/license_tool/.gitignore)
EMBEDDED_PEM = HERE / "license.pem"


def build():
    # PyInstaller 必须是当前 Python 已安装的包,否则 subprocess 调 -m PyInstaller
    # 会 No module named。Python 3.14 没装,Python 3.12 装了 —— 见模块顶部 docstring。
    try:
        import PyInstaller  # noqa: F401
    except ImportError:
        print(
            f"[FATAL] 当前 Python ({sys.executable}) 没装 PyInstaller。\n"
            f"        请用装了 PyInstaller 的 Python 跑,例如:\n"
            f"        {_PYINSTALLER_PYTHON_HINT!r} release.py build",
            file=sys.stderr,
        )
        sys.exit(2)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # 清理上次产物(本地 + 输出目录里的旧 .exe,避免堆积乱码 / 不同哈希的版本)
    dist = HERE / "dist"
    build = HERE / "build"
    if dist.exists():
        shutil.rmtree(dist)
    if build.exists():
        shutil.rmtree(build)
    # 输出目录里的 .exe 都清掉,只留 README/INSTALL/PEM 模板
    for old_exe in OUTPUT_DIR.glob("*.exe"):
        try:
            old_exe.unlink()
        except OSError:
            pass

    # 拷贝 PEM 到临时内嵌位置(只在 build 期间存在,build 完立即 unlink)
    if not SOURCE_PEM.exists():
        print(f"[FATAL] 源 PEM 不存在: {SOURCE_PEM}", file=sys.stderr)
        sys.exit(2)
    shutil.copy(str(SOURCE_PEM), str(EMBEDDED_PEM))
    print(f"[INFO] 内嵌 PEM: {EMBEDDED_PEM} ({EMBEDDED_PEM.stat().st_size} bytes)")

    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--windowed",
        "--name", "签发工具",
        "--icon", str(HERE / "SignTool.ico"),
        "--distpath", str(OUTPUT_DIR),
        "--workpath", str(HERE / "build"),
        "--specpath", str(HERE),
        # 把 license_tool.py 打进 .exe 根目录(否则 `import license_tool` 在 frozen 模式找不到模块)。
        # os.pathsep 在 Windows 是 ';' 在 Linux 是 ':',跨平台保险起见用变量。
        "--add-data", f"{HERE / 'license_tool.py'}{os.pathsep}.",
        # 把 PEM 私钥打进 .exe 根目录(运行时由 license_tool_gui._find_default_pem() 读取)。
        # 安全 trade-off:分发 .exe = 分发私钥能力,见 README 顶部红字警示。
        "--add-data", f"{EMBEDDED_PEM}{os.pathsep}.",
        str(SPEC),
    ]
    env = {**__import__("os").environ, "PYTHONIOENCODING": "utf-8", "PYTHONUTF8": "1"}
    try:
        r = subprocess.run(cmd, cwd=str(HERE), env=env, encoding="utf-8")
        if r.returncode != 0:
            sys.exit(r.returncode)
    finally:
        # 即便 PyInstaller 中途崩,也要 unlink 临时 PEM,绝不让私钥长期留在工作树
        try:
            EMBEDDED_PEM.unlink(missing_ok=True)
        except OSError:
            pass

    # 复制 README.txt 为完整安装使用说明(给市局广告处,无需打开 docs/)
    # 在头部插入"PEM 已内嵌"声明段,让用户/分发对象一眼看清安全 trade-off
    embedded_notice = (
        "冰灵锐目 激活码签发工具\n========================\n\n"
        "[!] PEM 已内嵌:本 .exe 内置 ECDSA P-256 私钥,反编译可提取。\n"
        "    适合场景:内部员工使用 / 受控分发。\n"
        "    不适合:公开发布 / 不可信分享。\n\n"
        "    任何拿到 .exe 副本的人都能用同一私钥签发 / 续期任意码。\n"
        "    请严格控制 .exe 的分发对象(只发市局广告处内部员工)。\n"
        "    私钥丢失(失 .exe 控制权)=所有已激活客户需重发版。\n\n"
    )
    readme_source = HERE / "INSTALL.md"
    if readme_source.exists():
        readme_content = readme_source.read_text(encoding="utf-8")
        # 跳过 INSTALL.md 顶部 H1 行(交付说明头部 embedded_notice 已经写了标题,
        # 再保留 H1 会让 shipped README.txt 第 1 行之后多一行重复标题)。
        # 顺带跳过 H1 之前的 blockquote / 空行(transform 前的 trim)。
        lines = readme_content.splitlines()
        while lines and (not lines[0].strip() or lines[0].lstrip().startswith(">")):
            lines.pop(0)
        if lines and lines[0].startswith("# "):
            lines.pop(0)
        while lines and not lines[0].strip():
            lines.pop(0)
        readme_content = "\n".join(lines)
        # 转纯文本:从最长的前缀开始替换,避免 # 残留
        plain = readme_content
        plain = plain.replace("### ", "    ")
        plain = plain.replace("## ", "==== ")
        plain = plain.replace("# ", "")
        plain = plain.replace("**", "")
        plain = plain.replace("`", "")
        # 在原标题前插入内嵌声明段
        plain = embedded_notice + plain
        (OUTPUT_DIR / "README.txt").write_text(plain, encoding="utf-8")
    else:
        (OUTPUT_DIR / "README.txt").write_text(embedded_notice +
            "请参考 INSTALL.md\n", encoding="utf-8")
    print(f"\n[OK] Done. Output: {OUTPUT_DIR}")


if __name__ == "__main__":
    build()