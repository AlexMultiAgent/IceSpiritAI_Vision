"""
冰灵锐目 激活码签发工具 — GUI 包装

UI 调用 license_tool.py 的纯函数层(in-process, 不走 subprocess — 避免打包问题)。
依赖: PySide6 + cryptography(随工具一起打包)
"""
from __future__ import annotations
import sys
from pathlib import Path

from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QTextEdit, QMessageBox,
    QFileDialog, QGroupBox, QFormLayout, QInputDialog, QComboBox
)


# ----- 导入 license_tool 的核心 -----
def _import_license_tool():
    """导入 license_tool 模块,兼容源码运行 + PyInstaller 冻结两种场景

    PyInstaller 冻结时,gui 与 license_tool.py 被 --add-data 一起打进 .exe 根目录,
    直接 `import license_tool` 即可。源码运行时按文件路径动态加载,不依赖 PYTHONPATH。
    """
    if getattr(sys, 'frozen', False):
        # PyInstaller 冻结 — license_tool.py 与 gui 一起被 bundler 收进来,直接 import
        import license_tool as lt
        return lt
    else:
        # 源码运行 — 显式按路径 import
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "license_tool", str(Path(__file__).parent / "license_tool.py")
        )
        if spec is None or spec.loader is None:
            raise ImportError(f"Cannot find license_tool.py at {Path(__file__).parent}")
        lt = importlib.util.module_from_spec(spec)
        # 必须先注册到 sys.modules,Python 3.12 的 @dataclasses 会查 sys.modules[cls.__module__]
        # 否则 _process_class 在 dataclasses.py 里 ns = sys.modules.get(...) 取 None 直接崩。
        sys.modules["license_tool"] = lt
        spec.loader.exec_module(lt)
        return lt


_lt = _import_license_tool()


# ----- 路径:TOOL_DIR 用于台账(在 .exe 同目录)-----
if getattr(sys, 'frozen', False):
    TOOL_DIR = Path(sys.executable).parent.resolve()
else:
    TOOL_DIR = Path(__file__).parent.resolve()


# 管理处室下拉数据源
DEPARTMENTS: list[tuple[str, str]] = [
    # (label, d-key) — d-key 与 license_tool.Payload.d 字段对齐
    ("广告处", "ad"),
    # 未来: ("食品口", "food"),
]


class LicenseToolGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("冰灵锐目 激活码签发工具")
        self.resize(820, 640)
        self.pem_path: Path | None = None

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)

        # PEM 选择
        pem_row = QHBoxLayout()
        self.pem_label = QLabel("未选择私钥")
        pem_btn = QPushButton("选择私钥 PEM 文件")
        pem_btn.clicked.connect(self.choose_pem)
        pem_row.addWidget(self.pem_label, 1)
        pem_row.addWidget(pem_btn)
        root.addLayout(pem_row)

        # 签发 / 续期 表单
        form = QGroupBox("签发 / 续期")
        form_layout = QFormLayout()
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("区县局用户名(签发给谁)")
        self.dept_combo = QComboBox()
        for label, key in DEPARTMENTS:
            self.dept_combo.addItem(label, key)
        self.expire_combo = QComboBox()
        for label, key in [("1 个月", "1m"), ("3 个月", "3m"), ("半年", "6m"),
                           ("1 年", "1y"), ("2 年", "2y"), ("5 年", "5y"), ("永久", "0")]:
            self.expire_combo.addItem(label, key)
        self.expire_combo.setCurrentIndex(3)  # 默认 1 年
        form_layout.addRow("名称:", self.name_input)
        form_layout.addRow("管理处室:", self.dept_combo)
        form_layout.addRow("到期日:", self.expire_combo)
        form.setLayout(form_layout)
        root.addWidget(form)

        # 操作按钮
        btn_row = QHBoxLayout()
        new_btn = QPushButton("签发新码")
        new_btn.clicked.connect(self.do_new)
        renew_btn = QPushButton("续期")
        renew_btn.clicked.connect(self.do_renew)
        verify_btn = QPushButton("完整验签")
        verify_btn.clicked.connect(self.do_verify)
        for b in [new_btn, renew_btn, verify_btn]:
            btn_row.addWidget(b)
        root.addLayout(btn_row)

        # 结果区
        self.result_area = QTextEdit()
        self.result_area.setReadOnly(True)
        self.result_area.setAcceptRichText(False)  # plain text
        # 不换行 — ICS1 完整可见(横向滚动)
        self.result_area.setLineWrapMode(QTextEdit.NoWrap)
        root.addWidget(self.result_area, 1)

    def choose_pem(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择私钥 PEM", str(TOOL_DIR), "PEM files (*.pem)")
        if path:
            self.pem_path = Path(path)
            self.pem_label.setText(str(self.pem_path))

    def _require_inputs(self, require_name: bool) -> tuple[bool, object | None, str, str, int]:
        """集中校验 PEM + name + 部门 + 到期日;返回 (ok, sk, name, d, expire_int)

        expire_int 已由 preset ("1y" 等) 经 compute_expire_yyyymmdd 转换为 yyyymmdd int。
        """
        if self.pem_path is None:
            QMessageBox.warning(self, "缺少私钥", "请先选择私钥 PEM 文件")
            return False, None, "", "", 0
        if require_name and not self.name_input.text().strip():
            QMessageBox.warning(self, "缺少名称", "请输入区县局用户名")
            return False, None, "", "", 0
        try:
            sk = _lt._load_private_key(str(self.pem_path))
        except Exception as e:
            QMessageBox.critical(self, "私钥加载失败", str(e))
            return False, None, "", "", 0
        name = self.name_input.text().strip()
        d = self.dept_combo.currentData() or "ad"
        preset = self.expire_combo.currentData() or "1y"
        try:
            expire_int = _lt.compute_expire_yyyymmdd(preset)
        except _lt.ParseException as e:
            QMessageBox.critical(self, "到期日无效", str(e))
            return False, None, "", "", 0
        return True, sk, name, d, expire_int

    def do_new(self):
        ok, sk, name, d, expire_int = self._require_inputs(require_name=True)
        if not ok:
            return
        try:
            code = _lt.new(sk, name=name, expire=expire_int, ledger_dir=TOOL_DIR, d=d)
        except _lt.ParseException as e:
            self._show_result(False, str(e))
            return
        self._show_result(True, code)
        self.result_area.append("--- 复制上面的 ICS1 字符串给客户 ---")

    def do_renew(self):
        ok, sk, name, d, expire_int = self._require_inputs(require_name=True)
        if not ok:
            return
        try:
            code = _lt.renew(sk, name=name, expire=expire_int, ledger_dir=TOOL_DIR, d=d)
        except _lt.ParseException as e:
            self._show_result(False, str(e))
            return
        self._show_result(True, code)
        self.result_area.append("--- 续期成功, 复制上面的 ICS1 字符串给客户 ---")

    def do_verify(self):
        if self.pem_path is None:
            QMessageBox.warning(self, "缺少私钥", "请先选择私钥 PEM 文件")
            return
        try:
            sk = _lt._load_private_key(str(self.pem_path))
            pk = sk.public_key()
        except Exception as e:
            QMessageBox.critical(self, "私钥加载失败", str(e))
            return
        code, ok = QInputDialog.getMultiLineText(self, "完整验签", "粘贴激活码:")
        if not ok or not code.strip():
            return
        code = code.strip()
        try:
            ok = _lt.verify(code, pk)
            display = code if len(code) <= 80 else code[:80] + "..."
            self._show_result(ok, display)

        except _lt.ParseException as e:
            self._show_result(False, str(e))

    def _show_result(self, ok: bool, text: str):
        prefix = "✓ OK" if ok else "✗ FAIL"
        # plain text append — 不 wrap(用 setLineWrapMode(QTextEdit.NoWrap) 已设)
        self.result_area.append(f"[{prefix}] {text}")


def main():
    app = QApplication(sys.argv)
    win = LicenseToolGUI()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()