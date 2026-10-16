package com.icespiritai.offline.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [CacheCleaner].
 *
 * The cleaner takes a directory + a [CacheCleanupPolicy] (keep most-recent
 * N files OR files newer than N days, whichever is more permissive) and
 * deletes the files that fail BOTH gates. The contract verified below:
 *
 *  - "Keep N most recent" honours `lastModified()` ordering, not file name
 *    or insertion order (a sanity check on the file-system read path).
 *  - "Keep files newer than N days" is a wall-clock comparison against
 *    the injected clock (no `System.currentTimeMillis()` baked in).
 *  - Sub-directories are NOT recursed — capture/, evidence/ etc are flat
 *    buckets; nested dirs are an app-side invariant.
 *  - A non-existent directory is a no-op (returns zeros), NOT an error:
 *    `cacheDir/capture` may not exist on a fresh install and the cold-
 *    start cleanup must not throw.
 *  - `bytesFreed` is the sum of pre-delete `File.length()` of every
 *    successful delete (a zero-byte file contributes 0; a delete that
 *    fails contributes its size).
 */
class CacheCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Fixed clock: "now" = 2026-10-16 12:00:00 UTC. */
    private val fixedNow: Long = 1_766_223_600_000L
    private val oneDayMs = 24L * 60L * 60L * 1000L
    private val cleaner = CacheCleaner(clock = { fixedNow })

    private fun newFile(name: String, ageDays: Int, sizeBytes: Int = 0): File {
        val file = File(tempFolder.root, name)
        file.writeBytes(ByteArray(sizeBytes))
        val mtime = fixedNow - ageDays * oneDayMs
        assertTrue("setLastModified failed for $name", file.setLastModified(mtime))
        return file
    }

    @Test
    fun `keeps top N most recent files by lastModified when age window is wide`() {
        // 5 files, all within age window. Policy = keep top 3.
        newFile("a.jpg", ageDays = 1)
        newFile("b.jpg", ageDays = 2)
        newFile("c.jpg", ageDays = 3)
        newFile("d.jpg", ageDays = 4)
        newFile("e.jpg", ageDays = 5)

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 3, keepNewerThanDays = 30),
        )

        assertEquals(2, result.totalDeleted)
        assertTrue("a.jpg should survive (most recent)", File(tempFolder.root, "a.jpg").exists())
        assertTrue("b.jpg should survive", File(tempFolder.root, "b.jpg").exists())
        assertTrue("c.jpg should survive", File(tempFolder.root, "c.jpg").exists())
        assertTrue("d.jpg should be deleted", !File(tempFolder.root, "d.jpg").exists())
        assertTrue("e.jpg should be deleted", !File(tempFolder.root, "e.jpg").exists())
    }

    @Test
    fun `count limit applies even to fresh files within age window`() {
        // 10 files all dated "now" — all within age window.
        // Policy = keep top 3 by count, keep 7 days by age.
        // Combined (AND): file must be in top 3 AND within age. Count
        // limit caps at 3 — the 4th..10th freshest files are deleted even
        // though the age window would otherwise cover them.
        repeat(10) { i -> newFile("f$i.jpg", ageDays = 0) }

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 3, keepNewerThanDays = 7),
        )

        assertEquals(7, result.totalDeleted)
        assertEquals(3, tempFolder.root.listFiles()!!.size)
    }

    @Test
    fun `files outside age window are deleted even if not over count limit`() {
        // 3 files, 10 days old each. Policy = keep top 10 by count, but only
        // keep files newer than 3 days. Count saves them; age deletes them.
        newFile("old1.zip", ageDays = 10)
        newFile("old2.zip", ageDays = 10)
        newFile("old3.zip", ageDays = 10)

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 10, keepNewerThanDays = 3),
        )

        assertEquals(3, result.totalDeleted)
        assertEquals(0, result.bytesFreed)
    }

    @Test
    fun `non-existent directory is a no-op not an exception`() {
        val ghost = File(tempFolder.root, "does-not-exist")

        val result = cleaner.cleanup(
            ghost,
            CacheCleanupPolicy(keepMostRecent = 10, keepNewerThanDays = 30),
        )

        assertEquals(0, result.totalDeleted)
        assertEquals(0, result.bytesFreed)
    }

    @Test
    fun `subdirectories are never recursed into or deleted`() {
        // Top-level files: 2 of them, all old. Policy deletes both.
        // Nested subdir with file: must NOT touch it.
        newFile("top1.jpg", ageDays = 100)
        newFile("top2.jpg", ageDays = 100)
        val subdir = File(tempFolder.root, "nested").apply { mkdirs() }
        val nestedFile = File(subdir, "kept.jpg").apply {
            writeBytes(ByteArray(10))
            setLastModified(fixedNow - 100 * oneDayMs)
        }

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 1, keepNewerThanDays = 30),
        )

        assertEquals(2, result.totalDeleted)
        assertTrue("subdir survived", subdir.exists())
        assertTrue("nested file untouched", nestedFile.exists())
    }

    @Test
    fun `bytesFreed sums lengths of deleted files`() {
        newFile("a.jpg", ageDays = 50, sizeBytes = 100)
        newFile("b.jpg", ageDays = 50, sizeBytes = 250)
        newFile("c.jpg", ageDays = 1) // kept: top 1 AND within 7 days
        newFile("d.jpg", ageDays = 50, sizeBytes = 50)

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 1, keepNewerThanDays = 7),
        )

        assertEquals(3, result.totalDeleted)
        assertEquals(400L, result.bytesFreed)
    }

    @Test
    fun `most recent files protected by count even if older than age cutoff`() {
        // 5 files, decreasing recency: 1d, 10d, 20d, 30d, 40d.
        // Policy: keep top 2 by count, but files older than 15d should be
        // age-eligible for deletion. Combined logic: keep files that are
        // in top N OR newer than cutoff → c.jpg (20d) is older than 15d but
        // ranks 3rd, NOT in top 2, so age says delete and count says delete.
        // Result: a.jpg + b.jpg kept (in top 2); c, d, e all deleted.
        newFile("a.jpg", ageDays = 1)
        newFile("b.jpg", ageDays = 10)
        newFile("c.jpg", ageDays = 20)
        newFile("d.jpg", ageDays = 30)
        newFile("e.jpg", ageDays = 40)

        val result = cleaner.cleanup(
            tempFolder.root,
            CacheCleanupPolicy(keepMostRecent = 2, keepNewerThanDays = 15),
        )

        assertEquals(3, result.totalDeleted)
        assertTrue("a.jpg kept", File(tempFolder.root, "a.jpg").exists())
        assertTrue("b.jpg kept", File(tempFolder.root, "b.jpg").exists())
        assertTrue("c.jpg deleted", !File(tempFolder.root, "c.jpg").exists())
        assertTrue("d.jpg deleted", !File(tempFolder.root, "d.jpg").exists())
        assertTrue("e.jpg deleted", !File(tempFolder.root, "e.jpg").exists())
    }
}