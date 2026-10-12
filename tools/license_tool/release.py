"""
PyInstaller 打包 license_tool_gui.py → 签发工具.exe

产物输出到 /d/icevision-license-tool/,与 release keystore 同盘。
"""
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent.resolve()
SPEC = HERE / "license_tool_gui.py"
OUTPUT_DIR = Path("D:/icevision-license-tool").resolve()


def build():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # 清理上次产物
    dist = HERE / "dist"
    build = HERE / "build"
    if dist.exists():
        shutil.rmtree(dist)
    if build.exists():
        shutil.rmtree(build)

    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--windowed",
        "--name", "SignTool",
        "--distpath", str(OUTPUT_DIR),
        "--workpath", str(HERE / "build"),
        "--specpath", str(HERE),
        str(SPEC),
    ]
    env = {**__import__("os").environ, "PYTHONIOENCODING": "utf-8", "PYTHONUTF8": "1"}
    r = subprocess.run(cmd, cwd=str(HERE), env=env, encoding="utf-8")
    if r.returncode != 0:
        sys.exit(r.returncode)

    # 复制 README.txt 为完整安装使用说明(给市局广告处,无需打开 docs/)
    readme_source = HERE / "INSTALL.md"
    if readme_source.exists():
        readme_content = readme_source.read_text(encoding="utf-8")
        # 转纯文本:从最长的前缀开始替换,避免 # 残留
        plain = readme_content
        plain = plain.replace("### ", "    ")
        plain = plain.replace("## ", "==== ")
        plain = plain.replace("# ", "")
        plain = plain.replace("**", "")
        plain = plain.replace("`", "")
        (OUTPUT_DIR / "README.txt").write_text(plain, encoding="utf-8")
    else:
        (OUTPUT_DIR / "README.txt").write_text(
            "冰灵锐目 激活码签发工具\n========================\n\n"
            "请参考 INSTALL.md\n",
            encoding="utf-8",
        )
    print(f"\n[OK] Done. Output: {OUTPUT_DIR}")


if __name__ == "__main__":
    build()