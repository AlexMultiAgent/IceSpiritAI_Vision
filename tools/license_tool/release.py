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
OUTPUT_DIR = Path("/d/icevision-license-tool")


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
        "--name", "签发工具",
        "--distpath", str(OUTPUT_DIR),
        "--workpath", str(HERE / "build"),
        "--specpath", str(HERE),
        str(SPEC),
    ]
    r = subprocess.run(cmd, cwd=str(HERE))
    if r.returncode != 0:
        sys.exit(r.returncode)

    # 复制 README + 占位 PEM(留空 PEM,提示填入真实私钥)
    (OUTPUT_DIR / "README.txt").write_text(
        "冰灵锐目 激活码签发工具\n"
        "========================\n\n"
        "1. 双击「签发工具.exe」\n"
        "2. 点击「选择私钥 PEM 文件」,选你的 icespiritai-license-private.pem\n"
        "3. 填 UID / 名称 / 到期日 → 签发或续期\n"
        "4. 签出的码发给客户(微信 / 邮件)\n\n"
        "台账文件:许可证台账.csv(同 .exe 所在目录)\n"
        "私钥备份:请妥善保管,丢失 = 所有已激活客户需重发版\n",
        encoding="utf-8",
    )
    print(f"\n✓ 打包完成:{OUTPUT_DIR}")


if __name__ == "__main__":
    build()