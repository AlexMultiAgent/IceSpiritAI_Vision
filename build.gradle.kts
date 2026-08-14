// Root build script — IceSpiritAI_Vision.
//
// Convention: keep this file empty of project-level configuration. All
// plugin versions are pinned in `gradle/libs.versions.toml` and applied
// via `apply false` here, then `apply true` in the module build scripts.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}