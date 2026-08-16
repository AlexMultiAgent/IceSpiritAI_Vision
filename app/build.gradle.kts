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

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.icespiritai.vision"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MODEL_PROFILE", "\"$modelProfile\"")

        buildConfigField("String", "UPDATE_JSON_URL",
            "\"http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json\"")
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
            // APK. Editing app/src/main/assets/rules/ad_law_rules.json is the
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
}

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
    debugImplementation(libs.compose.ui.test.manifest)
}
