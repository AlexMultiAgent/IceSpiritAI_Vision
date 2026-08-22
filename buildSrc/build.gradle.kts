// buildSrc/ — Kotlin JVM library auto-loaded into every build script in
// this Gradle build. Use this module ONLY for build-time helpers that
// must be shared between `app/build.gradle.kts` and JVM unit tests
// (e.g. LatestJsonGenerator, ArchiveVision — Tasks 2, 3, 15).
//
// Anything in app/ runtime code MUST NOT depend on this module.

plugins {
    `kotlin-dsl`
    // LatestJsonGeneratorTest (Task 2) pins the JSON shape via a local
    // @Serializable mirror of AppVersionInfo. The annotation needs the
    // kotlin-serialization compiler plugin + kotlinx-serialization-json
    // runtime. Kept testImplementation only — production build scripts
    // that consume LatestJsonGenerator do not touch the serialization API.
    // Version MUST match Gradle's embedded Kotlin (2.4.0 for Gradle 9.7.0):
    // using a different version trips the "kotlin-dsl + different Kotlin
    // version" advisory warning. The app module still uses 2.4.10 via the
    // version catalog; this only affects buildSrc's own compilation.
    kotlin("plugin.serialization") version "2.4.0"
}

repositories {
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

kotlin {
    jvmToolchain(17)
}
