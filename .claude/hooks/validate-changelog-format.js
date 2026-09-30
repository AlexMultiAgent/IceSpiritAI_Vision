// PostToolUse hook for Edit/Write on app/src/main/assets/user-changelog.md.
//
// Fires when an Edit / Write / MultiEdit tool modifies the user changelog.
// After the edit lands, re-reads the file from disk and runs five checks:
//
//   1. 首行必须是 `# 用户更新日志`
//   2. 每个版本顶部必须匹配 `## vX.Y.Z — YYYY-MM-DD`
//   3. 每个版本必须有 **bold 中文摘要。** 行(紧跟标题)
//   4. 每个 ### 子段标题必须在允许列表(新增 / 修复 / 变更 / 文档 / 调整 / 优化 / 测试)
//   5. 每条 - entry 必须以 `- **` 开头(必须加粗前缀)
//
// On violation: exit 2 + stderr Claude Code surfaces to the user.
//
// 主君 2026-09-30 mid-turn 要求:"以后不要再发生"格式漂移。
// 锁定于 commit dfd0dae(本批 + 上批 v0.5.8 changelog entry commit 后)。

const VALID_SECTIONS = ['新增', '修复', '变更', '文档', '调整', '优化', '测试'];
const TOP_HEADER_REGEX = /^## v\d+\.\d+\.\d+ — \d{4}-\d{2}-\d{2}$/;
const SUMMARY_REGEX = /^\*\*[^*]+\*\*\.?/;

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => { input += chunk; });
process.stdin.on('end', () => {
  let parsed = null;
  try {
    parsed = JSON.parse(input);
  } catch (_e) {
    process.exit(0);
  }

  const toolName = parsed && parsed.tool_name;
  if (toolName !== 'Edit' && toolName !== 'Write' && toolName !== 'MultiEdit') {
    process.exit(0);
  }

  const toolInput = (parsed && parsed.tool_input) || {};
  const filePath = typeof toolInput.file_path === 'string' ? toolInput.file_path : '';
  if (!filePath) process.exit(0);

  const normPath = filePath.replace(/\\/g, '/').replace(/^\/+/, '');
  const isUserchangelog = /(?:^|\/)app\/src\/main\/assets\/user-changelog\.md$/i.test(normPath);
  if (!isUserchangelog) process.exit(0);

  const fs = require('fs');
  if (!fs.existsSync(filePath)) process.exit(0);

  let raw;
  try {
    raw = fs.readFileSync(filePath, 'utf8');
  } catch (_e) {
    process.exit(0);
  }

  // Split into lines preserving line breaks; normalize CRLF.
  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  const errors = [];

  // --- Check 1: 首行必须是 # 用户更新日志 ---
  if (!lines[0].startsWith('# 用户更新日志')) {
    errors.push(`第 1 行必须是「# 用户更新日志」(实际:「${lines[0]}」)`);
  }

  // --- 遍历所有行做 2-5 检查 ---
  let inVersion = false;
  let sawSummary = false;
  let inSection = false;

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];

    // 跳过空行(不重置 in_section,因为视觉空行在 ### 子段与 - entry 之间是合法格式)
    if (line.trim() === '') continue;

    if (line.startsWith('## v')) {
      if (!TOP_HEADER_REGEX.test(line)) {
        errors.push(`第 ${i+1} 行版本标题格式错误(实际: ${line});必须是「## vX.Y.Z — YYYY-MM-DD」`);
      }
      inVersion = true;
      sawSummary = false;
      inSection = false;
      continue;
    }

    if (!inVersion) {
      // 文件顶部第一个版本之前的容许内容(比如空行)
      continue;
    }

    if (!sawSummary) {
      if (!SUMMARY_REGEX.test(line.trim())) {
        errors.push(`第 ${i+1} 行:版本摘要行必须是「**bold 摘要。**」格式(实际: ${line.trim()})`);
      }
      sawSummary = true;
      continue;
    }

    if (line.startsWith('### ')) {
      const h = line.slice(4).trim();
      if (!VALID_SECTIONS.includes(h)) {
        errors.push(`第 ${i+1} 行:子段标题「${h}」不在允许列表(${VALID_SECTIONS.join(' / ')})`);
      }
      inSection = true;
      continue;
    }

    if (line.startsWith('- ')) {
      if (!inSection) {
        errors.push(`第 ${i+1} 行:条目「- ...」前必须有 ### 子段标题(实际行: ${line})`);
      }
      if (!line.startsWith('- **')) {
        errors.push(`第 ${i+1} 行:条目必须以「- **加粗开头**:detail」格式(实际: ${line})`);
      }
      continue;
    }

    if (line.startsWith('#')) {
      errors.push(`第 ${i+1} 行:不期望的标题层级(应用 ## 而非 #);实际: ${line}`);
    }
  }

  if (errors.length > 0) {
    process.stderr.write(
      'BLOCKED by IceSpiritAI_Vision validate-changelog-format hook:\n' +
      '  File: ' + filePath + '\n' +
      '  错误 ' + errors.length + ' 项:\n' +
      errors.map(e => '    - ' + e).join('\n') + '\n' +
      '  锁定格式(2026-09-30):\n' +
      '    ## vX.Y.Z — YYYY-MM-DD\n' +
      '    **bold 中文摘要。**\n' +
      '    ### 新增 / 修复 / 变更 / 文档 / 调整 / 优化 / 测试\n' +
      '    - **加粗开头**[(commit xxx)]: detail\n' +
      '  Reference: docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md\n',
    );
    process.exit(2);
  }
  process.exit(0);
});