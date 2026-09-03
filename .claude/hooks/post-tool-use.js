// PostToolUse Bash hook — enforces IceSpiritAI_Vision commit hygiene.
//
// Receives JSON on stdin:
//   { "tool_name": "Bash", "tool_input": { "command": "..." }, "tool_result": { ... } }
//
// Rule (critical): After `git commit` lands, verify no
//   `Co-Authored-By: ...` trailer. CLAUDE.md hard rule; 2026-08-20 audit
//   found 隐性 trailers (`Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>`)
//   in history. The audit found the implicit form contains an anthropic.com
//   email even though user.name was replaced with AlexMultiAgent.
//
//   Detection regex matches ANY `Co-Authored-By:` line in commit body,
//   regardless of name or email — that's the conservative form, since
//   the project hard rule is "no Co-Authored-By at all".
//
// On critical violation: exit 2 with stderr message Claude Code surfaces
// to the user (the user can amend the commit via the suggested command).
// On allow: exit 0 silently.
//
// Why PostToolUse (not PreToolUse): PreToolUse runs before the command
// executes; we'd have to inspect the commit message text passed to
// `git commit -m '...'` and that text isn't easily extracted from
// heredocs + escapes. PostToolUse reads the actual landed git log.

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => { input += chunk; });
process.stdin.on('end', () => {
  let parsed = null;
  try {
    parsed = JSON.parse(input);
  } catch (_e) {
    // Malformed JSON → fail open (do not block legitimate work).
    process.exit(0);
  }

  const toolName = parsed && parsed.tool_name;
  const toolInput = (parsed && parsed.tool_input) || {};
  const cmd = typeof toolInput.command === 'string' ? toolInput.command : '';

  if (toolName !== 'Bash' || !cmd) process.exit(0);

  // Only check after `git commit` calls. Filter out commands that just
  // mention "git commit" in a comment / echo / grep — use word boundary
  // and require `git commit` followed by space or end-of-line.
  if (!/(?:^|;|\s|\|\||&&|\|)\s*git(?:\s+\S+)*\s+commit\b/.test(cmd)) {
    process.exit(0);
  }

  // git commit was invoked. Inspect the actual landed commit body.
  const { execSync } = require('child_process');
  let logMsg = '';
  try {
    logMsg = execSync('git log -1 --format=%B', { encoding: 'utf8' });
  } catch (_e) {
    // git not available (not in repo / etc.) — fail open.
    process.exit(0);
  }

  // Match ANY `Co-Authored-By:` line in the commit body, including
  // the implicit form `Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>`.
  const trailerRe = /^Co-Authored-By:.*$/im;
  if (trailerRe.test(logMsg)) {
    const trailerLine = logMsg
      .split(/\r?\n/)
      .find((l) => /^Co-Authored-By:/i.test(l));
    console.error(
      'BLOCKED by IceSpiritAI_Vision PostToolUse hook:\n' +
      '  The most recent commit contains a Co-Authored-By trailer.\n' +
      '  CLAUDE.md hard rule: NEVER add Co-Authored-By trailers — including the\n' +
      '  "implicit" form Co-Authored-By: AlexMultiAgent <noreply@anthropic.com>.\n' +
      '  The 2026-08-20 audit found this implicit form hiding an anthropic.com\n' +
      '  email in commits where user.name had been replaced with AlexMultiAgent.\n' +
      '  To remove from the LAST commit (only — older commits are grandfathered):\n' +
      '    git log -1 --format=%B | sed \'/^Co-Authored-By:/d\' | git commit --amend --file=-\n' +
      '  Or, if the commit was already pushed:\n' +
      '    DO NOT force-push main; create a follow-up commit that fixes the issue.\n' +
      '  Trailer found: ' + (trailerLine || '(unknown)'),
    );
    process.exit(2);
  }

  process.exit(0);
});