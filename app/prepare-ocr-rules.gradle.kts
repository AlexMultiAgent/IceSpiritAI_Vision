// app/prepare-ocr-rules.gradle.kts — Generate per-modelProfile rules assets.
//
// Both rule domains (ad_signage + food_label) are staged for every profile so
// the runtime loader can hydrate either domain regardless of the active model.
// shell           → ad_signage_rules.json + food_label_rules.json = {"version":1,"rules":[]} (skeleton)
// ice_ocr_rules   → ad_signage_rules.json (129 rules / v9) + food_label_rules.json (66 rules / v4)
// ice_vision      → same as shell (Phase 2+)
//
// Counts above MUST match the committed source-of-truth JSON in
// app/src/main/assets/rules/. Bump them when shipping a rule audit. A drift
// here is a documentation-only bug (no runtime impact) but trips up future
// audit readers — see the ad_signage_v9 audit in commit 8b20e98.
// Source files at app/src/main/assets/rules/ are NEVER mutated.
// AGP picks up only from app/build/generated/assets/rules/ via a reconfigured
// assets sourceSet (see app/build.gradle.kts hook).

import org.gradle.api.tasks.Copy

val modelProfileValue = providers.gradleProperty("modelProfile").getOrElse("shell")

val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/rules")

// The committed JSON under src/main/assets/rules/ is the single source of
// truth for the shipped rule set. Reading it at execution time (instead of
// maintaining a duplicated inlined copy) removes the "edit both places" drift
// hazard. The files are declared as task inputs below so edits invalidate the
// generated asset.
val fullAdSignageRulesFile = file("src/main/assets/rules/ad_signage_rules.json")
val fullFoodLabelRulesFile = file("src/main/assets/rules/food_label_rules.json")

val slimRulesJson = """{"version":1,"rules":[]}"""

data class DomainRules(val sourceFile: java.io.File, val stagedName: String)

val domainRules: List<DomainRules> = listOf(
    DomainRules(fullAdSignageRulesFile, "ad_signage_rules.json"),
    DomainRules(fullFoodLabelRulesFile, "food_label_rules.json"),
)

val prepareOcrRulesAssets = tasks.register("prepareOcrRulesAssets") {
    group = "build"
    description = "Generate rules assets into build/generated/assets/rules/ based on modelProfile"

    val outDir = generatedAssetsDir
    val activeProfile = modelProfileValue

    // Declare the active profile as an @Input so changing `-PmodelProfile=...`
    // invalidates the task cache. Without this, Gradle would treat the task as
    // UP-TO-DATE whenever the output dir already exists from a previous run,
    // regardless of the profile that produced it.
    inputs.property("modelProfile", activeProfile)
    domainRules.forEach { inputs.file(it.sourceFile) }
    outputs.dir(outDir)

    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        domainRules.forEach { dr ->
            val target = java.io.File(dir, dr.stagedName)
            val content = when (activeProfile) {
                "shell", "ice_vision" -> slimRulesJson
                "ice_ocr_rules" -> {
                    if (!dr.sourceFile.isFile) {
                        throw GradleException(
                            "Missing rules source file ${dr.sourceFile.absolutePath} " +
                                "required by modelProfile=ice_ocr_rules"
                        )
                    }
                    dr.sourceFile.readText(Charsets.UTF_8)
                }
                else -> {
                    logger.warn("[prepareOcrRulesAssets] Unknown modelProfile '$activeProfile' — defaulting to slim rules")
                    slimRulesJson
                }
            }
            target.writeText(content)
            logger.lifecycle(
                "[prepareOcrRulesAssets] profile=$activeProfile, wrote ${target.length()} bytes to ${target}"
            )
        }

        // Build-time assertion: every domain's staged rules file must exist.
        // Catches packaging defects (e.g. a future contributor adding a new
        // domainRules entry but forgetting to wire it into the doLast block).
        val missing = domainRules
            .map { java.io.File(dir, it.stagedName) }
            .filterNot { it.isFile }
        check(missing.isEmpty()) {
            "[prepareOcrRulesAssets] build/generate/assets/rules/ missing required rules files: " +
                missing.joinToString { it.name }
        }
    }
}

