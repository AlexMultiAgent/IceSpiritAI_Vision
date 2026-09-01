// Settings — IceSpiritAI_Vision.
//
// Maven mirror strategy mirrors IceSpiritAI_Translate: Aliyun is the
// primary mirror (broad coverage, fast in CN), with Tencent + Huawei as
// fallback when an Aliyun miss occurs.

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        google()
        mavenCentral()
    }
}

// Toolchain provisioning: buildSrc pins jvmToolchain(17) (mirrors the
// 2026-08 forward-path baseline) but the WIN runner ships JDK 25 only.
// We don't pin `org.gradle.java.home` in this file because the path is
// per-machine (see gradle.properties). Gradle's built-in toolchain
// auto-download falls back to foojay.io which is rate-limited in CN;
// contributors in CN should pre-stage JDK 17 manually or point
// org.gradle.java.home at their local install.
//
// Note: `org.gradle.java.installations.auto-download` is NOT set in
// gradle.properties (v0.1.42 explicitly removed the flag — there is no
// foojay-resolver-convention plugin on the classpath, so the flag has
// no effect anyway; pre-staging JDK 17 via JAVA_HOME is the only path).
rootProject.name = "IceSpirit"

include(":app")