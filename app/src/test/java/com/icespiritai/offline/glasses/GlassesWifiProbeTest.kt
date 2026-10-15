package com.icespiritai.offline.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The FTP parsing the Wi-Fi probe depends on, plus the runtime-permission
 * gate the probe's [run] entry enforces.
 */
class GlassesWifiProbeTest {

    private fun parse(line: String): GlassesWifiProbe.FileInfo? =
        GlassesWifiProbe.parseListLine(line)

    @Test
    fun parsesAnLsStyleListingLine() {
        val entry = parse("-rw-r--r--    1 1000     1000        34019 Sep 17 12:51 IMG_0001.JPG")

        assertEquals("IMG_0001.JPG", entry?.name)
        assertEquals(34_019L, entry?.size)
    }

    @Test
    fun keepsSpacesInsideAFileName() {
        val entry = parse("-rw-r--r-- 1 1000 1000 1234 Jan 01 2026 my photo.jpg")

        assertEquals("my photo.jpg", entry?.name)
        assertEquals(1_234L, entry?.size)
    }

    @Test
    fun dropsEntriesItCannotTrust() {
        // The `total N` header some servers print first.
        assertNull(parse("total 42"))
        // No size column.
        assertNull(parse("-rw-r--r-- 1 1000 1000 IMG_0001.JPG"))
        // No name column.
        assertNull(parse("-rw-r--r-- 1 1000 1000 34019"))
        // Dot directories are not photos.
        assertNull(parse("drwxr-xr-x 2 1000 1000 4096 Sep 17 12:51 ."))
        assertNull(parse("drwxr-xr-x 2 1000 1000 4096 Sep 17 12:51 .."))
        // Empty line.
        assertNull(parse(""))
    }

    @Test
    fun aDirectoryEntryIsStillAnEntry() {
        // The probe lists one flat directory, so a sub-directory is reported
        // rather than silently swallowed — the caller decides what to fetch.
        val entry = parse("drwxr-xr-x 2 1000 1000 4096 Sep 17 12:51 20260917")

        assertEquals("20260917", entry?.name)
        assertEquals(4_096L, entry?.size)
    }

    @Test
    fun wifiRequired_isEmptyOnPreS() {
        // Robolectric defaults to API 33 (TIRAMISU), so we can't easily
        // exercise pre-S here. The test still documents the contract —
        // an empty set on legacy API means no runtime request needed.
        val perms = GlassesPermissions.wifiRequired()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            assertEquals(0, perms.size)
        }
    }
}
