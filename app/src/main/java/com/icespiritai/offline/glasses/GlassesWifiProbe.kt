package com.icespiritai.offline.glasses

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The OEM photo path for a *storage* device, measured end to end: join the
 * glasses' own Wi-Fi AP, then FTP a photo off it.
 *
 * Why it exists: our BLE FA12 path moves a 34 KB photo in ~7.5 s (V2.5.8,
 * 2026-09-17), while the OEM app pulls an ordinary photo over FTP from the
 * AP the glasses start on `0x36` + `0x39`. Everything on the glasses' side is
 * known from the APK and from the device: the session comes up in ~0.4 s and
 * reports FTP `192.168.188.1`, SSID `Glasses-A88_556009`, AP password
 * `12345678`; the OEM logs in as `bk7258`/`123456` into `/Picture/`. The one
 * unknown is the phone side — how long joining the AP takes and how fast the
 * transfer is — which decides whether a warm session is worth building.
 *
 * The join follows the OEM's `ApWifiConnector` exactly, because that is what
 * the vendor ships:
 *   1. probe whether the SSID shows up in scan results — useful diagnostics,
 *      but not a gate: the OEM path still asks the system to join the
 *      specifier afterwards, because a soft AP can be hidden from scans;
 *   2. `WifiNetworkSpecifier` + `NetworkRequest` with
 *      `NET_CAPABILITY_INTERNET` removed, so the phone does not demand
 *      internet from a photo-transfer AP, then `requestNetwork`;
 *   3. `bindProcessToNetwork` for the transfer, `unbind` afterwards.
 *
 * Note this is a *normal* AP join, not Wi-Fi Direct: the glasses' Wi-Fi is a
 * Beken BK7258 running its own soft-AP + FTP server. A P2P discovery probe
 * finds no peer at all (2026-09-17), which is what pointed here.
 *
 * Diagnostic only: it never touches the BLE capture pipeline.
 */
class GlassesWifiProbe(private val context: Context) {

    /** One entry of the glasses' `/Picture/` directory. */
    data class FileInfo(val name: String, val size: Long)

    /**
     * Everything the probe learned, with the phase that failed (if any) so a
     * partial run still says where it stopped.
     */
    data class Report(
        val ssid: String = "",
        /** Scan attempts made while waiting for the AP to appear. */
        val scanAttempts: Int = 0,
        /** Discovery start → the SSID became visible. */
        val ssidVisibleMs: Long? = null,
        /** BSSID / frequency / security of the AP once seen. */
        val apSummary: String? = null,
        /** Request-network start → the system handed us the network. */
        val connectMs: Long? = null,
        val networkBound: Boolean = false,
        /** FTP login + LIST. */
        val listingMs: Long? = null,
        val files: List<FileInfo> = emptyList(),
        val fetchedName: String? = null,
        val fetchedBytes: Long? = null,
        /** RETR of one photo, after the listing. */
        val fetchMs: Long? = null,
        val savedTo: String? = null,
        val failedPhase: String? = null,
        val error: String? = null,
    )

