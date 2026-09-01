// Self-test for pre-tool-use.js regex patterns. Run with:
//   node hook-self-test.js
// Pass/fail per case. Goal: confirm v0.1.43 fix catches the bypasses
// while leaving legitimate commands alone.

const rule1 = /(?:^|;|\s|\|\||&&|\|)\s*git(?:\s+\S+)*\s+add\s+(?:-A\b|--all\b|\.(?=[\s\/.;&|*]|$)|\.\.\b|\.git\b|\*(?=[\s;|&]|$))/m;
const aarFile = /app[\\/]+libs[\\/]+(?:\*|[\w\-.]*\.aar)/;
const appLibsDir = /app[\\/]+libs[\\/](?=\s|$|\*|;|&|\|)/;
const destructive = /\b(rm|del|rm\s+-rf|rm\s+-r|rmdir|unlink|mv|git\s+rm|truncate|find\b[^\n]*-delete)\b/;

const cases = [
  // [command, rule1ShouldBlock, rule3ShouldBlock]
  ['git add -A',                       true,  false],
  ['git add --all',                    true,  false],
  ['git add .',                        true,  false],
  ['git add ./',                       true,  false],
  ['git add ..',                       true,  false],
  ['git add .git',                     true,  false],
  ['git add *',                        true,  false],
  ['git -C . add -A',                  true,  false],
  ['git --git-dir=foo add --all',      true,  false],
  ['echo hi; git add -A',              true,  false],
  ['  git   add  -A',                  true,  false],
  ['git add -A; echo done',            true,  false],
  ['git add file1.kt file2.kt',        false, false],
  ['git add -Afoo',                    false, false],
  ['git add .gitignore',               false, false],
  ['git status',                       false, false],
  ['git add src/main/kotlin/Foo.kt',   false, false],
  ['rm app/libs/ppocr-sdk.aar',        false, true],
  ['rm app/libs/*.aar',                false, true],
  ['rm -rf app/libs/',                 false, true],
  ['del app\\libs\\ppocr-sdk.aar',     false, true],
  ['rm app/libs/something.txt',        false, false],
  ['ls app/libs/',                     false, false],
];

let pass = 0, fail = 0;
for (const [cmd, r1, r3] of cases) {
  const r1actual = rule1.test(cmd);
  const r3actual = destructive.test(cmd) && (aarFile.test(cmd) || appLibsDir.test(cmd));
  const r1ok = r1actual === r1;
  const r3ok = r3actual === r3;
  const tag = (r1ok && r3ok) ? '[pass]' : '[FAIL]';
  console.log(tag + '  ' + cmd + '  (r1=' + r1actual + '/' + r1 + ', r3=' + r3actual + '/' + r3 + ')');
  if (r1ok && r3ok) pass++; else fail++;
}
console.log();
console.log('Total: pass=' + pass + ' fail=' + fail);
process.exit(fail === 0 ? 0 : 1);