// UserPromptSubmit hook — injects commit-hygiene reminders when the user's
// prompt suggests a commit / push / release / publish action.
//
// Receives JSON on stdin:
//   { "user_prompt": "...", "session_id": "...", ... }
//
// Match triggers (case-insensitive):
//   - commit
//   - 提交
//   - push
//   - 发布
//   - release
//
// On match: prints a reminder on stdout (Claude Code surfaces this to Claude
// as additional context). Exit 0.
//
// On no match: exit 0 with no output.

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => { input += chunk; });
process.stdin.on('end', () => {
  let text = '';
  try {
    const data = JSON.parse(input);
    text = (data.user_prompt && typeof data.user_prompt === 'string')
      ? data.user_prompt
      : '';
  } catch (_e) {
    process.exit(0);
  }

  if (!text) process.exit(0);

  const triggers = /(commit|提交|push|发布|release)/i;
  if (!triggers.test(text)) process.exit(0);

  console.log(
    '\n--- IceSpiritAI_Vision commit hygiene reminder (CLAUDE.md) ---\n' +
    'Before running any commit / push / release command, verify:\n' +
    '  1. commit author MUST be AlexMultiAgent (git config user.name is locked by repo).\n' +
    '     Run `git config user.name` first; abort if it does not print `AlexMultiAgent`.\n' +
    '  2. NEVER append `Co-Authored-By: Claude ...` trailer.\n' +
    '  3. Pre-flight: `git status --porcelain` MUST NOT include:\n' +
    '       - gradle.token.properties  (Gitea PAT — gitignored)\n' +
    '       - local.properties         (SDK path — gitignored)\n' +
    '       - ~/.gradle/gradle.properties (release signing creds — gitignored)\n' +
    '  4. NEVER use `git add -A` or `git add .`. Use explicit paths:\n' +
    '       `git add <path1> <path2> ...`\n' +
    '  5. If changes touch .kt / .gradle.kts / assets/rules/*.json / assets/*.md,\n' +
    '     verify with `./gradlew.bat testDebugUnitTest -PmodelProfile=shell` (BUILD SUCCESSFUL).\n' +
    '  6. Run `git log -1 --format="%h %an"` after commit to verify AlexMultiAgent authorship.\n' +
    '--- end reminder ---\n',
  );

  process.exit(0);
});