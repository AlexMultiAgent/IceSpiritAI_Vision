# icevision-license-tool — 开发者文档

> 给开发者 / 维护者看的工程说明。**操作员使用说明请看 [INSTALL.md](./INSTALL.md)** —— 那份是经 `release.py` 拼装后交付给市局广告处的 `readme.txt` 源文件。

## 文件清单

| 文件 | 职责 |
|---|---|
| `license_tool.py` | 纯函数库:签发 / 续期 / 验签 / payload 编解码,无 UI 依赖,pytest 单测覆盖 |
| `license_tool_gui.py` | PySide6 GUI 包装,UI 调用 `license_tool.py` 的纯函数层(in-process import) |
| `release.py` | PyInstaller 打包脚本 → `D:/icevision-license-tool/SignTool.exe` + 拼装 `OUTPUT_DIR/README.txt` |
| `INSTALL.md` | 操作员文档源,经 `release.py` 头部 `embedded_notice` + markdown→plain transform → `OUTPUT_DIR/README.txt` |
| `tests/` | pytest 单测,覆盖 `license_tool.py` 纯函数 |

## PEM 私钥分发模型(v0.5.18 起)

`release.py` 通过 `PyInstaller --add-data license.pem` 把私钥打进 `SignTool.exe` bundle 根,运行时由 `license_tool_gui._find_default_pem()` 自动从 `sys._MEIPASS/license.pem` 读取。

- **任何拿到 .exe 副本的人都能反编译提取私钥、用同一私钥签发 / 续期任意码**
- 仅限市局广告处内部分发(主君 2026-10-11 显式接受的 trade-off,见 `docs/superpowers/specs/2026-10-11-license-gate-design.md`)
- 失 .exe 控制权 = 失私钥 → 必须联系开发者重新生成 ECDSA P-256 密钥对 + 重打 .exe + App 端换 `BuildConfig.LICENSE_PUBLIC_KEY` + 发新版本,旧码全部失效
- bureau-side 操作员文档不教 `openssl ecparam` 自救 —— App 公钥不在 bureau 手里,自救无效

## 开发与发布

- **本地改完代码**:跑 `python release.py build` 重新打包 .exe + 拼装 `OUTPUT_DIR/README.txt`(仅 Python,不需要 JDK)
- **运营改文档**:只改 `INSTALL.md`,然后 `python release.py build` 重跑 transform 输出到 `OUTPUT_DIR/README.txt`
- **加新规则 / 类别 / 字段**:`license_tool.py` 纯函数层加单测 → `release.py` 改 → 重新 build

## .gitignore

`tools/license_tool/.gitignore` 排除:

- `build/`(PyInstaller 中间产物)
- `dist/`(PyInstaller 输出)
- `__pycache__/`
- `*.spec.bak`
- `license.pem`(`release.py` build 期间从 `D:/keystores/icespiritai-license-private.pem` 临时复制,build 完即 unlink)