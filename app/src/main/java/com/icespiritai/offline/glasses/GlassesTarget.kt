package com.icespiritai.offline.glasses

/**
 * Everything the OS can tell us about "which glasses is the user going to
 * hand us", captured in one value so the decision can be made by a pure
 * function (see [resolveGlassesTarget]).
 *
 * Every flag defaults to the healthy value: a snapshot built on a device
 * where Bluetooth behaves as expected needs no arguments, and tests only
 * override the dimension under test.
 */
data class BondedSnapshot(
    /** `BluetoothAdapter.getDefaultAdapter()` returned null (no radio). */
    val adapterAvailable: Boolean = true,
    /** Radio is on. Meaningless when [adapterAvailable] is false. */
    val bluetoothEnabled: Boolean = true,
    /**
     * `BLUETOOTH_CONNECT` is held (always true below API 31, where the
     * permission does not exist). When false, [bondedGlasses] is empty
     * because the OS refuses to answer at all.
     */
    val connectPermissionGranted: Boolean = true,
    /** Bonded devices whose name matches [GlassesDevice.NAME_PREFIXES]. */
    val bondedGlasses: List<GlassesDevice> = emptyList(),
) {
    companion object {
        val NO_ADAPTER = BondedSnapshot(adapterAvailable = false)
        val PERMISSION_MISSING = BondedSnapshot(connectPermissionGranted = false)
        val BLUETOOTH_OFF = BondedSnapshot(bluetoothEnabled = false)
    }
}

/**
 * Outcome of resolving "which glasses should this capture talk to".
 *
 * The failure arms are separate objects rather than a `null` plus a
 * message, because each one needs different UI: 未配对 is a *setup* problem
 * (system Bluetooth settings), 未授权 is a *permission* problem (re-ask or
 * open app settings), 蓝牙关闭 is a *radio* problem. Collapsing them into
 * `null` is what produced the v0.4.2-era behaviour where "no glasses
 * paired" surfaced as a bare Toast and the other causes surfaced not at
 * all.
 */
sealed interface GlassesTarget {

    data class Ready(
        val device: GlassesDevice,
        val source: Source,
    ) : GlassesTarget {
        enum class Source {
            /** Address this app connected to successfully at least once. */
            RememberedAddress,

            /** Read out of the OS bonded-device list right now. */
            SystemBonded,
        }
    }

    /** `BLUETOOTH_CONNECT` missing on API 31+. */
    data object ConnectPermissionMissing : GlassesTarget

    /** No Bluetooth radio at all (some emulators / stripped ROMs). */
    data object BluetoothUnavailable : GlassesTarget

    /** Radio present but switched off. */
    data object BluetoothOff : GlassesTarget

    /** Permission fine, radio fine, but nothing to connect to. */
    data object NotPaired : GlassesTarget
}

/**
 * Pure decision table for the glasses target.
 *
 * Extracted from `HomeScreen.launchGlassesCapture` (v0.4.3) for two
 * reasons: the branch chain that used to live inside the Composable was
 * the bug's blast radius (one unguarded platform call in it took the whole
 * process down), and pulling the decision out lets unit tests pin every
 * branch — including the ones that only fire on a phone with no glasses
 * paired, which is exactly the configuration the crash report came from.
 *
 * **Precedence** (first match wins):
 *
 *  1. hardware / permission / radio problems — reported before anything
 *     else, because none of them can be worked around by pairing;
 *  2. a remembered address that is still present in the OS bonded list —
 *     the strongest signal: previously used *and* currently authenticated;
 *  3. the first bonded glasses — the OS list is the source of truth for
 *     which device can actually complete a secure connection, so it wins
 *     when the user swapped glasses;
 *  4. a remembered address that is not in a readable (empty) matching set —
 *     e.g. the peripheral was renamed out of our prefix list, or a ROM
 *     reports an empty list spuriously. Keeps the pre-v0.4.3 happy path
 *     (every successful smoke test went through it) instead of regressing
 *     to 未配对;
 *  5. nothing — [GlassesTarget.NotPaired].
 *
 * @param nowMs injected so tests are not time-dependent; used only to
 *   stamp `lastSeenMs` on a device rebuilt from the remembered address.
 */
fun resolveGlassesTarget(
    lastPairedAddress: String?,
    snapshot: BondedSnapshot,
    nowMs: Long,
): GlassesTarget {
    if (!snapshot.adapterAvailable) return GlassesTarget.BluetoothUnavailable
    if (!snapshot.connectPermissionGranted) return GlassesTarget.ConnectPermissionMissing
    if (!snapshot.bluetoothEnabled) return GlassesTarget.BluetoothOff

    val remembered = lastPairedAddress?.trim()?.takeIf { it.isNotEmpty() }
    val bonded = snapshot.bondedGlasses

    if (remembered != null) {
        bonded.firstOrNull { it.address.equals(remembered, ignoreCase = true) }?.let {
            return GlassesTarget.Ready(it, GlassesTarget.Ready.Source.SystemBonded)
        }
    }
    if (bonded.isNotEmpty()) {
        return GlassesTarget.Ready(bonded.first(), GlassesTarget.Ready.Source.SystemBonded)
    }
    if (remembered != null) {
        return GlassesTarget.Ready(
            device = GlassesDevice(
                address = remembered,
                name = GlassesDevice.NAME_PREFIX,
                lastSeenMs = nowMs,
            ),
            source = GlassesTarget.Ready.Source.RememberedAddress,
        )
    }
    return GlassesTarget.NotPaired
}
