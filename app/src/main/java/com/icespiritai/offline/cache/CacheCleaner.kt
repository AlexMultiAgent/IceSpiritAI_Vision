package com.icespiritai.offline.cache

import java.io.File

/**
 * Retention policy for [CacheCleaner]. A file is kept only if BOTH gates
 * pass:
 *
 *  - `keepMostRecent` — file is in the top N by `lastModified()` recency
 *  - `keepNewerThanDays` — file is newer than this wall-clock window
 *
 * Both gates together give "bounded LRU + TTL": a file beyond the count
 * cap AND past the age window is deleted; a file fresh within the age
 * window still loses to the count cap. Used to prevent the `[App]
 * cacheDir` (capture/, evidence/, wifi_probe/, update/) from accumulating
 * gigabytes over a year of inspection runs. Concrete values live in
 * [DefaultCachePolicies] and are tuned per-bucket.
 */
data class CacheCleanupPolicy(
    val keepMostRecent: Int,
    val keepNewerThanDays: Int,
)

/**
 * Result of one cleanup pass. `bytesFreed` is the sum of pre-delete
 * `File.length()` of every successfully deleted file. A zero-byte file
 * contributes 0; a delete that returns false does not contribute its
 * size (the on-disk file is still there).
 */
data class CacheCleanupResult(
    val totalDeleted: Int,
    val bytesFreed: Long,
)

/**
 * Pure file-pruning helper. Reads a directory's flat list of files,
 * decides which to keep under a [CacheCleanupPolicy], deletes the rest.
 *
 * No recursion: cacheDir/\<bucket\>/ is a flat bucket by convention.
 * Sub-directories are NEVER recursed into or deleted — call sites
 * enumerate the buckets explicitly (capture/, evidence/, etc.).
 *
 * No-op on a missing directory (returns zero counts) so the
 * `IceSpiritApplication.onCreate` cold-start hook can call
 * `cleanup(cacheDir.resolve("capture"), ...)` without first checking
 * whether the directory exists.
 *
 * The clock is injected so the tests pin "now" without relying on
 * `System.currentTimeMillis()` (which would otherwise re-evaluate on
 * every assertion and produce flaky results).
 */
class CacheCleaner(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun cleanup(dir: File, policy: CacheCleanupPolicy): CacheCleanupResult {
        if (!dir.exists() || !dir.isDirectory) return CacheCleanupResult(0, 0)
        val files = dir.listFiles()?.filter { it.isFile } ?: return CacheCleanupResult(0, 0)
        if (files.isEmpty()) return CacheCleanupResult(0, 0)
        val ageCutoffMs = clock() - policy.keepNewerThanDays * DAY_MS
        val sorted = files.sortedByDescending { it.lastModified() }
        val keep = sorted.withIndex()
            .filter { (idx, file) ->
                idx < policy.keepMostRecent && file.lastModified() >= ageCutoffMs
            }
            .map { it.value }
            .toSet()
        var deleted = 0
        var bytesFreed = 0L
        for (file in files) {
            if (file in keep) continue
            bytesFreed += file.length()
            if (file.delete()) deleted++
        }
        return CacheCleanupResult(deleted, bytesFreed)
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

/**
 * Per-bucket retention policies. Each cacheDir sub-directory has its own
 * volume + lifetime profile — capture/ fills quickly with small JPEGs,
 * evidence/ holds large exports that the user might want to keep longer
 * for follow-up review, wifi_probe/ is purely diagnostic, update/ holds
 * downloaded APKs that should be pruned aggressively after the user has
 * installed the latest version.
 *
 * Tuned to keep a year of inspection runs around 50–250 MB rather than
 * the multiple-GB pile-up observed without this policy.
 */
object DefaultCachePolicies {
    /**
     * Glasses-captured JPEGs (`GlassesPhotoCaptureRepository`). 640×480
     * photos, ~30–80 KB each — count-based limit matters more than age
     * (a busy day may produce 30+ photos but they're each tiny).
     */
    val capture: CacheCleanupPolicy = CacheCleanupPolicy(
        keepMostRecent = 50,
        keepNewerThanDays = 14,
    )

    /**
     * Exported evidence ZIPs (`ExportAction`). Larger (often 1–10 MB
     * each, with the JPEG + rules JSON bundled) but written
     * infrequently — a 60-day window covers the typical follow-up
     * review cadence.
     */
    val evidence: CacheCleanupPolicy = CacheCleanupPolicy(
        keepMostRecent = 10,
        keepNewerThanDays = 60,
    )

    /**
     * Wi-Fi probe diagnostic files (`GlassesWifiProbe`). Rarely
     * interesting after the run completes; kept for a fortnight in case
     * the user wants to compare two probes back-to-back.
     */
    val wifiProbe: CacheCleanupPolicy = CacheCleanupPolicy(
        keepMostRecent = 10,
        keepNewerThanDays = 14,
    )

    /**
     * Downloaded APKs (`UpdateDownloadService`). The most recent APK is
     * usually still needed for `READY_TO_INSTALL` → install → prune. The
     * `DownloadStateStore` keeps the metadata around for the active
     * download, so deleting an older APK whose state is `ReadyToInstall`
     * is safe (it would have been installed or the user already moved
     * on). Aggressive on count to avoid a 75 MB × N pile-up if the
     * user has been deferring installs.
     */
    val update: CacheCleanupPolicy = CacheCleanupPolicy(
        keepMostRecent = 2,
        keepNewerThanDays = 60,
    )
}