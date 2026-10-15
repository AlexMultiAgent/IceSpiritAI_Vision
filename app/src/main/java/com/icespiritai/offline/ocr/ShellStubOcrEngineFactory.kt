package com.icespiritai.offline.ocr

import android.content.Context

/**
 * Runtime factory for the `shell` modelProfile. Registered into the
 * APK's `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory`
 * by `app/prepare-ocr-rules.gradle.kts::buildProfileServicesJar` when
 * `-PmodelProfile=shell` (the default).
 *
 * Lives in `src/main/` (not `src/shell/`) so CI can compile and test
 * the `ice_ocr_rules` profile without `src/shell/` ever entering the
 * classpath. The shell APK stays lightweight because this class is just
 * a thin wrapper around [ShellStubOcrEngine] — no model files, no
 * native libs.
 */
class ShellStubOcrEngineFactory : OcrEngineFactory {
    override fun create(context: Context): OcrEngine = ShellStubOcrEngine()
}
