package com.icespiritai.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.icespiritai.offline.glasses.BluetoothController
import com.icespiritai.offline.glasses.GlassesDeviceStore
import com.icespiritai.offline.glasses.GlassesFirmwareService
import com.icespiritai.offline.glasses.GlassesFirmwareUpdater
import com.icespiritai.offline.glasses.GlassesPhotoCaptureRepository
import com.icespiritai.offline.updater.DownloadStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Process-wide singleton for shared DataStore-backed stores and the
 * glasses BLE pipeline. Keeps long-lived instances alive across Activity
 * recreation and Service / Worker processes.
 *
 * File `update_state.preferences_pb` lives in the app's `datastore/` dir.
 */
object AppGraph {
    @Volatile private var storeInstance: DataStore<Preferences>? = null

    @Synchronized
    fun dataStore(context: Context): DataStore<Preferences> {
        return storeInstance ?: PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("update_state") }
        ).also { storeInstance = it }
    }

    fun downloadStateStore(context: Context): DownloadStateStore =
        DownloadStateStore(dataStore(context))

    // ────────────────────────────────────────────────────────────────────
    // Smart-glasses pipeline (Glass-D15 V2.4.5+)
    //
    // Process-scoped singleton per the same precedent as the update
    // DataStore: the GATT handle, BLE state flows, and capture coroutine
    // scope outlive Activity recreation. The PaddleOCR engine is held by
    // `IceSpiritVisionViewModel`'s lazy; the BLE controller + capture
    // repository are held here.
    // ────────────────────────────────────────────────────────────────────

    @Volatile private var glassesDeviceStoreInstance: GlassesDeviceStore? = null
    @Volatile private var glassesScope: CoroutineScope? = null
    @Volatile private var bluetoothControllerInstance: BluetoothController? = null
    @Volatile private var glassesCaptureRepositoryInstance: GlassesPhotoCaptureRepository? = null
    @Volatile private var glassesFirmwareUpdaterInstance: GlassesFirmwareUpdater? = null

    @Synchronized
    fun glassesDeviceStore(context: Context): GlassesDeviceStore {
        return glassesDeviceStoreInstance ?: GlassesDeviceStore(context.applicationContext)
            .also { glassesDeviceStoreInstance = it }
    }

    /**
     * Process-lifetime [CoroutineScope] for glasses pipeline work. Uses
     * a [SupervisorJob] so a failure in one capture doesn't poison the
     * scope (the next capture still launches).
     */
    private fun glassesScope(): CoroutineScope =
        glassesScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
            .also { glassesScope = it }

    @Synchronized
    fun bluetoothController(context: Context): BluetoothController {
        return bluetoothControllerInstance ?: BluetoothController(
            context = context.applicationContext,
            scope = glassesScope(),
            deviceStore = glassesDeviceStore(context.applicationContext),
        ).also { bluetoothControllerInstance = it }
    }

    @Synchronized
    fun glassesPhotoCaptureRepository(context: Context): GlassesPhotoCaptureRepository {
        return glassesCaptureRepositoryInstance ?: GlassesPhotoCaptureRepository(
            context = context.applicationContext,
            bluetoothController = bluetoothController(context.applicationContext),
            scope = glassesScope(),
        ).also { glassesCaptureRepositoryInstance = it }
    }

    /**
     * Firmware OTA orchestrator (check via the vendor OTA API → hand the
     * download URL to the glasses over BLE → watch the version change).
     * Process-scoped for the same reason as the capture repository: a
     * 20-minute watch must survive Activity recreation.
     */
    @Synchronized
    fun glassesFirmwareUpdater(context: Context): GlassesFirmwareUpdater {
        return glassesFirmwareUpdaterInstance ?: GlassesFirmwareUpdater(
            photoRepository = glassesPhotoCaptureRepository(context.applicationContext),
            controller = bluetoothController(context.applicationContext),
            service = GlassesFirmwareService(),
            scope = glassesScope(),
        ).also { glassesFirmwareUpdaterInstance = it }
    }
}
