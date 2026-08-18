// PreToolUse Bash hook — enforces IceSpiritAI_Vision commit hygiene.
//
// Receives JSON on stdin:
//   { "tool_name": "Bash", "tool_input": { "command": "..." } }
//
// Blocks:
//   1. `git add -A` / `git add .` — CLAUDE.md requires explicit paths only.
//   2. Any git command that references a sensitive file:
//        - gradle.token.properties (Gitea PAT)
//        - ~/.gradle/gradle.properties (release signing creds)
//        - local.properties (SDK path)
//
// On block: exit 2 with a stderr message Claude Code surfaces to the user.
// On allow: exit 0 with no output.

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => { input += chunk; });
process.stdin.on('end', () => {
  let cmd = '';
  try {
    const data = JSON.parse(input);
    cmd = (data.tool_input && typeof data.tool_input.command === 'string')
      ? data.tool_input.command
      : '';
  } catch (_e) {
    // Malformed JSON → fail open (do not block legitimate work).
    process.exit(0);
  }

  if (!cmd) process.exit(0);

  // Rule 1: `git add -A` / `git add .` (with optional trailing whitespace)
  if (/git\s+add\s+(-A|\.)\s*(\/\/.*)?$/im.test(cmd)) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision PreToolUse hook:\n' +
      '  CLAUDE.md requires explicit file paths in `git add`, not `git add -A` or `git add .`.\n' +
      '  List each file path explicitly: `git add path/to/file1 path/to/file2 ...`\n' +
      '  Original command: ' + cmd.split('\n')[0],
    );
    process.exit(2);
  }

  // Rule 2: git operations referencing sensitive files
  const sensitive = /(gradle\.token\.properties|~?\/?\.gradle\/gradle\.properties|local\.properties)/;
  const gitOp = /\b(add|commit|stash|restore|checkout|reset|rm|apply)\b/;
  if (sensitive.test(cmd) && gitOp.test(cmd)) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision PreToolUse hook:\n' +
      '  The command references a sensitive file (Gitea PAT / release signing creds / SDK path).\n' +
      '  These files are gitignored per .gitignore and must NEVER be staged or committed.\n' +
      '  If you need to rotate the Gitea PAT or release signing creds, edit ~/.gradle/gradle.properties (gitignored) directly.\n' +
      '  Original command: ' + cmd.split('\n')[0],
    );
    process.exit(2);
  }

  process.exit(0);
});