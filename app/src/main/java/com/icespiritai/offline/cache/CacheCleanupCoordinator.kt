package com.icespiritai.offline.cache

import java.io.File

/**
 * Per-bucket cleanup summary. Stored as [CacheCleanupCoordinator.lastCleanupResult]
 * so the Settings UI can show "上次清理: 删除 N 个, 释放 X MB" without
 * re-running cleanup on every screen entry.
 */
data class CacheCleanupSummary(
    val perDir: Map<String, CacheCleanupResult>,
    val totalDeleted: Int,
    val totalBytesFreed: Long,
) {
    /** Convenience for the UI's "上次清理" footer. */
    fun humanBytesFreed(): String = when {
        totalBytesFreed <= 0 -> "0 B"
        totalBytesFreed >= 1024 * 1024 ->
            "%.1f MB".format(totalBytesFreed / 1024.0 / 1024.0)
        totalBytesFreed >= 1024 ->
            "%.0f KB".format(totalBytesFreed / 1024.0)
        else -> "$totalBytesFreed B"
    }
}

/**
 * Orchestrates the per-bucket cache cleanup driven by [IceSpiritApplication.onCreate]
 * (cold start) and by `SettingsViewModel.runCacheCleanupNow` (manual
 * button). Owns a [CacheCleaner] instance and a hard-coded
 * `(bucket, policy)` table sourced from [DefaultCachePolicies].
 *
 * Two entry points, both applying each bucket's full policy
 * (count + age AND):
 *
 *  - [cleanup] — cold-start sweep. Triggered from
 *    [IceSpiritApplication.onCreate].
 *  - [cleanupImmediate] — user-driven sweep. Triggered from
 *    `SettingsViewModel.runCacheCleanupNow` when the user taps the
 *    Settings → 缓存清理 card's "立即清理" button.
 *
 * The two methods deliberately share the same policy today. Splitting
 * them at the API boundary leaves room for a future manual-tap variant
 * to use a stricter (count-only) policy without rewiring the caller —
 * a divergence that the equivalence unit test will surface in CI.
 *
 * `lastCleanupResult` is set on every successful run (either method)
 * and is the source of truth for the Settings card's footer text — UI
 * does not re-derive anything from disk.
 *
 * Threading: both methods do file IO and may block briefly on a slow
 * flash; callers (`IceSpiritApplication.onCreate`,
 * `SettingsViewModel.runCacheCleanupNow`) must dispatch onto
 * `Dispatchers.IO`.
 */
class CacheCleanupCoordinator(
    private val cleaner: CacheCleaner = CacheCleaner(),
) {
    @Volatile
    var lastCleanupResult: CacheCleanupSummary? = null
        private set

    /**
     * Cold-start sweep. Each bucket's full policy applies — a file is
     * kept iff it's within the count cap AND the age window.
     */
    fun cleanup(rootDir: File): CacheCleanupSummary = runPolicy(rootDir)

    /**
     * User-driven sweep from the "立即清理" button. Same policy as
     * [cleanup] today; the separate entry point is a deliberate seam
     * for future divergence (e.g. a stricter "always show progress"
     * variant). Pinning their equivalence in
     * `CacheCleanupCoordinatorTest` means a future split lands here in
     * CI rather than silently in production.
     */
    fun cleanupImmediate(rootDir: File): CacheCleanupSummary = cleanup(rootDir)

    private fun runPolicy(rootDir: File): CacheCleanupSummary {
        val perDir = LinkedHashMap<String, CacheCleanupResult>()
        var totalDeleted = 0
        var totalBytesFreed = 0L
        for ((bucket, policy) in BUCKETS) {
            val dir = File(rootDir, bucket)
            val result = cleaner.cleanup(dir, policy)
            perDir[bucket] = result
            totalDeleted += result.totalDeleted
            totalBytesFreed += result.bytesFreed
        }
        val summary = CacheCleanupSummary(perDir, totalDeleted, totalBytesFreed)
        lastCleanupResult = summary
        return summary
    }

    private companion object {
        /**
         * `bucket -> policy` table. Order matches the Settings card's
         * `保留目录` description (capture / evidence / wifi_probe / update
         * / region). LinkedHashMap so the perDir summary walks the buckets
         * in a stable, human-friendly order in logcat / UI.
         */
        val BUCKETS: Map<String, CacheCleanupPolicy> = linkedMapOf(
            "capture" to DefaultCachePolicies.capture,
            "evidence" to DefaultCachePolicies.evidence,
            "wifi_probe" to DefaultCachePolicies.wifiProbe,
            "update" to DefaultCachePolicies.update,
            "region" to DefaultCachePolicies.region,
        )
    }
}