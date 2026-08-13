// app/build.gradle.kts — IceSpiritAI_Vision (shell scaffold).
//
// modelProfile is a Gradle property routed by `-PmodelProfile=<name>`.
// Default `shell` = no vision model is bundled. Future profiles
// (`ice_vision_minimal`, `ice_vision`) will gate model-loading code in
// IceSpiritVisionActivity.kt via build-time constants.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val modelProfile = providers.gradleProperty("modelProfile")
    .getOrElse("shell")

android {
    namespace = "com.icespiritai.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.icespiritai.vision"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Expose the active profile to runtime code as a BuildConfig field.
        // Reading code: BuildConfig.MODEL_PROFILE == "shell" | "ice_vision_minimal" | "ice_vision"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)

    testImplementation(libs.junit)
}