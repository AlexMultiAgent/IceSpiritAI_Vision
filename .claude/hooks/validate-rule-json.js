// PostToolUse hook for Edit/Write on rule JSON files.
//
// Fires when an Edit or Write tool modifies a file matching
//   app/src/main/assets/rules/<domain>_rules.json
// After the edit lands, re-reads the file from disk and runs four checks:
//
//   1. JSON syntax (parseable)
//   2. Top-level `version` field present (integer)
//   3. `rules` array present (non-empty)
//   4. `id` uniqueness across the rules array (no duplicates)
//
// On violation: exit 2 with stderr Claude Code surfaces to the user, listing
// the failing rule. Catches errors earlier than `testDebugUnitTest` (which
// would surface them ~30 s later via `AssetRuleLoaderTest`).
//
// Why PostToolUse: the rule JSON is on disk after Edit/Write completes; reading
// from disk is simpler than parsing the escaped `tool_input.new_string` (Edit)
// or `tool_input.content` (Write), both of which can contain heredoc /
// multi-line content that the regex would have to tolerate.
//
// What this hook does NOT do:
//   - validate keywords arrays (dedup behavior is enforced by Aho-Corasick,
//     and the matcher test catches it)
//   - validate severity enum values (kotlinx.serialization catches it)
//   - validate lawText length (manual judgment)
//   - enforce version bump convention (skill responsibility)
//
// Why not lint the entire assets/ tree: the rule JSON is the only file under
// app/src/main/assets/ that has a strict schema; everything else is binary
// (models), markdown (changelog), or test data.

const path = require('path');

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => { input += chunk; });
process.stdin.on('end', () => {
  let parsed = null;
  try {
    parsed = JSON.parse(input);
  } catch (_e) {
    process.exit(0);  // malformed envelope → fail open
  }

  const toolName = parsed && parsed.tool_name;
  if (toolName !== 'Edit' && toolName !== 'Write' && toolName !== 'MultiEdit') {
    process.exit(0);
  }

  const toolInput = (parsed && parsed.tool_input) || {};
  const filePath = typeof toolInput.file_path === 'string' ? toolInput.file_path : '';
  if (!filePath) process.exit(0);

  // Match only the rule JSON files; everything else under assets/ is opaque to us.
  // Accept either absolute POSIX-style (`/app/...`) or repo-relative (`app/...`),
  // and either forward or back slashes (Windows paths).
  const normPath = filePath.replace(/\\/g, '/').replace(/^\/+/, '');
  const isRulesJson = /(?:^|\/)app\/src\/main\/assets\/rules\/[a-z_]+_rules\.json$/i.test(normPath);
  if (!isRulesJson) process.exit(0);

  const fs = require('fs');
  if (!fs.existsSync(filePath)) process.exit(0);  // file deleted by edit? skip

  let raw;
  try {
    raw = fs.readFileSync(filePath, 'utf8');
  } catch (_e) {
    process.exit(0);  // IO error → fail open (don't block legitimate work)
  }

  // --- Check 1: JSON syntax ---
  let doc;
  try {
    doc = JSON.parse(raw);
  } catch (e) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision validate-rule-json hook:\n' +
      '  File: ' + filePath + '\n' +
      '  JSON parse error: ' + e.message + '\n' +
      '  Fix the JSON syntax and re-edit. `AssetRuleLoaderTest` would have\n' +
      '  caught this 30 s later — this hook surfaces it now.',
    );
    process.exit(2);
  }

  // --- Check 2: top-level `version` ---
  if (!('version' in doc) || typeof doc.version !== 'number' || !Number.isInteger(doc.version)) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision validate-rule-json hook:\n' +
      '  File: ' + filePath + '\n' +
      '  Top-level `version` must be an integer (current rules are at v' +
      (typeof doc.version === 'number' ? doc.version : '?') + ').\n' +
      '  `AssetRuleLoaderTest` asserts version; bump it on every rules bump\n' +
      '  per the `add-rule-entry` skill.',
    );
    process.exit(2);
  }

  // --- Check 3: rules array ---
  if (!Array.isArray(doc.rules)) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision validate-rule-json hook:\n' +
      '  File: ' + filePath + '\n' +
      '  Top-level `rules` must be an array.',
    );
    process.exit(2);
  }
  if (doc.rules.length === 0) {
    console.error(
      'BLOCKED by IceSpiritAI_Vision validate-rule-json hook:\n' +
      '  File: ' + filePath + '\n' +
      '  `rules` array is empty — this would zero out the rule engine.\n' +
      '  Did you mean to delete entries? If so, revert this edit; rules\n' +
      '  deletion should go through `add-rule-entry` (which never deletes).',
    );
    process.exit(2);
  }

  // --- Check 4: id uniqueness ---
  const seenIds = new Map();
  const duplicates = [];
  for (const r of doc.rules) {
    if (!r || typeof r !== 'object' || !r.id) continue;
    if (seenIds.has(r.id)) duplicates.push(r.id);
    seenIds.set(r.id, true);
  }
  if (duplicates.length > 0) {
    const uniq = [...new Set(duplicates)];
    console.error(
      'BLOCKED by IceSpiritAI_Vision validate-rule-json hook:\n' +
      '  File: ' + filePath + '\n' +
      '  Duplicate rule id(s): ' + uniq.join(', ') + '\n' +
      '  Every rule id must be unique across the file. `AssetRuleLoaderTest`\n' +
      '  asserts uniqueness. Re-id the colliding entry(ies) — naming\n' +
      '  convention: <domain-prefix>_<regulation-abbr>_<article>_<short>.',
    );
    process.exit(2);
  }

  // All checks passed.
  process.exit(0);
});