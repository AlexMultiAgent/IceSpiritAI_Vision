// PostToolUse hook for Edit/Write on app/src/main/assets/user-changelog.md.
//
// Fires when an Edit / Write / MultiEdit tool modifies the user changelog.
// After the edit lands, re-reads the file from disk and runs four checks:
//
//   1. 首行必须是 `# 用户更新日志`
//   2. 每个版本顶部必须匹配 `## vX.Y.Z — YYYY-MM-DD`
//   3. 每个版本必须有摘要行(plain text,以 `。` 结尾,无 `**`)
//   4. 每个 ### 子段标题必须在允许列表(新增 / 修复 / 变更 / 文档 / 调整 / 优化 / 测试)
//   5. 每条 - entry 必须以 `- ` 开头(plain text,不允许 `**` bold)
//
// 主君 2026-10-01 mid-turn 要求:整个更新日志不要出现 `**(bold markdown),
// 给用户看要简洁。本 hook 锁定无 bold plain text 风格。
// 锁定于 commit 3631d4b(2026-09-30 changelog 格式二次修复)。

const VALID_SECTIONS = ['新增', '修复', '变更', '文档', '调整', '优化', '测试'];
const TOP_HEADER_REGEX = /^## v\d+\.\d+\.\d+ — \d{4}-\d{2}-\d{2}$/;
const ENTRY_PREFIX = '- ';

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

  // Check 0: the entire file must contain zero `**` (bold markdown is banned
  // user-facing-wise; 主君 2026-10-01 explicit rule).
  if (raw.includes('**')) {
    const lines = raw.split('\n');
    const linesWithBold = [];
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('**')) linesWithBold.push(i + 1);
    }
    process.stderr.write(
      'BLOCKED by IceSpiritAI_Vision validate-changelog-format hook:\n' +
      '  File: ' + filePath + '\n' +
      '  主君 2026-10-01 要求整个更新日志不要出现 `**`(bold markdown 给用户看不简洁)。\n' +
      '  共 ' + linesWithBold.length + ' 行含 `**`,例如:\n' +
      linesWithBold.slice(0, 5).map(i => '    L' + i + ': ' + lines[i-1].slice(0, 80)).join('\n') + '\n' +
      (linesWithBold.length > 5 ? '    ...(后续略)\n' : '') +
      '  移除 `**` 标记后重试。\n',
    );
    process.exit(2);
  }

  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  const errors = [];

  // Check 1: 首行必须是 "# 用户更新日志"
  if (!lines[0].startsWith('# 用户更新日志')) {
    errors.push(`第 1 行必须是「# 用户更新日志」(实际:「${lines[0]}」)`);
  }

  let inVersion = false;
  let sawSummary = false;
  let inSection = false;
  const seenVersions = new Map();
  let lastVersionLine = '';

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];

    if (line.trim() === '') continue;

    if (line.startsWith('## v')) {
      if (!TOP_HEADER_REGEX.test(line)) {
        errors.push(`第 ${i+1} 行版本标题格式错误(实际: ${line});必须是「## vX.Y.Z — YYYY-MM-DD」`);
      }
      // 主君 2026-10-02 反馈:重复的 ## v header 导致 APP 设置页 LazyColumn key
      // 冲突(IllegalStateException: Key 'vX.Y.Z' was already used),整个更新
      // 日志页闪退。Detect duplicate IMMEDIATELY.
      if (line === lastVersionLine) {
        errors.push(`第 ${i+1} 行:版本标题与上一行重复 (${line});会触发 APP 设置页 LazyColumn key 冲突导致闪退。立即 dedupe`);
      }
      const verMatch = line.match(/^## (v\d+\.\d+\.\d+)/);
      if (verMatch) {
        const ver = verMatch[1];
        if (seenVersions.has(ver)) {
          errors.push(`第 ${i+1} 行:版本 ${ver} 在文件中出现多次(已记录的行号 ${seenVersions.get(ver)} + 当前 ${i+1});LazyColumn key 冲突导致闪退`);
        } else {
          seenVersions.set(ver, i+1);
        }
      }
      lastVersionLine = line;
      inVersion = true;
      sawSummary = false;
      inSection = false;
      continue;
    }

    if (!inVersion) continue;

    if (!sawSummary) {
      if (!line.trim().endsWith('。') && !line.trim().endsWith('!') && !line.trim().endsWith('?')) {
        errors.push(`第 ${i+1} 行:版本摘要行必须以「。」(或 !/?)结尾的 plain text 句子(实际: ${line.trim()})`);
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

    if (line.startsWith(ENTRY_PREFIX)) {
      if (!inSection) {
        errors.push(`第 ${i+1} 行:条目「- ...」前必须有 ### 子段标题(实际行: ${line})`);
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
      '  锁定格式(2026-10-01):\n' +
      '    ## vX.Y.Z — YYYY-MM-DD\n' +
      '    plain text 中文摘要以「。」结尾。\n' +
      '    ### 新增 / 修复 / 变更 / 文档 / 调整 / 优化 / 测试\n' +
      '    - plain text(无 ** bold)\n' +
      '  Reference: docs/superpowers/specs/2026-09-30-ad-rule-fp-gates-and-keywords-design.md\n',
    );
    process.exit(2);
  }
  process.exit(0);
});