    /**
     * Join [ssid] (WPA2, [password]) and pull the newest photo from
     * [ftpHost]`:/Picture/`.
     */
    suspend fun run(
        ssid: String,
        password: String,
        ftpHost: String,
        ssidTimeoutMs: Long = 5_000L,
        connectTimeoutMs: Long = 25_000L,
    ): Report = withContext(Dispatchers.IO) {
        if (ssid.isBlank()) {
            return@withContext Report(failedPhase = "ssid", error = "眼镜未下发 AP SSID")
        }

        val startedAt = System.currentTimeMillis()
        val scan = waitForSsid(ssid, ssidTimeoutMs)
        Log.i(
            TAG,
            if (scan.visible) {
                "wifiProbe: SSID $ssid visible after ${scan.visibleAfterMs}ms (${scan.summary})"
            } else {
                "wifiProbe: SSID $ssid not in ${scan.attempts} scans (${ssidTimeoutMs}ms)"
            },
        )
        // A soft AP can be hidden: do not treat "not in scan results" as
        // terminal. The OEM's `ApWifiConnector` still submits the specifier,
        // and the system can satisfy it from a directed scan / hidden SSID.
        if (!scan.visible) {
            Log.w(
                TAG,
                "wifiProbe: SSID $ssid not in ${scan.attempts} scans — trying hidden specifier join",
            )
        }

        val cm = connectivityManager()
            ?: return@withContext Report(ssid = ssid, failedPhase = "connectivity", error = "无 ConnectivityManager")
        val request = buildRequest(ssid, password)
        val connectStart = System.currentTimeMillis()
        val session = requestNetwork(cm, request, connectTimeoutMs)
        val network = session?.network
        if (network == null) {
            return@withContext Report(
                ssid = ssid,
                scanAttempts = scan.attempts,
                ssidVisibleMs = scan.visibleAfterMs,
                apSummary = scan.summary,
                failedPhase = "connect",
                error = "系统未连接该 AP(需要用户在弹窗中确认;已按隐藏 SSID 尝试)",
            )
        }
        val connectMs = System.currentTimeMillis() - connectStart
        Log.i(TAG, "wifiProbe: network available after ${connectMs}ms (${describe(cm, network)})")

        val bound = runCatching { cm.bindProcessToNetwork(network) }.getOrDefault(false)
        Log.i(TAG, "wifiProbe: bindProcessToNetwork=$bound subnet=${cm.getLinkProperties(network)?.let(::subnetOf)}")
        try {
            val result = ftpFetch(ftpHost)
            Report(
                ssid = ssid,
                scanAttempts = scan.attempts,
                ssidVisibleMs = scan.visibleAfterMs,
                apSummary = scan.summary,
                connectMs = connectMs,
                networkBound = bound,
                listingMs = result.listingMs,
                files = result.files,
                fetchedName = result.name,
                fetchedBytes = result.bytes,
                fetchMs = result.fetchMs,
                savedTo = result.savedTo,
                failedPhase = result.failedPhase,
                error = result.error,
            )
        } finally {
            if (bound) runCatching { cm.bindProcessToNetwork(null) }
            runCatching { cm.unregisterNetworkCallback(session.callback) }
            Log.i(TAG, "wifiProbe: done after ${System.currentTimeMillis() - startedAt}ms")
        }
    }

    // ── AP discovery / join ─────────────────────────────────────────────

    private data class ScanOutcome(val visible: Boolean, val attempts: Int, val visibleAfterMs: Long?, val summary: String?)

