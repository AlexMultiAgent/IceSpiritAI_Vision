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
 * `lastCleanupResult` is set on every successful run and is the source
 * of truth for the Settings card's footer text — UI does not re-derive
 * anything from disk.
 *
 * Threading: `cleanup()` does file IO and may block briefly on a slow
 * flash; callers (Application.onCreate, ViewModel.setCacheCleanupEnabled)
 * must dispatch onto `Dispatchers.IO`.
 */
class CacheCleanupCoordinator(
    private val cleaner: CacheCleaner = CacheCleaner(),
) {
    @Volatile
    var lastCleanupResult: CacheCleanupSummary? = null
        private set

    fun cleanup(rootDir: File): CacheCleanupSummary {
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
         * `保留目录` description (capture / evidence / wifi_probe / update).
         * LinkedHashMap so the perDir summary walks the buckets in a
         * stable, human-friendly order in logcat / UI.
         */
        val BUCKETS: Map<String, CacheCleanupPolicy> = linkedMapOf(
            "capture" to DefaultCachePolicies.capture,
            "evidence" to DefaultCachePolicies.evidence,
            "wifi_probe" to DefaultCachePolicies.wifiProbe,
            "update" to DefaultCachePolicies.update,
        )
    }
}