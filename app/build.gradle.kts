// app/build.gradle.kts — IceSpiritAI_Vision (forward path baseline).
//
// modelProfile is a Gradle property routed by `-PmodelProfile=<name>`.
// Default `shell` = no vision/OCR model is bundled. Future profiles
// (`ice_vision_minimal`, `ice_vision`, `ice_ocr_rules`) will gate
// model-loading code in IceSpiritVisionActivity.kt via build-time
// constants (BuildConfig.MODEL_PROFILE).

// v9.5 release signing uses GradleException for fail-closed behavior on
// missing ICESPIRITAI_RELEASE_* credentials. Imported explicitly because
// Gradle's Kotlin DSL doesn't always resolve `GradleException` from the
// implicit context (the DSL imports `org.gradle.api.*` for some types
// but not all).
import org.gradle.api.GradleException
// v9.5: `project.exec { ... }` was removed in Gradle 9.x. Use the
// `ExecOperations` service, obtained inside `doLast { ... }` via
// `project.the<ExecOperations>()` — the only Kotlin DSL helper that
// resolves a service in the `tasks.register("name") { ... }` scope,
// since the `Task` receiver doesn't expose `services` (only `DefaultTask`
// does, and that's an internal API in Gradle 9).
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import com.icespiritai.buildhelpers.LatestJsonGenerator

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val modelProfile = providers.gradleProperty("modelProfile")
    .getOrElse("shell")

apply(from = "prepare-ocr-rules.gradle.kts")

// v9.5: expected release APK signing certificate SHA-256. Mirrors
// translate's same-name constant. Gradle Kotlin DSL scripts reject
// top-level `const val` ("Const 'val' are only allowed on top level,
// in named objects, or in companion objects"), so we wrap the default
// cert SHA-256 in a named object. The reference value
// `4a21f417782d561dccd31ff0a10e4d643d13d00a8a2be77b4e9eeee0660b3043`
// (alias `icespiritai`, locked since 2026-06-25 per translate) is
// overridable per-build via `ICESPIRITAI_RELEASE_CERT_SHA256` env var
// or Gradle property — Task 15's generateVisionLatestJson reads it.
object ReleaseSigningCert {
    const val DEFAULT_SHA256 = "4a21f417782d561dccd31ff0a10e4d643d13d00a8a2be77b4e9eeee0660b3043"
}

