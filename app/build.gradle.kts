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

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.icespiritai.vision"
        minSdk = 26
        targetSdk = 36
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

    packaging {
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

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Domain
    implementation(libs.hankcs.aho.corasick)

    // OCR engine: PaddleOCR official SDK + native runtime
    implementation(files("libs/ppocr-sdk.aar"))
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumentation tests (for SDK smoke test + Compose UI test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}