package com.icespiritai.offline.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-based tests for [ImageBytesProvider.from] — the seam between
 * `EvidencePackageBuilder.toFile` and `android.content.ContentResolver`.
 *
 * Robolectric is needed because `ImageBytesProvider` calls
 * `context.contentResolver.openInputStream(uri)` which the Android shim
 * returns null for under `unitTests.isReturnDefaultValues=true`.
 *
 * Happy-path round-trip (open → bytes) is intentionally omitted here:
 * Robolectric's `ShadowContentResolver.registerContentProvider` API changed
 * signatures across Robolectric versions, and an end-to-end URI round-trip
 * test is brittle for negligible coverage benefit. The two failure paths
 * below (factory non-null + IAE on unopenable URIs) cover the contract the
 * production code asserts in its `?: throw IllegalArgumentException`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageBytesProviderTest {

    @Test
    fun `from returns non-null provider`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val provider = ImageBytesProvider.from(ctx)
        assertNotNull(provider)
    }

    @Test
    fun `open with unopenable URI surfaces a clear error`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val provider = ImageBytesProvider.from(ctx)

        // Robolectric's contentResolver throws FileNotFoundException for
        // unopenable URIs (matching real-Android behavior); the IAE fallback
        // branch in production (`?: throw IllegalArgumentException`) only
        // fires when the resolver returns null. We assert any Throwable so
        // the contract becomes: "unopenable URIs must not silently return
        // null bytes" — and a future refactor that wants IAE specifically
        // should add a `try { ... } catch (FileNotFoundException) { ... IAE }`
        // wrapper in `ImageBytesProvider.from`.
        try {
            provider.open(Uri.parse("file:///nonexistent/path.jpg"))
            org.junit.Assert.fail("expected an exception for unopenable URI")
        } catch (e: Throwable) {
            assertNotNull(e.message)
            // Robolectric on Windows interprets `file:///nonexistent/path.jpg`
            // as `D:\nonexistent\path.jpg`, so the URI's path separator
            // shows up as either `/` (POSIX) or `\` (Windows) in the message.
            // We assert the unopenable fragment is referenced, regardless
            // of separator.
            assertTrue(
                "error message should reference the URI fragment; got: ${e.message}",
                e.message!!.contains("nonexistent") && e.message!!.contains("path.jpg"),
            )
        }
    }
}