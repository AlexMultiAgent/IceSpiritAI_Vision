# Add project specific ProGuard rules here.
# Empty for the initial shell scaffold — no native libs / no reflection-heavy
# dependencies are bundled yet. Populate as model profiles are added.

# Keep ServiceLoader-registered OcrEngineFactory implementations. The build
# puts META-INF/services/... into a runtimeOnly jar; ServiceLoader scans that
# jar by FQN. R8 must not rename these classes.
-keep class * implements com.icespiritai.offline.ocr.OcrEngineFactory { *; }
-keep class com.icespiritai.offline.ocr.FakeOcrEngineFactory { *; }
-keep class com.icespiritai.offline.ocr.PaddleOcrEngineFactory { *; }

# Keep kotlinx.serialization metadata for AppVersionInfo (signerCertSha256 field
# is decoded from JSON without an explicit KSerializer lookup; the auto-generated
# $serializer is referenced via Companion.get serializer()).
-keepclassmembers class com.icespiritai.offline.updater.AppVersionInfo {
    *** Companion;
}
-keepclasseswithmembers class com.icespiritai.offline.updater.AppVersionInfo {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep kotlinx.serialization metadata for the @Serializable rule classes loaded
# from `assets/rules/*.json` at runtime. Without these rules R8 strips the
# auto-generated `$serializer` companions and `serializer()` lookup methods,
# and the first AdSignageRuleLoader / FoodLabelRuleLoader.decode() call throws
# `SerializationException: Serializer for class '...' is not found`. Pattern
# below mirrors the official kotlinx.serialization R8 keep rules (see
# https://github.com/Kotlin/kotlinx.serialization#android -- "R8/ProGuard
# configuration" section).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.icespiritai.offline.rules.** {
    *** Companion;
    static <1>$Companion Companion;
}
-keepclasseswithmembers class com.icespiritai.offline.rules.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.icespiritai.offline.rules.**
-keep class <1>$$serializer { *; }
