"""
冰灵锐目 激活码签发工具 — GUI 包装

UI 通过 subprocess 调用 license_tool.py 的纯函数层,核心契约在 commit 6e01395。
依赖:PySide6 + cryptography(随工具一起打包)
"""
from __future__ import annotations
import subprocess
import sys
from pathlib import Path

from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QTextEdit, QMessageBox,
    QFileDialog, QGroupBox, QFormLayout, QInputDialog, QComboBox
)

# PyInstaller 冻结后,__file__ 指向 %TEMP%/_MEIxxxxxx/ 解压目录(每次启动换)。
# 必须用 sys.executable 拿真正的 .exe 路径,这样台账 / README 都落在 .exe 同目录。
if getattr(sys, "frozen", False):
    # 运行在 PyInstaller 冻结的 .exe 里
    TOOL_DIR = Path(sys.executable).parent.resolve()
    LICENSE_TOOL = TOOL_DIR / "license_tool.py"  # GUI 仅用其文件名做 subprocess 入口,文件未必真的存在于 TOOL_DIR(只在源码运行时),无需担心
else:
    # 源码运行(Python 直接调 license_tool_gui.py)
    TOOL_DIR = Path(__file__).parent.resolve()
    LICENSE_TOOL = TOOL_DIR / "license_tool.py"


# 管理处室 → 部门 key 映射。
# 跟 license_tool.py 的 DEFAULT_DEPARTMENT / Payload.d 字段对齐。
# 新增部门需开发者改代码 + 发新 .exe;不在此处的下拉值不会被签发器写出。
DEPARTMENTS: list[tuple[str, str]] = [
    ("广告处", "ad"),
    # 未来扩展:
    # ("食品口", "food"),
]


class _InputCancelled(Exception):
    """当用户输入被合法取消(到期日格式错)时抛出,避免 ValueError 触发 SystemExit(0)。"""
    pass


class LicenseToolGUI(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("冰灵锐目 激活码签发工具")
        self.resize(820, 640)
        self.pem_path: Path | None = None
        self.ledger_dir: Path = TOOL_DIR

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
        self.name_input.setPlaceholderText("用户名 / 机构代号")
        self.expire_input = QLineEdit()
        self.expire_input.setPlaceholderText("yyyyMMdd,留空或 0 表示永久")
        self.dept_combo = QComboBox()
        for label, key in DEPARTMENTS:
            self.dept_combo.addItem(label, key)
        form_layout.addRow("名称:", self.name_input)
        form_layout.addRow("管理处室:", self.dept_combo)
        form_layout.addRow("到期日:", self.expire_input)
        form.setLayout(form_layout)
        root.addWidget(form)

        # 操作按钮
        btn_row = QHBoxLayout()
        new_btn = QPushButton("签发新码")
        new_btn.clicked.connect(self.do_new)
        renew_btn = QPushButton("续期")
        renew_btn.clicked.connect(self.do_renew)
        info_btn = QPushButton("查询码信息(仅解析,不验签)")
        info_btn.clicked.connect(self.do_info)
        verify_btn = QPushButton("完整验签")
        verify_btn.clicked.connect(self.do_verify)
        for b in [new_btn, renew_btn, info_btn, verify_btn]:
            btn_row.addWidget(b)
        root.addLayout(btn_row)

        # 结果区
        self.result_area = QTextEdit()
        self.result_area.setReadOnly(True)
        root.addWidget(self.result_area, 1)

    def choose_pem(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择私钥 PEM", str(TOOL_DIR), "PEM files (*.pem)")
        if path:
            self.pem_path = Path(path)
            self.pem_label.setText(str(self.pem_path))

    def _run_tool(self, args: list[str]) -> tuple[int, str]:
        if self.pem_path is None:
            return 1, "请先选择私钥 PEM 文件"
        cmd = [sys.executable, str(LICENSE_TOOL), *args,
               "--pem", str(self.pem_path),
               "--ledger-dir", str(self.ledger_dir)]
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            return r.returncode, r.stdout + r.stderr
        except subprocess.TimeoutExpired:
            return 1, "工具调用超时(10s)"
        except Exception as e:
            return 1, str(e)

    def _current_dept_key(self) -> str:
        """从下拉框读当前选中的部门 key;若下拉为空(理论上不会),回退到广告处。"""
        data = self.dept_combo.currentData()
        return data or "ad"

    def do_new(self):
        if not self._check_inputs(require_name=True, require_dept=False):
            return
        try:
            expire = self._parse_expire()
        except _InputCancelled:
            return
        d = self._current_dept_key()
        rc, out = self._run_tool([
            "new",
            "--name", self.name_input.text().strip(),
            "--d", d,
            "--expire", expire
        ])
        self._show_result(rc, out)

    def do_renew(self):
        if not self._check_inputs(require_name=True, require_dept=False):
            return
        try:
            expire = self._parse_expire()
        except _InputCancelled:
            return
        d = self._current_dept_key()
        rc, out = self._run_tool([
            "renew",
            "--name", self.name_input.text().strip(),
            "--d", d,
            "--expire", expire
        ])
        self._show_result(rc, out)

    def do_info(self):
        code, ok = QInputDialog.getMultiLineText(self, "查询码信息", "粘贴激活码:")
        if not ok or not code.strip():
            return
        rc, out = self._run_tool(["info", "--code", code.strip()])
        self._show_result(rc, out)

    def do_verify(self):
        code, ok = QInputDialog.getMultiLineText(self, "完整验签", "粘贴激活码:")
        if not ok or not code.strip():
            return
        rc, out = self._run_tool(["verify", "--code", code.strip()])
        self._show_result(rc, out)

    def _check_inputs(self, require_name: bool, require_dept: bool = True) -> bool:
        if self.pem_path is None:
            QMessageBox.warning(self, "缺少私钥", "请先选择私钥 PEM 文件")
            return False
        # V2: 签发 / 续期都按 name 走,不再有 uid 输入项。续期按 name 查台账。
        if require_name:
            if not self.name_input.text().strip():
                QMessageBox.warning(self, "缺少名称", "请输入用户名 / 机构代号")
                return False
            if not self.dept_combo.currentData() and require_dept:
                QMessageBox.warning(self, "缺少管理处室", "请选择管理处室")
                return False
        return True

    def _parse_expire(self) -> str:
        raw = self.expire_input.text().strip()
        if raw in ("", "0", "永久", "permanent"):
            return "0"
        if len(raw) == 8 and raw.isdigit():
            return raw
        QMessageBox.warning(self, "到期日错", "格式必须是 yyyyMMdd,或留空 / 0 表示永久")
        raise _InputCancelled()

    def _show_result(self, rc: int, out: str):
        prefix = "✓ OK" if rc == 0 else "✗ FAIL"
        self.result_area.append(f"[{prefix}]\n{out.rstrip()}\n{'-' * 60}")


def main():
    app = QApplication(sys.argv)
    win = LicenseToolGUI()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()