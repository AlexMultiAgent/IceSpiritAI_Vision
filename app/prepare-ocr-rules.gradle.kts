// app/prepare-ocr-rules.gradle.kts — Generate per-modelProfile rules assets.
//
// shell           → ad_law_rules.json contains {"version":1,"rules":[]} (skeleton)
// ice_ocr_rules   → ad_law_rules.json contains the 10 golden rules
// ice_vision      → ad_law_rules.json contains {"version":1,"rules":[]} (Phase 2+)
//
// Source files at app/src/main/assets/rules/ are NEVER mutated.
// AGP picks up only from app/build/generated/assets/rules/ via a reconfigured
// assets sourceSet (see app/build.gradle.kts hook).

import org.gradle.api.tasks.Copy

val modelProfileValue = providers.gradleProperty("modelProfile").getOrElse("shell")

val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/rules")

// 10 golden rules JSON, inlined so build doesn't need to read it twice.
// Source of truth: app/src/main/assets/rules/ad_law_rules.json — when editing
// rules, edit BOTH this constant and the source file (until we add a Sync task).
val fullRulesJson = """
{
  "version": 1,
  "rules": [
    {
      "id": "medical_absolute",
      "category": "medical",
      "regulation": "《广告法》§16",
      "keywords": ["根治", "100% 有效", "彻底治愈", "无副作用"],
      "severity": "Violation"
    },
    {
      "id": "health_claim",
      "category": "medical",
      "regulation": "《广告法》§17",
      "keywords": ["疗效", "治愈率", "根治率"],
      "severity": "Violation"
    },
    {
      "id": "education_guarantee",
      "category": "education",
      "regulation": "《广告法》§24",
      "keywords": ["保过", "包过", "不过退款", "100% 通过"],
      "severity": "Violation"
    },
    {
      "id": "education_absolute",
      "category": "education",
      "regulation": "《广告法》§24",
      "keywords": ["最好", "最强师资", "第一"],
      "severity": "Warning"
    },
    {
      "id": "finance_promise",
      "category": "finance",
      "regulation": "《广告法》§25",
      "keywords": ["稳赚不赔", "无风险", "保本高收益"],
      "severity": "Violation"
    },
    {
      "id": "realestate_promise",
      "category": "realestate",
      "regulation": "《广告法》§26",
      "keywords": ["升值回报", "投资回报", "学区房包入学"],
      "severity": "Warning"
    },
    {
      "id": "absolute_top",
      "category": "absolute",
      "regulation": "《广告法》§9",
      "keywords": ["最佳", "最好", "第一", "顶级", "唯一"],
      "severity": "Warning"
    },
    {
      "id": "absolute_100",
      "category": "absolute",
      "regulation": "《广告法》§9",
      "keywords": ["100%", "百分百", "百分之百"],
      "severity": "Warning"
    },
    {
      "id": "tobacco_alcohol",
      "category": "restricted",
      "regulation": "《广告法》§22",
      "keywords": ["戒烟", "解酒"],
      "severity": "Info"
    },
    {
      "id": "minor_targeting",
      "category": "minor",
      "regulation": "《广告法》§28",
      "keywords": ["儿童专用", "宝宝必备"],
      "severity": "Info"
    }
  ]
}
""".trimIndent()

val slimRulesJson = """{"version":1,"rules":[]}"""

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
    outputs.dir(outDir)

    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        val target = java.io.File(dir, "ad_law_rules.json")
        val content = when (activeProfile) {
            "shell", "ice_vision" -> slimRulesJson
            "ice_ocr_rules" -> fullRulesJson
            else -> {
                logger.warn("[prepareOcrRulesAssets] Unknown modelProfile '$activeProfile' — defaulting to slim rules")
                slimRulesJson
            }
        }
        target.writeText(content)
        logger.lifecycle("[prepareOcrRulesAssets] profile=$activeProfile, wrote ${target.length()} bytes to ${target}")
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

// Wire into preBuild so both rules + models are populated before package.
tasks.named("preBuild").configure {
    dependsOn(prepareOcrRulesAssets)
    dependsOn(copyOcrModelsAssets)
}