"""
冰灵锐目 激活码签发工具 — GUI 包装

UI 调用 license_tool.py 的纯函数层(in-process, 不走 subprocess — 避免打包问题)。
依赖: PySide6 + cryptography(随工具一起打包)
"""
from __future__ import annotations
import sys
from pathlib import Path

from PySide6.QtCore import QTimer
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
        self.resize(820, 720)
        self.pem_path: Path | None = None
        # 启动时若 .exe 内嵌了 PEM,自动加载 — 用户无需点"选择私钥"按钮。
        # 源码运行模式 sys.frozen=False,无 embedded → 留 None,保持现状。
        default_pem = self._find_default_pem()
        if default_pem is not None:
            self.pem_path = default_pem

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)

        # PEM 选择
        pem_row = QHBoxLayout()
        self.pem_label = QLabel(self._format_pem_label(self.pem_path))
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

        # 复制按钮行 — 复制最近一次签发/续期成功的 ICS1 字符串
        self._last_success_code: str | None = None
        copy_row = QHBoxLayout()
        copy_row.addStretch(1)
        self.copy_last_btn = QPushButton("复制 ICS1 字符串")
        self.copy_last_btn.setEnabled(False)
        self.copy_last_btn.clicked.connect(self.copy_last_success)
        copy_row.addWidget(self.copy_last_btn)
        root.addLayout(copy_row)

        # 安全提示:PEM 内嵌于 .exe 物理文件,任何拿到副本的人都能反编译提取私钥。
        # 显式红字 + 加粗,让分发对象(市局广告处同事)明确知道分发 .exe = 分发私钥能力。
        if getattr(sys, 'frozen', False) and self.pem_path is not None and \
                str(self.pem_path).startswith(str(getattr(sys, '_MEIPASS', ''))):
            warning_label = QLabel(
                "⚠ 安全提示:本工具的 PEM 私钥已内嵌于 .exe 物理文件中。"
                "任何拿到 .exe 副本的人都能反编译提取私钥。"
                "请严格控制 .exe 的分发对象,不要分享给不可信第三方。"
            )
            warning_label.setWordWrap(True)
            warning_label.setStyleSheet("color: #c62828; font-weight: bold;")
            root.addWidget(warning_label)

    def _format_pem_label(self, pem_path: "Path | None") -> str:
        """PEM 路径显示文案:frozen 模式 + MEIPASS 内嵌 → 加 [内嵌] 前缀让用户一眼看清来源。"""
        if pem_path is None:
            return "未选择私钥"
        if getattr(sys, 'frozen', False) and \
                str(pem_path).startswith(str(getattr(sys, '_MEIPASS', ''))):
            return f"[内嵌] {pem_path}"
        return str(pem_path)

    def _find_default_pem(self) -> "Path | None":
        """查找默认 PEM 路径(优先级)。

        1. PyInstaller --onefile 冻结 → sys._MEIPASS/license.pem(PyInstaller 在打包时由
           release.py 的 --add-data 把 PEM 放到 bundle 根)
        2. 否则返回 None(源码运行模式无内嵌,需要手动选 PEM)
        """
        if getattr(sys, 'frozen', False):
            mei = getattr(sys, '_MEIPASS', None)
            if mei:
                candidate = Path(mei) / 'license.pem'
                if candidate.exists():
                    return candidate
        return None

    def choose_pem(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择私钥 PEM", str(TOOL_DIR), "PEM files (*.pem)")
        if path:
            self.pem_path = Path(path)
            self.pem_label.setText(self._format_pem_label(self.pem_path))
        else:
            # 用户按 Cancel — 试 embedded PEM(.exe 内嵌的 license.pem)
            embedded = self._find_default_pem()
            if embedded is not None:
                self.pem_path = embedded
                self.pem_label.setText(self._format_pem_label(self.pem_path))

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
            self._show_result(ok, display, signing=False)

        except _lt.ParseException as e:
            self._show_result(False, str(e))

    def _show_result(self, ok: bool, text: str, *, signing: bool = True):
        """显示一条结果到 result_area;`signing=True` 且 ok=True 表示来自签发/续期(完整 ICS1) — 更新 copy 按钮。
        verify 走 signing=False,不污染最近一次成功签发记录。
        """
        prefix = "✓ OK" if ok else "✗ FAIL"
        # plain text append — 不 wrap(用 setLineWrapMode(QTextEdit.NoWrap) 已设)
        self.result_area.append(f"[{prefix}] {text}")
        # 失败不清空上次成功 — 用户可能要保留
        if signing and ok and text.startswith("ICS1."):
            self._last_success_code = text
            self.copy_last_btn.setEnabled(True)
            self.copy_last_btn.setText("复制 ICS1 字符串")

    def copy_last_success(self):
        if not self._last_success_code:
            return
        QApplication.clipboard().setText(self._last_success_code)
        self.copy_last_btn.setText("✓ 已复制!")
        QTimer.singleShot(1500, lambda: self.copy_last_btn.setText("复制 ICS1 字符串"))


def main():
    app = QApplication(sys.argv)
    win = LicenseToolGUI()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()