// ----- OCR model staging -------------------------------------------------
//
// For `ice_ocr_rules` profile only: mirror app/src/main/assets/models/ into
// build/generated/assets/models/ so AGP picks the ONNX + YAML files up.
//
// Rationale: app/build.gradle.kts sets `assets.srcDirs` to ONLY
// build/generated/assets/ (the rules JSON lives there). Without this Copy task
// the APK would ship without models/det|inference.onnx, etc. — and any
// `recognize()` call would throw `OCRError.ModelNotFound`.
//
// `shell` / `ice_vision` profiles SKIP the copy (kept via `onlyIf`) so the
// shell APK stays small and matches its "no model bundled" intent.
//
// The .gitignore rule `app/src/main/assets/models/**/*.onnx` means the source
// dir is empty (or contains only .gitkeep) unless
// `tools/download-ppocr-models.sh` has been run; Copy with empty `from` is a
// harmless no-op so first-build with ice_ocr_rules still succeeds.

val copyOcrModelsAssets = tasks.register<Copy>("copyOcrModelsAssets") {
    group = "build"
    description = "Copy ONNX model assets into build/generated/assets/models/ when modelProfile == ice_ocr_rules"

    val activeProfile = modelProfileValue
    val modelSrcDir = file("src/main/assets/models")
    val outDir = layout.buildDirectory.dir("generated/assets/models")

    // Cache invalidation: switching -PmodelProfile must re-evaluate even if the
    // source files haven't changed. Without this, a "shell" build (which
    // skipped this task) might still be cached as UP-TO-DATE when switching to
    // "ice_ocr_rules" — leading to a missing-models APK.
    inputs.property("modelProfile", activeProfile)
    outputs.dir(outDir)

    // Skip entirely when profile != ice_ocr_rules.
    onlyIf { activeProfile == "ice_ocr_rules" }

    from(modelSrcDir) {
        include("**/*.onnx")
        include("**/*.yml")
        // Also drop the .gitkeep placeholder so it doesn't leak into the APK.
        exclude("**/.gitkeep")
    }
    into(outDir)

    doLast {
        val dir = outDir.get().asFile
        val fileCount = if (dir.exists()) dir.walkTopDown().count { it.isFile } else 0
        logger.lifecycle(
            "[copyOcrModelsAssets] profile=$activeProfile copied $fileCount model files to $dir"
        )
    }
}

// ----- Profile META-INF/services JAR -------------------------------------
//
// AGP 9.x's resource merge only picks up files under known resource
// qualifiers (`values/`, `drawable/`, `layout/`, ...) from custom `res`
// source dirs; arbitrary `META-INF/services/...` files added via
// `res.directories.add(...)` are silently dropped. And `assets/` ships
// every file under `assets/META-INF/services/...` — which is NOT scanned
// by ServiceLoader on Android (the classloader enumerates the APK's
// `META-INF/services/` root, not `assets/META-INF/services/`).
//
// The reliable way to bundle a ServiceLoader registration into an Android
// APK is therefore to ship the file inside a JAR and add it as a runtime
// dependency: AGP's `processJavaResources` task merges the JAR's
// `META-INF/` into the APK's root `META-INF/`, which IS where ServiceLoader
// looks.
//
// We generate one tiny JAR per build containing only the active profile's
// `META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory` file.
// `app/build.gradle.kts` adds it as a `runtimeOnly files(...)` dependency
// gated on the active profile.
//
// `shell` is the default profile: any unknown `-PmodelProfile` value falls
// through to it so the build still produces a working APK.

data class ProfileServices(val profile: String, val factoryFqn: String)

val profileServices: ProfileServices = when (modelProfileValue) {
    "ice_ocr_rules" -> ProfileServices("ice_ocr_rules", "com.icespiritai.offline.ocr.PaddleOcrEngineFactory")
    else -> ProfileServices("shell", "com.icespiritai.offline.ocr.FakeOcrEngineFactory")
}