    /**
     * Wait until [ssid] appears in scan results, re-scanning every couple of
     * seconds — the OEM's `waitUntilSsidVisible`. Also the only way to tell
     * "the glasses never broadcast an AP" from "the join failed".
     */
    private suspend fun waitForSsid(ssid: String, timeoutMs: Long): ScanOutcome {
        val wifi = context.getSystemService(WifiManager::class.java)
            ?: return ScanOutcome(false, 0, null, null)
        if (!wifi.isWifiEnabled) {
            Log.w(TAG, "wifiProbe: phone Wi-Fi is off")
            return ScanOutcome(false, 0, null, "手机 Wi-Fi 未开启")
        }
        val startedAt = System.currentTimeMillis()
        var attempts = 0
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            attempts++
            runCatching { wifi.startScan() }
            val hit = runCatching {
                @Suppress("DEPRECATION")
                wifi.scanResults.firstOrNull { it.SSID == ssid }
            }.getOrNull()
            if (hit != null) {
                return ScanOutcome(
                    visible = true,
                    attempts = attempts,
                    visibleAfterMs = System.currentTimeMillis() - startedAt,
                    summary = "${hit.BSSID} ${hit.frequency}MHz level=${hit.level} cap=${hit.capabilities}",
                )
            }
            delay(SCAN_INTERVAL_MS)
        }
        return ScanOutcome(false, attempts, null, null)
    }

    /**
     * The OEM's `buildNetworkRequest`: a WPA2 specifier with the internet
     * capability *removed*, so the system accepts an AP that has no uplink.
     */
    private fun buildRequest(ssid: String, password: String): NetworkRequest {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (password.isNotBlank()) setWpa2Passphrase(password) }
            .build()
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
    }

    /** The system's answer to `requestNetwork`: the network plus its callback. */
    private class Session(val network: Network, val callback: ConnectivityManager.NetworkCallback)

    /** `requestNetwork` → the [Network] the system hands over, or `null`. */
    private suspend fun requestNetwork(
        cm: ConnectivityManager,
        request: NetworkRequest,
        timeoutMs: Long,
    ): Session? {
        val available = CompletableDeferred<Network?>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!available.isCompleted) available.complete(network)
            }

            override fun onUnavailable() {
                Log.w(TAG, "wifiProbe: onUnavailable (user did not confirm, or AP rejected)")
                if (!available.isCompleted) available.complete(null)
            }
        }
        runCatching { cm.requestNetwork(request, callback, timeoutMs.toInt()) }
            .onFailure { Log.w(TAG, "wifiProbe: requestNetwork threw ${it.javaClass.simpleName}: ${it.message}") }
        val network = withTimeoutOrNull(timeoutMs + 5_000L) { available.await() }
        if (network == null) {
            // Nothing will call back any more: drop our registration here, or
            // the process keeps a live callback for an AP we never joined.
            runCatching { cm.unregisterNetworkCallback(callback) }
            return null
        }
        return Session(network, callback)
    }

    private fun connectivityManager(): ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private fun describe(cm: ConnectivityManager, network: Network): String = runCatching {
        val link = cm.getLinkProperties(network)
        "iface=${link?.interfaceName} routes=${link?.routes?.take(2)} dns=${link?.dnsServers}"
    }.getOrDefault("?")

    private fun subnetOf(link: LinkProperties): String? =
        link.linkAddresses.firstOrNull()?.toString()

    // ── FTP ─────────────────────────────────────────────────────────────

    private data class FetchResult(
        val listingMs: Long? = null,
        val files: List<FileInfo> = emptyList(),
        val name: String? = null,
        val bytes: Long? = null,
        val fetchMs: Long? = null,
        val savedTo: String? = null,
        val failedPhase: String? = null,
        val error: String? = null,
    )

    /** Login + LIST `/Picture/`, then RETR the newest entry. */
    private fun ftpFetch(host: String): FetchResult {
        if (host.isBlank()) return FetchResult(failedPhase = "host", error = "没有 FTP 地址")
        val client = FtpConnection(host)
        return try {
            val listingStart = System.currentTimeMillis()
            client.connect()
            client.login(FTP_USER, FTP_PASSWORD)
            client.binary()
            val listing = client.list(PICTURE_DIR)
            val listingMs = System.currentTimeMillis() - listingStart
            Log.i(TAG, "wifiProbe: LIST ${listing.size} entries in ${listingMs}ms")
            listing.take(5).forEach { Log.i(TAG, "wifiProbe: file ${it.name} ${it.size}B") }

            val newest = listing.lastOrNull()
                ?: return FetchResult(listingMs = listingMs, failedPhase = "list", error = "目录为空")
            val target = File(
                File(context.cacheDir, "wifi_probe").apply { mkdirs() },
                newest.name,
            )
            val fetchStart = System.currentTimeMillis()
            val bytes = target.outputStream().use { client.retrieve("$PICTURE_DIR/${newest.name}", it) }
            val fetchMs = System.currentTimeMillis() - fetchStart
            Log.i(
                TAG,
                "wifiProbe: RETR ${newest.name} ${bytes}B in ${fetchMs}ms " +
                    "(${bytes * 1000 / maxOf(fetchMs, 1)} B/s) -> ${target.absolutePath}",
            )
            FetchResult(
                listingMs = listingMs,
                files = listing,
                name = newest.name,
                bytes = bytes,
                fetchMs = fetchMs,
                savedTo = target.absolutePath,
            )
        } catch (e: Exception) {
            Log.w(TAG, "wifiProbe: FTP failed: ${e.javaClass.simpleName}: ${e.message}")
            FetchResult(failedPhase = "ftp", error = "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    /**
     * The smallest FTP client that can do this job: login, `PASV`, `LIST`,
     * `RETR`. The glasses use Apache Commons Net on the OEM side, so the
     * protocol is plain RFC 959 — no vendor extensions.
     */
    private class FtpConnection(
        private val host: String,
        private val port: Int = FTP_PORT,
    ) : Closeable {

        private val control = Socket()
        private lateinit var reader: BufferedReader
        private lateinit var writer: PrintWriter

        fun connect() {
            control.connect(InetSocketAddress(host, port), CONTROL_TIMEOUT_MS)
            control.soTimeout = CONTROL_TIMEOUT_MS
            reader = BufferedReader(InputStreamReader(control.inputStream, Charsets.ISO_8859_1))
            writer = PrintWriter(control.outputStream, false)
            val greeting = readReply()
            if (!greeting.startsWith("220")) error("FTP 问候异常:$greeting")
        }

        fun login(user: String, password: String) {
            expect("331", command("USER $user"))
            val reply = command("PASS $password")
            if (!reply.startsWith("230")) error("登录失败:$reply")
        }

        fun binary() {
            expect("200", command("TYPE I"))
        }

        /** `LIST` over a passive data connection. */
        fun list(path: String): List<FileInfo> {
            val (dataHost, dataPort) = passive()
            expect("150", command("LIST $path"))
            val entries = Socket().use { data ->
                data.connect(InetSocketAddress(dataHost, dataPort), DATA_TIMEOUT_MS)
                BufferedReader(InputStreamReader(data.inputStream, Charsets.ISO_8859_1))
                    .readLines()
                    .mapNotNull(::parseListLine)
            }
            expect("226", readReply())
            return entries
        }

        /** `RETR [path]` into [sink]; returns the byte count. */
        fun retrieve(path: String, sink: OutputStream): Long {
            val (dataHost, dataPort) = passive()
            expect("150", command("RETR $path"))
            val bytes = Socket().use { data ->
                data.connect(InetSocketAddress(dataHost, dataPort), DATA_TIMEOUT_MS)
                data.getInputStream().use { input -> input.copyTo(sink) }
            }
            expect("226", readReply())
            return bytes
        }

        /** `PASV` → (dataHost, dataPort) from the `227 … (h1,h2,h3,h4,p1,p2)` reply. */
        private fun passive(): Pair<String, Int> {
            val reply = command("PASV")
            if (!reply.startsWith("227")) error("PASV 被拒:$reply")
            val numbers = PASV_PATTERN.find(reply)?.groupValues?.drop(1)?.map { it.toInt() }
                ?: error("PASV 响应无法解析:$reply")
            return numbers.take(4).joinToString(".") to (numbers[4] * 256 + numbers[5])
        }

        private fun command(line: String): String {
            writer.print(line + "\r\n")
            writer.flush()
            return readReply()
        }

        /** One FTP reply, joining `123-…` continuation lines up to `123 …`. */
        private fun readReply(): String {
            val first = reader.readLine() ?: error("连接被关闭")
            if (first.length < 4 || first[3] != '-') return first
            val code = first.take(3)
            val lines = StringBuilder(first)
            while (true) {
                val line = reader.readLine() ?: break
                lines.append('\n').append(line)
                if (line.startsWith("$code ")) break
            }
            return lines.toString()
        }

        private fun expect(code: String, reply: String) {
            if (!reply.startsWith(code)) error("期望 $code,收到:$reply")
        }

        override fun close() {
            runCatching { writer.print("QUIT\r\n"); writer.flush() }
            runCatching { control.close() }
        }
    }

    /** Internal so the unit tests can exercise the listing parser directly. */
    internal companion object {
        const val TAG = "GlassesCapture"

        /** OEM `MediaSyncManager.downloadMediaFiles` credentials. */
        const val FTP_USER = "bk7258"
        const val FTP_PASSWORD = "123456"

        /** OEM `PhotoCaptureService.downloadPhotoFromFtp` directory. */
        const val PICTURE_DIR = "/Picture"

        const val FTP_PORT = 21
        const val CONTROL_TIMEOUT_MS = 12_000
        const val DATA_TIMEOUT_MS = 20_000
        const val SCAN_INTERVAL_MS = 2_000L

        val PASV_PATTERN = Regex("""\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)""")

        /**
         * `-rw-r--r-- 1 1000 1000 34019 Sep 17 12:51 IMG_0001.JPG` →
         * name + size. Dot entries and anything with too few fields are
         * dropped rather than guessed at.
         */
        fun parseListLine(line: String): FileInfo? {
            // limit = 9 keeps a file name that contains spaces intact.
            val parts = line.trim().split(Regex("\\s+"), limit = 9)
            if (parts.size < 5 || parts[0] == "total") return null
            val name = parts.getOrNull(8) ?: return null
            if (name == "." || name == "..") return null
            val size = parts[4].toLongOrNull() ?: return null
            return FileInfo(name, size)
        }
    }
}
