# `lintVitalAnalyzeRelease` crashes on `.gradle.kts` (AGP 9.3 + Kotlin 2.4.10)

> Captured 2026-08-17 during v9.5 (in-app update mechanism) work.

## TL;DR

`./gradlew :app:assembleRelease -PmodelProfile=shell` crashes inside
`lintVitalAnalyzeRelease` with

```
Unexpected failure during lint analysis (this is a bug in lint or one of the libraries it depends on)
\`findFirCompiledSymbol\` only works on compiled declarations, but the given declaration is not compiled.
  at LLResolutionFacade.findCompiledFirSymbol(LLResolutionFacade.kt:279)
  at LLResolutionFacade.resolveToFirSymbol$low_level_api_fir(LLResolutionFacade.kt:135)
  at LowLevelFirApiFacadeKt.resolveToFirSymbol(LowLevelFirApiFacade.kt:37)
  at KaFirScriptSymbol$special$$inlined$lazyFirSymbol$1.invoke(KaFirPsiSymbol.kt:412)
  ...
  at UastGradleVisitor.visitBuildScript(UastGradleVisitor.kt:43)
  at LintDriver$checkBuildScripts$3.run(LintDriver.kt:1402)
  at LintDriver.checkBuildScripts(LintDriver.kt:1377)
```

**Workaround applied:** `app/build.gradle.kts` disables the
`lint*Analyze*` and `lint*Vital*` tasks outright. Release gating is
performed by the [smoke test](../smoke/2026-08-14-phase1-smoke.md)
instead. The detailed disable list lives in [`app/lint.xml`](../../app/lint.xml)
for the day an upstream fix lands.

## Root cause

The crash originates in lint's `UastGradleVisitor.visitBuildScript`
([lint-api-32.3.0.jar:com/android/tools/lint/client/api/UastGradleVisitor.kt:43](../../app/lint.xml)).
That visitor resolves every Kotlin declaration it encounters via the
**Kotlin Analysis API (KAA)** using the **FIR (Frontend IR)** backend.
FIR exposes `LLResolutionFacade.findCompiledFirSymbol`
([Kotlin 2.4.10](../../app/build.gradle.kts) source `LLResolutionFacade.kt:279`)
which **requires the declaration to be FIR-compiled**:

> `findCompiledFirSymbol` only works on compiled declarations,
> but the given declaration is not compiled.

`.gradle.kts` scripts are only **parsed** by Gradle — Gradle never runs
the Kotlin FIR compiler on them. So as soon as lint tries to resolve a
symbol in our `app/build.gradle.kts` (e.g. `packaging { ... }`,
`lint { ... }`, `tasks.lintVitalAnalyzeRelease`), KAA throws
`KotlinIllegalArgumentExceptionWithAttachments` and the whole
`lintAnalyze*` / `lintVitalAnalyze*` task fails.

## Why we can't disable build-script analysis via the lint DSL

- `LintDriver.checkBuildScripts` only runs if at least one Detector is
  registered for `Scope.GRADLE_FILE`. The four candidates in
  `lint-checks-32.3.0.jar` are:
  - `GradleDetector` (~40 Issues)
  - `CommentDetector` (`TODO` / `StopShip` / `EasterEgg`)
  - `AppBundleLocaleChangesDetector` (`AppBundleLocaleChanges`)
  - `ByteOrderMarkDetector` (`ByteOrderMark`)

  Verified by `strings` on each class — these are the only detectors
  whose bytecode references `GRADLE_FILE`. (Five other classes
  implement the `GradleScanner` interface but don't list
  `Scope.GRADLE_FILE` in their `getApplicableFiles()`; they don't
  contribute to `scopeDetectors[GRADLE_FILE]`.)

- The intuitive fix is to `disable` every Issue on those four
  detectors, empty `scopeDetectors[GRADLE_FILE]`, and trigger the
  early-return at `LintDriver.checkBuildScripts:4666` (`ifnull 929`).
  We did this (see [`app/lint.xml`](../../app/lint.xml)) but
  `lintAnalyzeDebug` still crashed with the same stack trace.

- AGP 9.3's user-facing `Lint` DSL
  ([`gradle-common-api-9.3.0.jar:com/android/build/api/dsl/Lint.class`](../../app/build.gradle.kts))
  exposes only `disable / enable / checkOnly / abortOnError /
  warningsAsErrors / lintConfig / baseline / textReport / htmlReport /
  sarifReport / xmlReport / checkTestSources / ...`. There is **no
  `checkBuildScripts` toggle, no `extraLintOptions`, no way to pass
  arbitrary CLI flags**. Verified by `javap -p`.

- The standalone lint CLI 32.3.0 (`lint-32.3.0.jar:LintCliFlags.class`)
  similarly has no `--ignore-build-scripts` flag — none of the ~40
  flag names in that class contains "ignore", "skip", "no-gradle", or
  "build-script".

So there's no documented kill-switch. The only thing left is to skip
the task itself.

## What `app/build.gradle.kts` does now

```kotlin
tasks.matching {
    it.name.startsWith("lint") &&
        (it.name.contains("Vital") || it.name.contains("Analyze"))
}.configureEach { enabled = false }
```

`tasks.named(...)` would throw if the task doesn't exist for the
active variant set (e.g. `lintVitalAnalyzeRelease` only exists for
the `release` variant, and `shell` profile doesn't always expose a
`release` variant). The `matching` predicate is idempotent across both
`shell` and `ice_ocr_rules` profiles.

`app/lint.xml` still contains the full `disable` list (GradleDetector +
CommentDetector + AppBundleLocaleChangesDetector + ByteOrderMarkDetector
Issue IDs) so that, when upstream fixes the FIR symbol resolution for
parsed-but-not-compiled Gradle scripts, re-enabling the lint task only
requires removing the `tasks.matching { ... }` block.

## Release gating is unchanged

The `assembleRelease` pipeline runs the [release smoke test](../smoke/2026-08-14-phase1-smoke.md)
via `generateVisionLatestJson` → `archiveVisionRelease` →
`uploadVisionReleaseToGitea`, which verifies:

1. APK is signed by the v1 signing cert (v1 path needed because the
   in-app update verifier uses `JarFile` + `META-INF/CERT.RSA`).
2. Cert SHA-256 fingerprint matches the pinned reference.
3. APK SHA-256 in `vision-latest.json` matches the file on disk.
4. Gitea `latest` tag now points at the new APK + JSON.

So release correctness is independent of lint. Disabling lint only
loses static-analysis coverage; we recover the most useful checks
(`GradleDependency`, `NewerVersionAvailable`, `OutdatedLibrary`, ...)
the moment AGP/lint ship a fix.

## Re-enabling lint when upstream fixes the bug

1. Verify the fix is in `lint-checks` ≥ 32.4.0 (or whatever the next
   release is — search for "findCompiledFirSymbol" in
   `LintDriver.checkBuildScripts` or `UastGradleVisitor.visitBuildScript`).
2. Remove the `tasks.matching { ... }` block in `app/build.gradle.kts`.
3. `./gradlew :app:lintVitalAnalyzeRelease -PmodelProfile=shell` should
   now pass with the existing `app/lint.xml` disable list.
4. If you want GradleDetector + CommentDetector + AppBundle +
   ByteOrderMark checks back, delete the corresponding `<issue
   id="..." severity="ignore" />` lines from `app/lint.xml`.