val servicesJarDir = layout.buildDirectory.dir("generated/services-jar")

val buildProfileServicesJar = tasks.register("buildProfileServicesJar") {
    group = "build"
    description = "Build a JAR containing the active profile's META-INF/services/OcrEngineFactory registration"

    val outDir = servicesJarDir
    val active = profileServices
    val srcDir = file("src/${active.profile}/resources")

    inputs.property("modelProfile", active.profile)
    inputs.dir(srcDir).withPropertyName("srcDir")
    outputs.dir(outDir)

    doLast {
        val dst = outDir.get().asFile
        dst.mkdirs()

        // Write the service file directly. We don't need to consult the
        // source dir at runtime because each profile's services file is
        // single-line and contains exactly one FQN — we know it from
        // `profileServices.factoryFqn` above, derived from the active
        // profile. This keeps the task deterministic without relying on
        // `src/<profile>/resources/META-INF/services/...` existing on disk
        // (which is committed to the repo for readability but isn't the
        // authoritative source).
        val services = java.io.File(dst, "META-INF/services")
        services.mkdirs()
        java.io.File(services, "com.icespiritai.offline.ocr.OcrEngineFactory")
            .writeText("${active.factoryFqn}\n")

        // Bundle into a JAR. The JAR layout puts META-INF/services/... at
        // the JAR root, which is what `processJavaResources` extracts.
        val jarFile = java.io.File(dst, "ocr-engine-services.jar")
        java.util.jar.JarOutputStream(java.io.FileOutputStream(jarFile)).use { jos ->
            val entry = java.util.jar.JarEntry("META-INF/services/com.icespiritai.offline.ocr.OcrEngineFactory")
            jos.putNextEntry(entry)
            jos.write("${active.factoryFqn}\n".toByteArray())
            jos.closeEntry()
        }

        logger.lifecycle(
            "[buildProfileServicesJar] profile=${active.profile} -> ${jarFile.absolutePath}"
        )
    }
}

// ----- User-facing changelog copy ----------------------------------------
//
// app/src/main/assets/user-changelog.md is the source of truth (git-tracked,
// human-edited). app/build.gradle.kts restricts `assets.setSrcDirs` to ONLY
// build/generated/assets/ so the rules JSON + models can be swapped per
// modelProfile without rebuilding on rules-file edits. Without this Copy
// task the in-app changelog screen would see no asset.
//
// The mirror lives at build/generated/assets/user-changelog.md and is read
// at runtime by ChangelogScreen via context.assets.open("user-changelog.md").
//
// generateVisionLatestJson in build.gradle.kts also reads the SOURCE file
// (not the generated one) to avoid a chicken-and-egg dependency between the
// assembleRelease pipeline and the staging task.

val copyUserChangelogAsset = tasks.register("copyUserChangelogAsset") {
    group = "build"
    description = "Mirror app/src/main/assets/user-changelog.md into build/generated/assets/."

    val src = file("src/main/assets/user-changelog.md")
    val outFile = layout.buildDirectory.file("generated/assets/user-changelog.md")

    inputs.file(src)
    outputs.file(outFile)

    doLast {
        require(src.isFile) {
            "copyUserChangelogAsset: missing source ${src.absolutePath}"
        }
        val dst = outFile.get().asFile
        dst.parentFile.mkdirs()
        src.copyTo(dst, overwrite = true)
        logger.lifecycle(
            "[copyUserChangelogAsset] copied ${src.length()} bytes -> ${dst}"
        )
    }
}

// Wire into preBuild so the JAR exists before package / processJavaResources.
tasks.named("preBuild").configure {
    dependsOn(prepareOcrRulesAssets)
    dependsOn(copyOcrModelsAssets)
    dependsOn(copyUserChangelogAsset)
    dependsOn(buildProfileServicesJar)
}