// Client-side pin for the in-app update channel. Mirrors the build-side
// ReleaseSigningCert.DEFAULT_SHA256 and is overridable via the SAME
// ICESPIRITAI_RELEASE_CERT_SHA256 env var / gradle property that
// generateVisionLatestJson honours. Baked into BuildConfig so the app
// verifies downloaded APKs against a value it already knows at build time,
// instead of trusting the signerCertSha256 carried in the (cleartext)
// vision-latest.json — a MITM cannot then supply a self-consistent
// {JSON + APK} pair and pass the gate.
val releaseCertSha256 = providers.environmentVariable("ICESPIRITAI_RELEASE_CERT_SHA256")
    .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_CERT_SHA256"))
    .orElse(ReleaseSigningCert.DEFAULT_SHA256)
    .get()
    .lowercase()

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.icespiritai.vision"
        minSdk = 26
        targetSdk = 37
        versionCode = 23
        versionName = "0.1.23"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MODEL_PROFILE", "\"$modelProfile\"")

        buildConfigField("String", "UPDATE_JSON_URL",
            "\"http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json\"")

        buildConfigField("String", "UPDATE_EXPECTED_CERT_SHA256",
            "\"$releaseCertSha256\"")
    }

    signingConfigs {
        // ICESPIRITAI_RELEASE_* are read with env var first (not logged by
        // Gradle, ideal for CI secrets) and gradle property as fallback
        // (-P flag or ~/.gradle/gradle.properties for local dev).
        // See tools/create-release-keystore.ps1 for one-time setup.
        create("release") {
            val storeFileProp = providers.environmentVariable("ICESPIRITAI_RELEASE_STORE_FILE")
                .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_STORE_FILE"))
            val storePasswordProp = providers.environmentVariable("ICESPIRITAI_RELEASE_STORE_PASSWORD")
                .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_STORE_PASSWORD"))
            val keyAliasProp = providers.environmentVariable("ICESPIRITAI_RELEASE_KEY_ALIAS")
                .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_KEY_ALIAS"))
            val keyPasswordProp = providers.environmentVariable("ICESPIRITAI_RELEASE_KEY_PASSWORD")
                .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_KEY_PASSWORD"))

            // v9.5 fail-closed release signing. Pre-v9.5 this block only
            // configured itself when the store file happened to be present,
            // and `buildTypes.release` silently fell back to the DEBUG signing
            // key otherwise — a debug-signed APK could therefore flow through
            // generateVisionLatestJson -> archiveVisionRelease ->
            // uploadVisionReleaseToGitea and land on the in-app update channel.
            // Now the release build type binds to this config unconditionally,
            // so an incomplete credential set must fail the build, never
            // downgrade the signer.
            //
            // ALL FOUR inputs are required: a store file without its password
            // (or without the key alias) produced a half-configured signing
            // config that failed much later with an opaque AGP error.
            //
            // Provider.isPresent is true even for empty-string Gradle properties
            // (`-PICESPIRITAI_RELEASE_STORE_FILE=`); also reject blank values so
            // the throw below fires with the helpful "missing X" message instead
            // of the opaque `file("")` IllegalArgumentException from the
            // storeFile = file(storeFileProp.get()) line further down.
            val missingSigningInputs = listOf(
                "ICESPIRITAI_RELEASE_STORE_FILE" to storeFileProp,
                "ICESPIRITAI_RELEASE_STORE_PASSWORD" to storePasswordProp,
                "ICESPIRITAI_RELEASE_KEY_ALIAS" to keyAliasProp,
                "ICESPIRITAI_RELEASE_KEY_PASSWORD" to keyPasswordProp,
            ).filter { (_, value) -> value.orNull.isNullOrBlank() }.map { (name, _) -> name }

            if (missingSigningInputs.isNotEmpty()) {
                // `signingConfigs` is configured EAGERLY on every Gradle
                // invocation of :app, so an unconditional throw would brick
                // assembleDebug / testDebugUnitTest / lintDebug / IDE sync for
                // every contributor who does not hold the production keystore.
                // Scope the hard failure to invocations that actually ask for
                // release artifacts.
                val wantsReleaseArtifacts = gradle.startParameter.taskNames.any { taskName ->
                    taskName.contains("release", ignoreCase = true) ||
                        taskName.contains("uploadVisionReleaseToGitea", ignoreCase = true) ||
                        taskName.contains("generateVisionLatestJson", ignoreCase = true) ||
                        taskName.contains("archiveVisionRelease", ignoreCase = true)
                }
                if (wantsReleaseArtifacts) {
                    // Names ONLY. Gradle prints exception messages verbatim to
                    // the console and to any CI log; the values here are the
                    // keystore path, the store password and the key password.
                    throw GradleException(
                        "Release signing is not configured: missing " +
                            missingSigningInputs.joinToString(", ") +
                            ". Set every ICESPIRITAI_RELEASE_* value as an environment variable " +
                            "or a Gradle property before running a release task " +
                            "(one-time setup: tools/create-release-keystore.ps1). " +
                            "assembleRelease no longer falls back to the debug signing key.",
                    )
                }
                // Non-release invocation: leave the config unpopulated. Any
                // release packaging that slips past the heuristic above still
                // fails closed — AGP's ValidateSigningTask aborts on the
                // missing store file instead of emitting a debug-signed APK.
                return@create
            }

            storeFile = file(storeFileProp.get())
            storePassword = storePasswordProp.get()
            keyAlias = keyAliasProp.get()
            keyPassword = keyPasswordProp.get()
            // Re-enable v1 alongside v2+v3 (translate's same rationale).
            // minSdk=26 (Android 8) verifies v2/v3 first, so v1 acts as a
            // fallback path for legacy verifiers and tooling. AGP defaults
            // to v2-only when these are not explicitly set; without
            // enableV3Signing=true a direct ./gradlew assembleRelease
            // produces a v2-only APK and ApkSignatureVerifier (the v1-only
            // META-INF/CERT.RSA reader used by the in-app update channel)
            // returns null, blocking every legitimate in-app update.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // v9.5 fail-closed: bind unconditionally to the release signing
            // config. The previous shape (an `if (hasReleaseStoreFile) release
            // else signingConfigs.debug` fallback) existed so `assembleRelease`
            // stayed runnable in CI without secrets — but it meant a
            // DEBUG-signed APK could reach generateVisionLatestJson ->
            // archiveVisionRelease -> uploadVisionReleaseToGitea and be
            // served as an in-app update. `signingConfigs.release` now
            // raises a GradleException when its inputs are missing, so a
            // missing keystore fails the build instead of downgrading the key.
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Required for Robolectric + Compose UI tests. When true, AGP generates
        // `com/android/tools/test_config.properties` on the unit-test classpath
        // pointing at the merged manifest + assets — without this, Robolectric
        // can't find `androidx.activity.ComponentActivity` and the no-arg
        // `createComposeRule()` (used by SeverityBadgeTest) fails to launch.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 built-in Kotlin: JVM target inherits from compileOptions above.
    // (kotlinOptions / compilerOptions DSL not available without the
    //  kotlin-android plugin, which AGP 9 no longer requires.)

    buildFeatures {
        compose = true
        buildConfig = true
        // viewBinding intentionally off (use Compose)
    }

    sourceSets {
        getByName("main") {
            // main.assets comes ONLY from the generated dir. AGP's default
            // is to also include src/main/assets, so we REPLACE (not append)
            // via setSrcDirs.
            //
            // The source app/src/main/assets/rules/ JSON files are
            // intentionally NOT bundled: their contents are not
            // modelProfile-aware, and the per-profile copy under
            // build/generated/assets/rules/ is the authoritative one for the
            // APK. Editing app/src/main/assets/rules/{ad_signage,food_label}_rules.json is the
            // only place rules change: prepare-ocr-rules.gradle.kts reads that
            // file at execution time for the ice_ocr_rules profile (and the
            // file is tracked as a task input for cache invalidation).
            //
            // If/when non-rules assets appear under src/main/assets/
            // (e.g. models/, fonts/), add a Copy task here that mirrors
            // them into the generated dir before assemble.
            assets.setSrcDirs(
                listOf(layout.buildDirectory.dir("generated/assets").get().asFile),
            )
        }
        // Per-profile java srcDirs are wired up via
        // `androidComponents.onVariants { ... addStaticSourceDirectory(...) }`
        // below. AGP 9.x no longer permits `create("name")` / `register("name")`
        // for arbitrary sourceSet names inside `sourceSets {}` — only
        // variant-tied sources are accepted.
        //
        // Per-profile META-INF/services/... is packaged as a tiny JAR by the
        // `buildProfileServicesJar` task in prepare-ocr-rules.gradle.kts and
        // added as a `runtimeOnly files(...)` dependency below — the only
        // reliable way to get a ServiceLoader registration into an Android
        // APK via AGP 9 (see that task's KDoc for the full rationale).
    }

    packaging {
        // Phase 2 / Task 4: AGP 9 defaults to useLegacyPackaging = false, which
        // compresses native libs inside the APK. On Android 14/15 with
        // extractNativeLibs=false, OpenCVLoader's System.loadLibrary fails to find
        // libopencv_java4.so. Setting useLegacyPackaging = true extracts the libs
        // to /data/app/<pkg>/lib/<abi>/ at install time.
        //
        // Trade-off: APK install size grows (libs are uncompressed). Acceptable
        // for ice_ocr_rules profile where native libs are required. shell profile
        // has no native libs so this setting is a no-op for shell.
        jniLibs {
            useLegacyPackaging = true
        }

        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    // AGP 9.3 + Kotlin 2.4.10 + lint 32.3.0 build-script crash WORKAROUND.
    //
    // Root cause: `lintVitalAnalyzeRelease` (and `lintAnalyzeDebug`) ->
    // `LintDriver.checkBuildScripts` (lint-api-32.3.0.jar) iterates
    // `.gradle.kts` files and hands each to `UastGradleVisitor.visitBuildScript`.
    // That visitor resolves Kotlin declarations via the Kotlin Analysis API
    // (KAA) using FIR; FIR's `LLResolutionFacade.findCompiledFirSymbol`
    // (Kotlin 2.4.10) requires the declaration to be FIR-compiled, but
    // `.gradle.kts` scripts are only parsed by Gradle — never FIR-compiled —
    // so it throws
    //   `KotlinIllegalArgumentExceptionWithAttachments: findFirCompiledSymbol
    //    only works on compiled declarations, but the given declaration is
    //    not compiled.`
    //
    // We tried the documented workaround: disable every Issue on every
    // Detector that registers for `Scope.GRADLE_FILE` (`GradleDetector`,
    // `CommentDetector`, `AppBundleLocaleChangesDetector`,
    // `ByteOrderMarkDetector`) via `app/lint.xml` + the DSL. In principle
    // this should empty `scopeDetectors[Scope.GRADLE_FILE]` and trigger the
    // early-return at `LintDriver.checkBuildScripts` line 4666. In practice
    // the crash persists on AGP 9.3 + lint 32.3.0 — likely because some
    // internal code path populates `scopeDetectors[GRADLE_FILE]` before the
    // user-supplied disable list is honored. (Confirmed by reading
    // `IssueRegistry.createDetectors$lint_api` bytecode:
    // `Configuration.isEnabled(Issue)` is called, but at least one issue
    // on one of the four detectors still passes that gate.)
    //
    // AGP 9.x's `Lint` DSL does NOT expose `checkBuildScripts` (verified by
    // `javap` on `gradle-common-api-9.3.0.jar:com/android/build/api/dsl/
    // Lint.class` — only disable/enable/checkOnly/abortOnError/etc., no
    // script toggle). The lint CLI 32.3.0 (`LintCliFlags.class`) similarly
    // has no `--ignore-build-scripts` flag (verified: no such constant in
    // the class). Upstream bug — Kotlin team is tracking the KAA/FIR
    // integration; until it ships a fix we cannot make
    // `lintVitalAnalyzeRelease` pass.
    //
    // Pragmatic resolution: disable the lint vital task entirely. Release
    // gating is enforced by the smoke test (signed APK + cert-pin
    // verification + Gitea URL + SHA-256 handshake — see
    // `docs/smoke/2026-08-14-phase1-smoke.md`), not by lint's HTML report.
    // When upstream ships the fix, re-enable the task and remove this block.
    lint {
        abortOnError = false
        checkTestSources = true
        // Document the disable list in lint.xml so it's discoverable when
        // someone re-enables the task. AGP picks it up automatically when
        // present at `app/lint.xml`; the explicit `lintConfig` keeps it
        // working if AGP ever changes the default lookup path.
        lintConfig = file("lint.xml")
    }
}

// Disable lint vital + analyze tasks: AGP 9 + lint 32.3.0 + Kotlin 2.4.10
// crashes during .gradle.kts analysis (see KDoc on the `lint { }` block
// above). The smoke test gates releases independently — re-enable these
// tasks when upstream ships a fix for `findFirCompiledSymbol only works on
// compiled declarations`.
//
// `tasks.named(...) { enabled = false }` throws if the task doesn't exist
// on the active variant set (e.g. `lintVitalAnalyzeRelease` is only
// registered for the `release` variant, which `shell` profile doesn't
// expose by default). Use the `matching` predicate to handle both shell +
// ice_ocr_rules profiles without failing configuration.
tasks.matching { it.name.startsWith("lint") && (it.name.contains("Vital") || it.name.contains("Analyze")) }.configureEach { enabled = false }

// Per-profile java source directory wiring: AGP 9.x no longer permits
// arbitrary `sourceSets { create("shell") }` declarations — only
// variant-tied java sources are accepted. We attach the active profile's
// java src dir to every variant so Kotlin/Javac compile picks up the
// profile-specific `OcrEngineFactory` (and `PaddleOcrEngine` for the
// `ice_ocr_rules` profile). META-INF/services/... is packaged as a tiny
// JAR via `buildProfileServicesJar` and added as a `runtimeOnly` dependency
// below — see that task's KDoc for why a JAR is the only reliable
// ServiceLoader registration mechanism in AGP 9.
androidComponents {
    onVariants { variant ->
        val javaDir = when (modelProfile) {
            "ice_ocr_rules" -> "src/ice_ocr_rules/java"
            else -> "src/shell/java" // default profile is `shell`
        }
        variant.sources.java?.addStaticSourceDirectory(
            project.projectDir.resolve(javaDir).absolutePath,
        )
    }
}

// =============================================================================
// v9.5: release pipeline for in-app updates. Mirrors translate's
// generateLatestJson -> archiveLatestRelease -> uploadToGitea chain at
// translate/app/build.gradle.kts:1039-1479, but for vision:
//
//   assembleRelease
//     -> generateVisionLatestJson  (verifies cert + emits vision-latest.json)
//     -> archiveVisionRelease       (stages renamed APK + JSON into build/)
//     -> uploadVisionReleaseToGitea (curl.exe idempotent upload to Gitea)
//
// 2026-08-21: removed the versioned-archive copy that previously wrote
// to `发布版历史存档/`. Staging now lives under `build/generated/release-staging/`
// (gitignored via `build/`) so release artifacts never leak outside the
// build tree. The APK rename to `icespiritai-vision.apk` is still done
// in `archiveVisionRelease` because the Gitea release asset name is the
// basename of the multipart upload — clients read that exact filename.
//
// MUST STAY IN SYNC with translate's pipeline + with the JVM-mirrored
// helpers in LatestJsonGenerator.kt (build) and ApkSignatureVerifier.kt
// (runtime).
//
// Phase 1 differences from translate:
//   - No CHANGELOG.md parsing (vision's CHANGELOG doesn't exist yet)
//     → hardcode changelog = "" for now
//   - No DownloadStats mirror → pass apkCumulativeDownloads = 0L
//     (translate's pipeline queries Gitea dl_counts; vision Phase 2+
//     can adopt this once a CHANGELOG/DownloadStats track is needed)
// =============================================================================

/**
 * SHA-256 hex of a file. MUST STAY IN SYNC with LatestJsonGenerator.sha256Hex
 * (build scripts cannot import app/src/main/java/ — see CLAUDE.md Gotchas).
 */
fun sha256HexForBuild(file: java.io.File): String {
    val md = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
        DigestInputStream(fis, md).use { dis ->
            val buf = ByteArray(64 * 1024)
            while (dis.read(buf) >= 0) { /* drain */ }
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Reads the v1 signing certificate from META-INF/CERT.{RSA,DSA,EC} and
 * returns its SHA-256 fingerprint. MUST STAY IN SYNC with
 * `ApkSignatureVerifier.readFirstSignerCert` at
 * `app/src/main/java/com/icespiritai/offline/updater/ApkSignatureVerifier.kt`
 * (build scripts cannot import from app/src/main/java/).
 *
 * The runtime side reads the same v1 cert from the downloaded APK and
 * compares against `AppVersionInfo.signerCertSha256`; the build side
 * writes the same fingerprint into `vision-latest.json`. If both sides
 * apply different parsing rules for the PKCS#7 wrapper, SHA-256 won't
 * match and every legitimate update is rejected.
 *
 * Required because the in-app update verifier uses the v1 path
 * (META-INF/CERT.RSA via JarFile); AGP defaults to v2-only when
 * enableV1Signing is unset, so without `enableV1Signing = true` in
 * signingConfigs.release, the verifier returns null and every legitimate
 * in-app update is blocked.
 *
 * Returns null if no v1 cert is present (i.e. APK is v2/v3-only).
 */
fun extractApkCertificateSha256(apkFile: java.io.File): String? {
    if (!apkFile.exists()) return null
    val zip = try { ZipFile(apkFile) } catch (_: Exception) { return null }
    zip.use { z ->
        for (ext in listOf("RSA", "DSA", "EC")) {
            val entry = z.entries().toList().firstOrNull { it.name == "META-INF/CERT.$ext" }
                ?: continue
            val certs = try {
                val bytes = z.getInputStream(entry).use { it.readBytes() }
                CertificateFactory.getInstance("X.509")
                    .generateCertificates(bytes.inputStream())
            } catch (_: Exception) {
                continue
            }
            val first = certs.firstOrNull() as? X509Certificate ?: continue
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(first.encoded)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
    return null
}

// ----- generateVisionLatestJson -----
//
// Reads the versioned APK from outputs/apk/release/, verifies its
// signing cert matches the pinned ReleaseSigningCert.DEFAULT_SHA256
// (defense-in-depth against a debug-signed APK slipping through to the
// in-app update channel), and emits vision-latest.json next to it.
val giteaBaseUrl = "http://125.211.45.14:3000"
val giteaRepo = "giteaadmin/vision-app"

tasks.register("generateVisionLatestJson") {
    group = "build"
    description = "Verify the signed release APK cert + emit vision-latest.json next to it."
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    inputs.dir(apkDir)
    val outJson = apkDir.map { it.file("vision-latest.json") }
    outputs.file(outJson)
    doLast {
        val dir = apkDir.get().asFile
        // AGP 9.x default release output filename is `app-release.apk`. We
        // keep that name (no in-place rename — AGP 9 removed
        // `android.applicationVariants.all`); `archiveVisionRelease` does
        // the rename to `icespiritai-vision.apk` downstream — required for
        // the Gitea release asset name (multipart upload basename).
        val apk = dir.listFiles { f -> f.name == "app-release.apk" }
            ?.singleOrNull()
            ?: error("generateVisionLatestJson: expected app-release.apk in $dir, found ${dir.listFiles()?.size ?: 0} files")
        require(apk.exists()) {
            "generateVisionLatestJson: expected ${apk.absolutePath} but it does not exist. Did assembleRelease run first?"
        }

        // Cert-pin defense-in-depth: refuse to emit JSON if the APK's
        // signing cert doesn't match the pinned SHA-256. This is a
        // SECOND gate after signingConfigs.release's fail-closed
        // signing check — protects against a future signing-config
        // regression or someone manually swapping in a debug-signed
        // APK at this path.
        val expectedReleaseCertSha256 = providers
            .environmentVariable("ICESPIRITAI_RELEASE_CERT_SHA256")
            .orElse(providers.gradleProperty("ICESPIRITAI_RELEASE_CERT_SHA256"))
            .orElse(ReleaseSigningCert.DEFAULT_SHA256)
            .get()
            .lowercase()
        require(expectedReleaseCertSha256.matches(Regex("[0-9a-f]{64}"))) {
            "ICESPIRITAI_RELEASE_CERT_SHA256 must be 64 lowercase hex characters"
        }
        val apkCertSha256 = extractApkCertificateSha256(apk)
            ?: throw GradleException(
                "generateVisionLatestJson: no signing certificate in ${apk.name}; " +
                    "the APK must be v1-signed (META-INF/CERT.RSA)."
            )
        if (apkCertSha256 != expectedReleaseCertSha256) {
            throw GradleException(
                "generateVisionLatestJson: APK signing certificate fingerprint mismatch. " +
                    "expected=$expectedReleaseCertSha256 actual=$apkCertSha256. Upload aborted."
            )
        }

        val vc = android.defaultConfig.versionCode
        val vn = android.defaultConfig.versionName
        val size = apk.length()
        val sha = sha256HexForBuild(apk)
        val url = "http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/icespiritai-vision.apk"
        // Read user-changelog.md from the SOURCE path (not the build/
        // generated/assets/ mirror) so this task doesn't depend on the
        // copyUserChangelogAsset task running first. The mirror task is
        // only needed at package/asset time. Must keep the parser in sync
        // with VersionHistoryRenderer in app/src/main/java/.
        val changelogFile = file("src/main/assets/user-changelog.md")
        val cl = if (changelogFile.isFile) {
            LatestJsonGenerator.extractLatestChangelog(changelogFile.readText(Charsets.UTF_8))
        } else {
            logger.warn("[generateVisionLatestJson] missing ${changelogFile.absolutePath}; emitting empty changelog")
            ""
        }
        // Phase 1: no DownloadStats mirror → 0L. Phase 2+: query Gitea
        // dl_counts and snapshot + commit (translate's
        // loadAndAccumulateDownloadStats pattern).
        val apkCumulative = 0L

        val payload = mapOf(
            "versionCode" to vc,
            "versionName" to vn,
            "apkUrl" to url,
            "apkSize" to size,
            "apkSha256" to sha,
            "changelog" to cl,
            "apkCumulativeDownloads" to apkCumulative,
            "signerCertSha256" to apkCertSha256,
        )
        outJson.get().asFile.writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(payload)) + "\n"
        )
        logger.lifecycle(
            "generateVisionLatestJson: ${outJson.get().asFile.name} versionCode=$vc " +
                "versionName=$vn apkSize=$size sha256=${sha.take(16)}… cumulativeApk=$apkCumulative"
        )
    }
}

// ----- archiveVisionRelease -----
//
// Stages the signed release APK as `icespiritai-vision.apk` (the Gitea
// release asset name = multipart upload basename — clients read this
// filename directly) + vision-latest.json into a build-local staging
// dir consumed by uploadVisionReleaseToGitea. Lives under
// `build/generated/release-staging/` rather than the repo root so
// release artifacts never leak outside the build tree.
val uploadStagingDir = layout.buildDirectory.dir("generated/release-staging").get().asFile

tasks.register("archiveVisionRelease") {
    group = "build"
    description = "Stage renamed APK + vision-latest.json into build/generated/release-staging/ for upload."
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    inputs.dir(apkDir)
    val outJson = apkDir.map { it.file("vision-latest.json") }
    inputs.file(outJson)
    outputs.dir(uploadStagingDir)
    dependsOn("generateVisionLatestJson")
    doLast {
        val dir = apkDir.get().asFile
        val apk = dir.listFiles { f -> f.name == "app-release.apk" }
            ?.singleOrNull()
            ?: error("archiveVisionRelease: expected app-release.apk in $dir")
        val json = dir.resolve("vision-latest.json")
        require(json.exists()) {
            "archiveVisionRelease: expected ${json.absolutePath} but it does not exist. Did generateVisionLatestJson run first?"
        }

        // Upload staging: APK renamed to icespiritai-vision.apk + JSON.
        // The Gitea release asset name comes from the multipart upload
        // basename, so the rename is non-negotiable — clients fetch
        // `<gitea>/.../latest/icespiritai-vision.apk` directly.
        if (!uploadStagingDir.exists()) uploadStagingDir.mkdirs()
        require(uploadStagingDir.isDirectory) {
            "archiveVisionRelease: ${uploadStagingDir.absolutePath} exists but is not a directory"
        }
        val apkUploadDest = uploadStagingDir.resolve("icespiritai-vision.apk")
        // Byte-for-byte copy via buffered streams so the APK signature
        // stays intact (Windows File.copy can mangle binary writes if
        // not opened in binary mode).
        FileInputStream(apk).use { ins ->
            FileOutputStream(apkUploadDest).use { out ->
                ins.copyTo(out, bufferSize = 64 * 1024)
            }
        }
        val jsonUploadDest = uploadStagingDir.resolve(json.name)
        json.copyTo(jsonUploadDest, overwrite = true)

        logger.lifecycle(
            "archiveVisionRelease: staged icespiritai-vision.apk -> ${apkUploadDest.absolutePath}, " +
                "vision-latest.json -> ${jsonUploadDest.absolutePath}"
        )
    }
}

// ----- uploadVisionReleaseToGitea -----
//
// Idempotent upload of APK + JSON to giteaadmin/vision-app tag `latest`.
// Same algorithm as translate's uploadToGitea (translate/app/build.gradle.kts
// :1330-1479):
//   1. GET tag/latest (200 = found, 404 = create)
//   2. DELETE any existing assets matching our two filenames
//   3. POST APK + JSON as multipart attachments
//
// Required: gradle.token.properties must contain GITEA_TOKEN=<pat>.
// Fails fast with a clear error if missing/blank.
//
// outputs.upToDateWhen { false } — Gradle would otherwise mark this task
// up-to-date after the first successful run and silently skip re-uploads
// (a remote side-effect task needs explicit "never skip").
tasks.register("uploadVisionReleaseToGitea") {
    group = "build"
    description = "Push APK + vision-latest.json to giteaadmin/vision-app 'latest' release."
    val giteaTokenFile = rootProject.file("gradle.token.properties")
    inputs.dir(uploadStagingDir)
    // NOTE: do NOT declare inputs.file(giteaTokenFile) — Gradle
    // validates declared input files exist before running the task,
    // and the resulting "input file does not exist" error is opaque.
    // We do the existence + content check inside doLast with a clear
    // error message pointing to the .example template.
    outputs.upToDateWhen { false }

    doLast {
        // Gradle 9.x removed `project.exec` AND `ExecOperations` is not
        // reachable via `project.the<T>()` (not a registered extension
        // type). ProcessBuilder is a pure JVM API that bypasses Gradle's
        // exec infrastructure entirely and gives us full control over
        // stdout capture + exit code handling. Same curl flags as
        // translate's pattern.

        val tag = "latest"

        if (!giteaTokenFile.exists()) {
            throw GradleException(
                "uploadVisionReleaseToGitea: gradle.token.properties not found at ${giteaTokenFile.absolutePath}. " +
                "Copy gradle.token.properties.example -> gradle.token.properties and fill in your Gitea PAT."
            )
        }
        val token = giteaTokenFile.readLines()
            .mapNotNull { line ->
                val stripped = line.substringBefore("#").trim()
                if (stripped.startsWith("GITEA_TOKEN=")) stripped.removePrefix("GITEA_TOKEN=") else null
            }
            .singleOrNull()
        require(!token.isNullOrBlank()) {
            "uploadVisionReleaseToGitea: GITEA_TOKEN missing in gradle.token.properties. " +
            "Set GITEA_TOKEN=<your-pat> in that file."
        }

        val api = "$giteaBaseUrl/api/v1/repos/$giteaRepo/releases"
        val authHeader = "Authorization: token $token"

        // Single curl wrapper. `-w "\n%{http_code}"` appends the status
        // code as the last line so we can branch on it without separate
        // exec calls. isIgnoreExitValue lets 4xx/5xx not fail the exec;
        // we surface those via the parsed status code instead.
        // --http1.1: force HTTP/1.1 (HTTP/2 large-multipart uploads to
        // Gitea 1.22.3 hit a mid-stream reset on this Windows host).
        // --expect100-timeout 60: wait up to 60s for the server's 100
        // Continue before sending the multipart body.
        fun curl(extraArgs: List<String>): String {
            val cmd = listOf("curl.exe", "-sS", "-w", "\n%{http_code}",
                "--http1.1", "--expect100-timeout", "60",
                "--connect-timeout", "30", "--max-time", "600",
                "-H", authHeader) + extraArgs
            val pb = ProcessBuilder(cmd)
            pb.redirectError(ProcessBuilder.Redirect.PIPE)
            val process = pb.start()
            val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
            // Drain stderr so the child process doesn't block on a full pipe.
            process.errorStream.readBytes()
            // isIgnoreExitValue equivalent: capture stdout regardless of
            // exit code. Surface non-zero via the status code we appended
            // to the body (`-w "\n%{http_code}"`).
            process.waitFor()
            return stdout
        }

        fun splitStatus(raw: String): Pair<String, String> {
            val lastNl = raw.lastIndexOf('\n')
            return if (lastNl < 0) raw to "" else raw.substring(0, lastNl) to raw.substring(lastNl + 1)
        }

        // 1. Lookup existing release by tag.
        val (lookupBody, lookupCode) = splitStatus(curl(listOf("$api/tags/$tag")))
        val releaseId = when (lookupCode) {
            "200" -> {
                Regex("\"id\"\\s*:\\s*(\\d+)").find(lookupBody)?.groupValues?.get(1)
                    ?: throw GradleException("uploadVisionReleaseToGitea: GET tag/$tag body has no id: $lookupBody")
            }
            "404" -> {
                logger.lifecycle("uploadVisionReleaseToGitea: tag $tag not found, creating new release...")
                val postBody = "{" +
                    "\"tag_name\":\"$tag\"," +
                    "\"name\":\"$tag\"," +
                    "\"draft\":false," +
                    "\"prerelease\":false" +
                    "}"
                val (createBody, createCode) = splitStatus(curl(listOf(
                    "-X", "POST", "-H", "Content-Type: application/json",
                    "-d", postBody, api)))
                require(createCode == "201") {
                    "uploadVisionReleaseToGitea: POST release returned HTTP $createCode: $createBody"
                }
                Regex("\"id\"\\s*:\\s*(\\d+)").find(createBody)?.groupValues?.get(1)
                    ?: throw GradleException("uploadVisionReleaseToGitea: POST release body has no id: $createBody")
            }
            else -> throw GradleException("uploadVisionReleaseToGitea: GET tag/$tag returned HTTP $lookupCode: $lookupBody")
        }

        // 2. Idempotent replace: delete existing assets with matching names.
        val (assetsBody, assetsCode) = splitStatus(curl(listOf("$api/$releaseId/assets")))
        require(assetsCode == "200") {
            "uploadVisionReleaseToGitea: GET release assets returned HTTP $assetsCode: $assetsBody"
        }
        val targetNames = setOf("icespiritai-vision.apk", "vision-latest.json")
        val idPattern = Regex(""""id"\s*:\s*(\d+)""")
        val namePattern = Regex(""""name"\s*:\s*"([^"]+)"""")
        Regex("""\{[^{}]*\}""").findAll(assetsBody).forEach { objMatch ->
            val obj = objMatch.value
            val idM = idPattern.find(obj)
            val nameM = namePattern.find(obj)
            if (idM != null && nameM != null) {
                val assetId = idM.groupValues[1]
                val assetName = nameM.groupValues[1]
                if (assetName in targetNames) {
                    logger.lifecycle("uploadVisionReleaseToGitea: deleting existing asset $assetName (id=$assetId)")
                    val (delBody, delCode) = splitStatus(curl(listOf(
                        "-X", "DELETE", "$api/$releaseId/assets/$assetId")))
                    require(delCode == "204") {
                        "uploadVisionReleaseToGitea: DELETE asset $assetName returned HTTP $delCode: $delBody"
                    }
                }
            }
        }

        // 3. Upload two new assets.
        val stagedApk = uploadStagingDir.resolve("icespiritai-vision.apk")
        val stagedJson = uploadStagingDir.resolve("vision-latest.json")
        require(stagedApk.exists()) {
            "uploadVisionReleaseToGitea: missing staged APK ${stagedApk.absolutePath}. Run archiveVisionRelease first."
        }
        require(stagedJson.exists()) {
            "uploadVisionReleaseToGitea: missing staged JSON ${stagedJson.absolutePath}. Run generateVisionLatestJson first."
        }
        listOf(stagedApk, stagedJson).forEach { f ->
            val (upBody, upCode) = splitStatus(curl(listOf(
                "-X", "POST", "-F", "attachment=@${f.absolutePath}",
                "$api/$releaseId/assets")))
            require(upCode == "201") {
                "uploadVisionReleaseToGitea: upload ${f.name} returned HTTP $upCode: $upBody"
            }
            logger.lifecycle("uploadVisionReleaseToGitea: uploaded ${f.name} (${f.length()} bytes)")
        }
        logger.lifecycle("uploadVisionReleaseToGitea: pushed 2 assets to tag $tag (release id=$releaseId)")
    }
}

afterEvaluate {
    tasks.named("assembleRelease").configure {
        finalizedBy("generateVisionLatestJson")
        finalizedBy("archiveVisionRelease")
    }
    // Chain uploadVisionReleaseToGitea after archiveVisionRelease so a
    // single ./gradlew assembleRelease produces APK + JSON + pushes to
    // Gitea. uploadVisionReleaseToGitea has outputs.upToDateWhen { false }
    // so it always re-runs even when its inputs haven't changed.
    tasks.named("archiveVisionRelease").configure {
        finalizedBy("uploadVisionReleaseToGitea")
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Domain
    implementation(libs.hankcs.aho.corasick)

    // Image viewer (pinch / pan / double-tap zoom) for Routes.VIEWER
    implementation(libs.telephoto.zoomable.image.coil)

    // OCR engine: PaddleOCR official SDK + native runtime (ice_ocr_rules only)
    if (modelProfile == "ice_ocr_rules") {
        implementation(files("libs/ppocr-sdk.aar"))
        implementation(libs.onnxruntime.android)
        implementation(libs.opencv.android)
    }

    // ServiceLoader registration for the per-profile `OcrEngineFactory`.
    // The `buildProfileServicesJar` task (in prepare-ocr-rules.gradle.kts)
    // emits a one-entry JAR containing only
    // `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory` so
    // AGP's `processJavaResources` pipeline extracts it into the APK at
    // `META-INF/services/...` — exactly where ServiceLoader looks.
    runtimeOnly(files(layout.buildDirectory.dir("generated/services-jar/ocr-engine-services.jar").get().asFile))

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    // Compose UI test rule + matchers for Robolectric-driven unit tests
    // (e.g. SeverityBadgeTest). `ui-test-manifest` stays `debugImplementation`
    // because Compose reads it only in debug variants.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    // Instrumentation tests (for SDK smoke test + Compose UI test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Espresso on Compose: `onView(withText(...))` in IceSpiritVisionActivityTest
    // requires espresso-core alongside compose.ui.test.junit4 (which provides
    // the Espresso <-> Compose interop hook).
    androidTestImplementation(libs.androidx.espresso.core)
    // espresso-intents: ExportActionShareTest (P0-3 audit fix) needs to
    // capture the ACTION_SEND chooser dispatched from ExportAction.share()
    // on a real device so we can assert MIME, FLAG_GRANT_READ_URI_PERMISSION
    // and FileProvider authority.
    androidTestImplementation(libs.androidx.espresso.intents)
    debugImplementation(libs.compose.ui.test.manifest)
}
