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
