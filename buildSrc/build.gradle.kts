// buildSrc/ — Kotlin JVM library auto-loaded into every build script in
// this Gradle build. Use this module ONLY for build-time helpers that
// must be shared between `app/build.gradle.kts` and JVM unit tests
// (e.g. LatestJsonGenerator, ArchiveVision — Tasks 2, 3, 15).
//
// Anything in app/ runtime code MUST NOT depend on this module.

plugins {
    `kotlin-dsl`
}

repositories {
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}
