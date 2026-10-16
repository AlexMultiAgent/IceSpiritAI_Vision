package com.icespiritai.offline.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [CacheCleanupCoordinator].
 *
 * The coordinator drives the four cacheDir sub-buckets (`capture`,
 * `evidence`, `wifi_probe`, `update`) through a single [CacheCleaner].
 * Each bucket has its own [CacheCleanupPolicy] (see
 * [DefaultCachePolicies]); the coordinator is responsible only for
 * looping them and summing the per-bucket results.
 *
 * Tests pin a fake clock on the underlying [CacheCleaner] so the
 * per-bucket policies produce deterministic outcomes.
 */
class CacheCleanupCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fixedNow: Long = 1_766_223_600_000L
    private val oneDayMs = 24L * 60L * 60L * 1000L

    private fun newFile(dir: File, name: String, ageDays: Int, sizeBytes: Int = 0): File {
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(ByteArray(sizeBytes))
        file.setLastModified(fixedNow - ageDays * oneDayMs)
        return file
    }

    @Test
    fun `cleanup iterates every known bucket`() {
        val root = tempFolder.root
        // Each bucket has files that fall within its policy → kept.
        // Verify the coordinator visits all four buckets (and their dirs
        // are recognised even if they don't exist yet).
        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        val summary = coordinator.cleanup(root)

        assertEquals(4, summary.perDir.size)
        assertTrue("capture bucket present", "capture" in summary.perDir)
        assertTrue("evidence bucket present", "evidence" in summary.perDir)
        assertTrue("wifi_probe bucket present", "wifi_probe" in summary.perDir)
        assertTrue("update bucket present", "update" in summary.perDir)
        assertEquals(0, summary.totalDeleted)
    }

    @Test
    fun `cleanup deletes files that violate any bucket policy`() {
        val root = tempFolder.root
        // capture policy: keep 50 / 14 days. Put 5 files 100 days old.
        // evidence policy: keep 10 / 60 days. Put 3 files 100 days old.
        // wifi_probe policy: keep 10 / 14 days. Put 4 files 100 days old.
        // update policy: keep 2 / 60 days. Put 4 files 100 days old.
        repeat(5) { i -> newFile(File(root, "capture"), "c$i.jpg", ageDays = 100) }
        repeat(3) { i -> newFile(File(root, "evidence"), "e$i.zip", ageDays = 100) }
        repeat(4) { i -> newFile(File(root, "wifi_probe"), "w$i.txt", ageDays = 100) }
        repeat(4) { i -> newFile(File(root, "update"), "u$i.apk", ageDays = 100) }

        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        val summary = coordinator.cleanup(root)

        // Every bucket's files are 100d old → fail every age window.
        // Count limit alone wouldn't delete them (5 ≤ 50, 3 ≤ 10, 4 ≤ 10,
        // 4 > 2 but AND-combined with age fails all 4), so age drives the
        // deletion in all four buckets.
        assertEquals(5, summary.perDir["capture"]!!.totalDeleted)
        assertEquals(3, summary.perDir["evidence"]!!.totalDeleted)
        assertEquals(4, summary.perDir["wifi_probe"]!!.totalDeleted)
        assertEquals(4, summary.perDir["update"]!!.totalDeleted)
        assertEquals(16, summary.totalDeleted)
    }

    @Test
    fun `per-bucket counts are independent of each other`() {
        val root = tempFolder.root
        // capture: 2 fresh files (within age, below count) → kept
        // evidence: 5 files 100 days old → all outside 60-day window → deleted
        newFile(File(root, "capture"), "c1.jpg", ageDays = 1)
        newFile(File(root, "capture"), "c2.jpg", ageDays = 2)
        repeat(5) { i -> newFile(File(root, "evidence"), "e$i.zip", ageDays = 100) }

        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        val summary = coordinator.cleanup(root)

        assertEquals(0, summary.perDir["capture"]!!.totalDeleted)
        assertEquals(5, summary.perDir["evidence"]!!.totalDeleted)
    }

    @Test
    fun `missing buckets are reported with zero counts not an exception`() {
        val root = tempFolder.root
        // Root has nothing. Each bucket must NOT exist; coordinator
        // must still return a perDir entry with zeros (no exception).
        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        val summary = coordinator.cleanup(root)

        assertEquals(0, summary.totalDeleted)
        assertEquals(0L, summary.totalBytesFreed)
        for (result in summary.perDir.values) {
            assertEquals(0, result.totalDeleted)
            assertEquals(0L, result.bytesFreed)
        }
    }

    @Test
    fun `bytesFreed totals across buckets`() {
        val root = tempFolder.root
        newFile(File(root, "capture"), "c1.jpg", ageDays = 100, sizeBytes = 100)
        newFile(File(root, "evidence"), "e1.zip", ageDays = 100, sizeBytes = 500)

        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        val summary = coordinator.cleanup(root)

        assertEquals(600L, summary.totalBytesFreed)
    }

    @Test
    fun `lastCleanupResult reflects most recent run`() {
        val root = tempFolder.root
        val coordinator = CacheCleanupCoordinator(CacheCleaner(clock = { fixedNow }))
        assertNull(coordinator.lastCleanupResult)

        coordinator.cleanup(root)
        val first = coordinator.lastCleanupResult
        assertNotNull(first)

        // A second run with no changes should be a no-op (zero deletes).
        coordinator.cleanup(root)
        val second = coordinator.lastCleanupResult
        assertNotNull(second)
        assertEquals(0, second!!.totalDeleted)
    }
}