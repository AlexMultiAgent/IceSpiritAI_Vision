// app/build.gradle.kts — IceSpiritAI_Vision (forward path baseline).
//
// modelProfile is a Gradle property routed by `-PmodelProfile=<name>`.
// Default `shell` = no vision/OCR model is bundled. Future profiles
// (`ice_vision_minimal`, `ice_vision`, `ice_ocr_rules`) will gate
// model-loading code in IceSpiritVisionActivity.kt via build-time
// constants (BuildConfig.MODEL_PROFILE).

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val modelProfile = providers.gradleProperty("modelProfile")
    .getOrElse("shell")

apply(from = "prepare-ocr-rules.gradle.kts")

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            // APK. The source files remain in the repo as human-readable
            // references for editing rules — but editing them does NOT
            // change the bundled JSON; the Gradle constant in
            // app/prepare-ocr-rules.gradle.kts must be updated (until we
            // add a Sync task that mirrors source → constant).
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
    debugImplementation(libs.compose.ui.test.manifest)
}