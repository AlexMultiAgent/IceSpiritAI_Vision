package com.aiglass.zhangwen.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aiglass.zhangwen.config.BoundDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

/**
 * 精简版眼镜蓝牙控制器。
 * 绑定流程对齐原 glassfront「App 内完成」：
 * 点绑定 → 首次绑定观测 → BLE GATT（未配对则先 createBond）→ BLE 就绪后拉经典 ACL。
 */
@SuppressLint("MissingPermission")
class BluetoothController(private val appContext: Context) {

    private val tag = "AiGlassBt"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val packetBuilder = BlePacketBuilder()
    private val cmd = packetBuilder.cfg

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val devicePrefixes = listOf("Glasses", "A88_")

    private val SERVICE_UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb")
    private val WRITE_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb")
    private val NOTIFY_UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val PHOTO_SERVICE_UUID = UUID.fromString("0000FA10-0000-1000-8000-00805f9b34fb")
    private val PHOTO_CTRL_UUID = UUID.fromString("0000FA11-0000-1000-8000-00805f9b34fb")
    private val PHOTO_DATA_UUID = UUID.fromString("0000FA12-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var photoCtrlChar: BluetoothGattCharacteristic? = null
    private var photoDataChar: BluetoothGattCharacteristic? = null
    private var photoFa12NotifyEnabled = false
    private var fff2NotifyEnabled = false
    private var pendingSubscribeFa12AfterFff2 = false
    private var fa12CccdRetryCount = 0
    private var currentMtu = 23
    private var postConnectSetupDone = false
    private var seq: Byte = 0
    private var servicesDiscoveryRequested = false

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discovered = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ScannedDevice>> = _discovered.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _connectedMac = MutableStateFlow<String?>(null)
    val connectedDeviceMac: StateFlow<String?> = _connectedMac.asStateFlow()

    private val _firmwareVersion = MutableStateFlow("")
    val firmwareVersion: StateFlow<String> = _firmwareVersion.asStateFlow()

    private val _commandReady = MutableStateFlow(false)
    val isCommandChannelReady: StateFlow<Boolean> = _commandReady.asStateFlow()

    private val _aiPhotoBleTerminal = MutableStateFlow<AiPhotoBleEvent?>(null)
    val aiPhotoBleTerminal: StateFlow<AiPhotoBleEvent?> = _aiPhotoBleTerminal.asStateFlow()

    private val _aiPhotoBleEvents = MutableSharedFlow<AiPhotoBleEvent>(extraBufferCapacity = 64)
    val aiPhotoBleEvents: SharedFlow<AiPhotoBleEvent> = _aiPhotoBleEvents

    private val _isClassicBonding = MutableStateFlow(false)
    val isClassicBonding: StateFlow<Boolean> = _isClassicBonding.asStateFlow()

    private val _isClassicBonded = MutableStateFlow(false)
    val isClassicBonded: StateFlow<Boolean> = _isClassicBonded.asStateFlow()

    private val _isClassicConnected = MutableStateFlow(false)
    val isClassicConnected: StateFlow<Boolean> = _isClassicConnected.asStateFlow()

    private val _isClassicConnecting = MutableStateFlow(false)
    val isClassicConnecting: StateFlow<Boolean> = _isClassicConnecting.asStateFlow()

    private val _bindStatusMessage = MutableStateFlow<String?>(null)
    val bindStatusMessage: StateFlow<String?> = _bindStatusMessage.asStateFlow()

    /** 对齐原项目 isFullyLinked：BLE 通道就绪 + 经典已配对（ACL/Profile 尽力拉起） */
    private val _isFullyLinked = MutableStateFlow(false)
    val isFullyLinked: StateFlow<Boolean> = _isFullyLinked.asStateFlow()

    private val aiPhotoStateLock = Any()
    private var aiPhotoBleBuffer: ByteArray? = null
    private var aiPhotoBleCovered: BitSet? = null
    private var aiPhotoBleTotalSize = 0
    private var aiPhotoBleFileName = ""
    private var aiPhotoCmdSent = false
    private var aiPhotoLocalDelivered = false
    private var aiPhotoAwaitingCrcConfirm = false
    private val aiPhotoEarlyChunks = mutableListOf<Pair<Int, ByteArray>>()
    private var aiPhotoSession = 0

    private val scanResults = ConcurrentHashMap<String, ScannedDevice>()
    private var scanTimeoutRunnable: Runnable? = null
    private var pendingBindAddress: String? = null
    private var pendingBindName: String? = null
    private var bondReceiverRegistered = false
    private var isUnbinding = false
    /** 首次绑定观测（对齐 beginFirstBindClassicConnect） */
    private var firstBindClassicObserveActive = false
    private var classicAclPullJob: Job? = null
    private var connectJob: Job? = null

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = deviceFromIntent(intent) ?: return
                    if (pendingBindAddress != null &&
                        !device.address.equals(pendingBindAddress, ignoreCase = true)
                    ) {
                        return
                    }
                    val state = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR
                    )
                    when (state) {
                        BluetoothDevice.BOND_BONDING -> {
                            _isClassicBonding.value = true
                            _isClassicConnecting.value = true
                            _bindStatusMessage.value = "正在配对音频通道（蓝牙）…"
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            _isClassicBonding.value = false
                            _isClassicBonded.value = true
                            refreshDeviceBondFlags()
                            syncClassicConnectionState(device)
                            // 不在此处开 GATT：由 bind 协程 ensureBindPairingBeforeGatt 等待后继续
                        }
                        BluetoothDevice.BOND_NONE -> {
                            if (isUnbinding) return
                            _isClassicBonding.value = false
                            _isClassicBonded.value = false
                            _isClassicConnected.value = false
                            refreshFullyLinked()
                            if (firstBindClassicObserveActive) {
                                _bindStatusMessage.value = "经典蓝牙配对取消或失败，请重试"
                            }
                            refreshDeviceBondFlags()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = deviceFromIntent(intent) ?: return
                    if (device.address.equals(pendingBindAddress ?: _connectedMac.value, true)) {
                        Log.i(tag, "经典 ACL 已连接: ${device.address}")
                        _isClassicConnected.value = true
                        _isClassicConnecting.value = false
                        refreshFullyLinked()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = deviceFromIntent(intent) ?: return
                    if (device.address.equals(_connectedMac.value, true)) {
                        _isClassicConnected.value = false
                        refreshFullyLinked()
                    }
                }
            }
        }
    }

    init {
        registerBondReceiver()
        val mac = BoundDeviceStore.getMac(appContext)
        if (mac != null) {
            _isClassicBonded.value = isDeviceClassicBonded(mac)
        }
    }

    private fun deviceFromIntent(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(bondReceiver, filter)
        }
        bondReceiverRegistered = true
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName
                ?: device.name
                ?: return
            if (devicePrefixes.none { name.startsWith(it, ignoreCase = true) }) return
            val bonded = device.bondState == BluetoothDevice.BOND_BONDED
            val item = ScannedDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                classicBonded = bonded
            )
            scanResults[device.address] = item
            publishDiscovered()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "扫描失败: $errorCode")
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(tag, "GATT 已连接，开始发现服务")
                _isConnected.value = true
                _isConnecting.value = false
                val name = g.device?.name ?: BoundDeviceStore.getName(appContext) ?: "眼镜"
                val mac = g.device?.address.orEmpty()
                _connectedName.value = name
                _connectedMac.value = mac
                BoundDeviceStore.save(appContext, name, mac)
                _isClassicBonded.value = isDeviceClassicBonded(mac)
                _bindStatusMessage.value = "绑定完成"
                pendingBindAddress = null
                pendingBindName = null
                servicesDiscoveryRequested = false
                // 先协商 MTU，完成后再 discoverServices，避免与后续 CCCD 写入打架
                mainHandler.postDelayed({
                    val ok = runCatching { g.requestMtu(517) }.getOrDefault(false)
                    if (!ok) {
                        Log.w(tag, "requestMtu 未被受理，直接发现服务")
                        requestDiscoverServices(g, "mtu_rejected")
                    }
                }, 200)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(tag, "GATT 断开 status=$status")
                cleanupGattState(keepBound = true)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
                Log.i(tag, "MTU=$mtu")
            } else {
                Log.w(tag, "MTU 协商失败 status=$status，继续发现服务")
            }
            requestDiscoverServices(g, "mtu_changed")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(tag, "服务发现失败: $status")
                return
            }
            val service = g.getService(SERVICE_UUID)
            writeChar = service?.getCharacteristic(WRITE_UUID)
            notifyChar = service?.getCharacteristic(NOTIFY_UUID)

            val photoService = g.getService(PHOTO_SERVICE_UUID)
            photoCtrlChar = photoService?.getCharacteristic(PHOTO_CTRL_UUID)
            photoDataChar = photoService?.getCharacteristic(PHOTO_DATA_UUID)
            photoFa12NotifyEnabled = false
            fff2NotifyEnabled = false

            if (writeChar == null || notifyChar == null) {
                Log.w(tag, "未找到 FFF0 指令通道")
                return
            }
            Log.i(
                tag,
                "服务发现完成 FA10=${photoService != null} FA12=${photoDataChar != null} " +
                    "FA11=${photoCtrlChar != null} MTU=$currentMtu"
            )
            // 必须串行写 CCCD：先 FFF2，完成后再订 FA12
            pendingSubscribeFa12AfterFff2 = photoDataChar != null
            enableNotify(g, notifyChar!!, "FFF2")
            if (photoDataChar == null) {
                Log.w(tag, "未找到 FA10/FA12，识图 BLE 传图不可用")
                finishPostConnectSetup(g)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val charUuid = descriptor.characteristic?.uuid
            when (charUuid) {
                NOTIFY_UUID -> {
                    fff2NotifyEnabled = status == BluetoothGatt.GATT_SUCCESS
                    Log.i(tag, "FFF2 CCCD status=$status enabled=$fff2NotifyEnabled")
                    if (pendingSubscribeFa12AfterFff2 && photoDataChar != null) {
                        pendingSubscribeFa12AfterFff2 = false
                        // 稍延后写入，部分机型连续写 descriptor 仍会失败
                        mainHandler.postDelayed({
                            if (_isConnected.value && gatt === g && photoDataChar != null) {
                                enableNotify(g, photoDataChar!!, "FA12")
                            }
                        }, 80)
                    } else {
                        finishPostConnectSetup(g)
                    }
                }
                PHOTO_DATA_UUID -> {
                    photoFa12NotifyEnabled = status == BluetoothGatt.GATT_SUCCESS
                    Log.i(
                        tag,
                        "FA12 CCCD status=$status enabled=$photoFa12NotifyEnabled " +
                            "retry=$fa12CccdRetryCount"
                    )
                    when {
                        photoFa12NotifyEnabled -> {
                            fa12CccdRetryCount = 0
                            finishPostConnectSetup(g)
                        }
                        fa12CccdRetryCount < 2 -> {
                            fa12CccdRetryCount++
                            mainHandler.postDelayed({
                                if (_isConnected.value && gatt === g &&
                                    photoDataChar != null && !photoFa12NotifyEnabled
                                ) {
                                    Log.w(tag, "FA12 CCCD 失败，重试 #$fa12CccdRetryCount")
                                    enableNotify(g, photoDataChar!!, "FA12_retry")
                                } else {
                                    finishPostConnectSetup(g)
                                }
                            }, 200)
                        }
                        else -> {
                            Log.e(tag, "FA12 CCCD 多次失败，识图 BLE 可能不可用")
                            finishPostConnectSetup(g)
                        }
                    }
                }
                else -> Log.d(tag, "其它 descriptor 写入 uuid=$charUuid status=$status")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            dispatchNotify(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            dispatchNotify(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(tag, "写入失败 uuid=${characteristic.uuid} status=$status")
            }
        }
    }

    fun refreshBluetoothState() {
        _isBluetoothEnabled.value = adapter?.isEnabled == true
    }

    fun startScan() {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            _isBluetoothEnabled.value = false
            return
        }
        stopScan()
        scanResults.clear()
        mergeBondedDevicesIntoScan()
        publishDiscovered()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        ad.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        _isScanning.value = true
        scanTimeoutRunnable = Runnable { stopScan() }.also {
            mainHandler.postDelayed(it, 40_000)
        }
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        scanTimeoutRunnable = null
        _isScanning.value = false
    }

    /**
     * 绑定入口（对齐原项目 IN_APP_COMPLETE）：
     * beginFirstBindClassicConnect → BLE connect；未配对则 GATT 前 createBond；
     * BLE setup 完成后再拉经典 ACL。
     */
    fun bindDevice(address: String, name: String? = null) {
        stopScan()
        registerBondReceiver()
        pendingBindAddress = address
        pendingBindName = name
        _isConnecting.value = true
        _bindStatusMessage.value = "正在连接眼镜…"
        if (!name.isNullOrBlank()) {
            BoundDeviceStore.save(appContext, name, address)
        } else {
            BoundDeviceStore.save(appContext, BoundDeviceStore.getName(appContext) ?: "眼镜", address)
        }
        beginFirstBindClassicConnect(address)
        connectInternal(address, name, userInitiatedBind = true)
    }

    /** 仅 BLE 重连（启动恢复；已绑定设备） */
    fun connect(address: String, name: String? = null) {
        pendingBindAddress = address
        pendingBindName = name
        connectInternal(address, name, userInitiatedBind = false)
    }

    private fun beginFirstBindClassicConnect(address: String) {
        firstBindClassicObserveActive = true
        _isClassicConnecting.value = true
        _isClassicBonded.value = isDeviceClassicBonded(address)
        Log.i(tag, "首次绑定：观测经典链路 mac=$address（ACL 拉取延后到 BLE 就绪）")
    }

    fun endFirstBindClassicConnect(reason: String = "done") {
        if (!firstBindClassicObserveActive) return
        firstBindClassicObserveActive = false
        classicAclPullJob?.cancel()
        classicAclPullJob = null
        _isClassicConnecting.value = false
        Log.i(tag, "首次绑定结束 reason=$reason")
    }

    private fun connectInternal(
        address: String,
        name: String?,
        userInitiatedBind: Boolean
    ) {
        val ad = adapter ?: return
        connectJob?.cancel()
        connectJob = mainScope.launch {
            val device = ad.getRemoteDevice(address)
            if (userInitiatedBind || firstBindClassicObserveActive) {
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    _bindStatusMessage.value = "正在配对音频通道（蓝牙）…"
                    val ok = ensureBindPairingBeforeGatt(device)
                    if (!ok) {
                        _isConnecting.value = false
                        _bindStatusMessage.value = "经典蓝牙配对未完成，请在系统弹窗中确认后重试"
                        return@launch
                    }
                }
            }
            if (!isActive) return@launch
            _bindStatusMessage.value = "正在连接低功耗蓝牙…"
            openGattConnection(address, name)
        }
    }

    private suspend fun ensureBindPairingBeforeGatt(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            _isClassicBonded.value = true
            return true
        }
        _isClassicBonding.value = true
        _isClassicConnecting.value = true
        val accepted = requestClassicBond(device)
        if (!accepted && device.bondState == BluetoothDevice.BOND_NONE) {
            // 再试一次普通 createBond
            runCatching { device.createBond() }
        }
        val bonded = withTimeoutOrNull(20_000L) {
            while (isActive) {
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> return@withTimeoutOrNull true
                    else -> delay(300)
                }
            }
            false
        } == true
        _isClassicBonding.value = false
        _isClassicBonded.value = bonded
        return bonded
    }

    private fun openGattConnection(address: String, name: String?) {
        val ad = adapter ?: return
        // 不断开已有同设备连接
        if (_isConnected.value &&
            _connectedMac.value.equals(address, ignoreCase = true) &&
            gatt != null
        ) {
            _isConnecting.value = false
            return
        }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        _isConnecting.value = true
        val device = ad.getRemoteDevice(address)
        if (!name.isNullOrBlank()) {
            BoundDeviceStore.save(appContext, name, address)
        }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    private fun scheduleClassicAclPullForFirstBind(address: String, reason: String) {
        if (!firstBindClassicObserveActive) return
        classicAclPullJob?.cancel()
        classicAclPullJob = mainScope.launch {
            pullClassicAclForFirstBind(address, reason)
        }
    }

    private suspend fun pullClassicAclForFirstBind(address: String, reason: String) {
        val device = adapter?.getRemoteDevice(address) ?: return
        Log.i(tag, "开始绑定期经典 ACL 拉取 reason=$reason mac=$address")
        _isClassicConnecting.value = true
        _bindStatusMessage.value = "正在配对音频通道（蓝牙）…"

        // 等 BLE setup 稳定
        delay(800)
        if (!_isConnected.value) return

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            ensureBindPairingBeforeGatt(device)
        }
        if (!_isConnected.value) return

        // BluetoothDevice.connect() 隐藏 API 轻拉经典 ACL
        runCatching {
            val method = device.javaClass.getMethod("connect")
            method.isAccessible = true
            method.invoke(device)
            Log.i(tag, "已调用 BluetoothDevice.connect() 拉经典")
        }.onFailure {
            Log.d(tag, "Device.connect 不可用: ${it.message}")
        }

        // 轮询 ACL / Profile
        withTimeoutOrNull(15_000L) {
            while (isActive && _isConnected.value) {
                if (syncClassicConnectionState(device)) return@withTimeoutOrNull true
                delay(500)
            }
            false
        }

        if (_isClassicConnected.value || device.bondState == BluetoothDevice.BOND_BONDED) {
            _isClassicBonded.value = device.bondState == BluetoothDevice.BOND_BONDED
            if (!_isClassicConnected.value && _isClassicBonded.value) {
                // 已配对但 Profile 未挂上：绑定阶段视为可完成（对齐原项目不强制 HFP）
                _isClassicConnected.value = true
            }
            _isClassicConnecting.value = false
            _bindStatusMessage.value = "配对成功"
            endFirstBindClassicConnect("classic_ready")
        } else {
            _isClassicConnecting.value = false
            _bindStatusMessage.value = "BLE 已连接；经典蓝牙未完全就绪，可在系统蓝牙中确认连接"
        }
        refreshFullyLinked()
        refreshDeviceBondFlags()
    }

    private fun syncClassicConnectionState(device: BluetoothDevice): Boolean {
        val bonded = device.bondState == BluetoothDevice.BOND_BONDED
        _isClassicBonded.value = bonded
        val bm = bluetoothManager
        val hfp = runCatching {
            bm.getConnectionState(device, BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        val a2dp = runCatching {
            bm.getConnectionState(device, BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        val acl = bonded && runCatching {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean == true
        }.getOrDefault(false)
        val ready = hfp || a2dp || acl
        if (ready) {
            _isClassicConnected.value = true
            _isClassicConnecting.value = false
        }
        refreshFullyLinked()
        return ready
    }

    private fun refreshFullyLinked() {
        _isFullyLinked.value =
            _isConnected.value && _commandReady.value &&
                (_isClassicConnected.value || _isClassicBonded.value)
    }

    fun disconnect(keepBound: Boolean = true) {
        connectJob?.cancel()
        classicAclPullJob?.cancel()
        if (!keepBound) endFirstBindClassicConnect("disconnect")
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        cleanupGattState(keepBound)
    }

    fun unbind() {
        isUnbinding = true
        endFirstBindClassicConnect("unbind")
        val mac = _connectedMac.value ?: BoundDeviceStore.getMac(appContext)
        disconnect(keepBound = false)
        if (mac != null) {
            runCatching {
                val device = adapter?.getRemoteDevice(mac)
                if (device != null) removeClassicBond(device)
            }
        }
        BoundDeviceStore.clear(appContext)
        _isClassicBonded.value = false
        _isClassicBonding.value = false
        _isClassicConnected.value = false
        _isClassicConnecting.value = false
        _isFullyLinked.value = false
        _bindStatusMessage.value = null
        pendingBindAddress = null
        pendingBindName = null
        mainHandler.postDelayed({ isUnbinding = false }, 1500)
    }

    fun scheduleStartupAutoReconnect() {
        val mac = BoundDeviceStore.getMac(appContext) ?: return
        if (_isConnected.value || _isConnecting.value) return
        mainHandler.postDelayed({
            if (!_isConnected.value) {
                connect(mac, BoundDeviceStore.getName(appContext))
            }
        }, 800)
    }

    private fun requestClassicBond(device: BluetoothDevice): Boolean {
        // 原项目：国产双模优先普通 createBond 弹系统框；再回退 BREDR
        val plain = runCatching { device.createBond() }.getOrDefault(false)
        if (plain) return true
        return requestClassicBondBredr(device)
    }

    private fun requestClassicBondBredr(device: BluetoothDevice): Boolean {
        return runCatching {
            val transportBredr = BluetoothDevice.TRANSPORT_BREDR
            val method = device.javaClass.getMethod("createBond", Int::class.javaPrimitiveType)
            method.isAccessible = true
            val result = method.invoke(device, transportBredr) as? Boolean ?: false
            Log.i(tag, "createBond(TRANSPORT_BREDR)=$result mac=${device.address}")
            result
        }.onFailure { err ->
            Log.d(tag, "createBond(BREDR) 不可用: ${err.message}")
        }.getOrDefault(false)
    }

    private fun removeClassicBond(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_NONE) return
        runCatching {
            val method = device.javaClass.getMethod("removeBond")
            method.isAccessible = true
            val result = method.invoke(device) as? Boolean ?: false
            Log.i(tag, "removeBond=$result mac=${device.address}")
        }.onFailure {
            Log.w(tag, "removeBond 失败: ${device.address}", it)
        }
    }

    private fun isDeviceClassicBonded(address: String): Boolean {
        return runCatching {
            adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        }.getOrDefault(false)
    }

    private fun mergeBondedDevicesIntoScan() {
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
        for (device in bonded) {
            val name = device.name ?: continue
            if (devicePrefixes.none { name.startsWith(it, ignoreCase = true) }) continue
            val existing = scanResults[device.address]
            scanResults[device.address] = ScannedDevice(
                name = name,
                address = device.address,
                rssi = existing?.rssi ?: -100,
                classicBonded = true
            )
        }
    }

    private fun refreshDeviceBondFlags() {
        val updated = scanResults.mapValues { (mac, item) ->
            item.copy(classicBonded = isDeviceClassicBonded(mac))
        }
        scanResults.clear()
        scanResults.putAll(updated)
        mergeBondedDevicesIntoScan()
        publishDiscovered()
    }

    private fun publishDiscovered() {
        _discovered.value = scanResults.values.sortedWith(
            compareByDescending<ScannedDevice> { it.classicBonded }
                .thenByDescending { it.rssi }
        )
    }

    fun isCommandChannelReady(): Boolean = _commandReady.value

    fun takePhoto(): Boolean {
        if (!isCommandChannelReady()) {
            Log.w(tag, "普通拍照失败：指令通道未就绪")
            return false
        }
        writeCommand(packetBuilder.buildTakePhotoPacket(nextSeq()), "takePhoto_0x30")
        return true
    }

    fun prewarmAiPhotoBlePriority() {
        runCatching {
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        }
    }

    /** 识图前确保 FA12 已订阅；若未订阅则补订并短等 */
    fun ensureAiPhotoChannelReady(timeoutMs: Long = 3_000L): Boolean {
        if (currentMtu < 247) {
            Log.w(tag, "MTU 不足: $currentMtu")
            return false
        }
        if (photoFa12NotifyEnabled) return true
        val g = gatt ?: return false
        ensurePhotoDataNotifySubscribed(g, "pre_capture")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (photoFa12NotifyEnabled) return true
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
        return photoFa12NotifyEnabled
    }

    fun requestAiPhotoBleCapture(quality: Int = 80): Boolean {
        if (!isCommandChannelReady()) {
            emitAiFailed("指令通道未就绪，请先连接眼镜")
            return false
        }
        if (currentMtu < 247) {
            emitAiFailed("识图通道未就绪(MTU=$currentMtu)，请断开重连后再试")
            return false
        }
        if (!photoFa12NotifyEnabled) {
            val ok = ensureAiPhotoChannelReady(3_000L)
            if (!ok) {
                emitAiFailed(
                    "识图通道未就绪(MTU=$currentMtu, FA12=false)，请断开重连后再试"
                )
                return false
            }
        }
        clearAiPhotoState(keepTerminal = false)
        aiPhotoSession++
        aiPhotoCmdSent = true
        _aiPhotoBleTerminal.value = null
        prewarmAiPhotoBlePriority()
        writeCommand(packetBuilder.buildAiPhotoRequestPacket(nextSeq(), quality), "aiPhoto_0x33")
        return true
    }

    fun cancelAiPhotoBleTransfer(reason: String = "app_cancel") {
        writePhotoCtrl(byteArrayOf(0x04), "cancel:$reason")
        clearAiPhotoState(keepTerminal = true)
    }

    private fun refreshCommandReady() {
        val ready = gatt != null && writeChar != null && _isConnected.value && postConnectSetupDone
        _commandReady.value = ready
    }

    private fun requestDiscoverServices(g: BluetoothGatt, reason: String) {
        if (servicesDiscoveryRequested) {
            Log.d(tag, "跳过重复 discoverServices ($reason)")
            return
        }
        servicesDiscoveryRequested = true
        val ok = runCatching { g.discoverServices() }.getOrDefault(false)
        Log.i(tag, "discoverServices($reason)=$ok")
        if (!ok) {
            servicesDiscoveryRequested = false
            mainHandler.postDelayed({
                if (_isConnected.value && gatt === g && !servicesDiscoveryRequested) {
                    requestDiscoverServices(g, "retry")
                }
            }, 300)
        }
    }

    private fun finishPostConnectSetup(g: BluetoothGatt) {
        if (postConnectSetupDone) {
            refreshCommandReady()
            return
        }
        postConnectSetupDone = true
        refreshCommandReady()
        _bindStatusMessage.value = if (photoFa12NotifyEnabled) {
            "低功耗蓝牙已连接（识图通道就绪）"
        } else {
            "低功耗蓝牙已连接"
        }
        Log.i(
            tag,
            "通道就绪 write=${writeChar != null} FA12=$photoFa12NotifyEnabled MTU=$currentMtu"
        )
        val mac = g.device?.address
        if (firstBindClassicObserveActive && mac != null) {
            scheduleClassicAclPullForFirstBind(mac, "ble_ready")
        }
        refreshFullyLinked()
        // 连接就绪后主动拉一次固件版本
        mainHandler.postDelayed({ requestFirmwareVersion() }, 400)
    }

    /** 请求眼镜固件版本（0x10 + sub 0x20） */
    fun requestFirmwareVersion() {
        if (!isCommandChannelReady()) {
            Log.w(tag, "获取固件版本跳过：通道未就绪")
            return
        }
        writeCommand(
            packetBuilder.buildFirmwareVersionRequestPacket(nextSeq()),
            "firmware_0x20"
        )
    }

    private fun ensurePhotoDataNotifySubscribed(g: BluetoothGatt, reason: String) {
        if (photoFa12NotifyEnabled && photoDataChar != null) {
            Log.d(tag, "FA12 已订阅，跳过 ($reason)")
            return
        }
        val service = g.getService(PHOTO_SERVICE_UUID) ?: run {
            Log.w(tag, "未找到照片服务 FA10 ($reason)")
            return
        }
        val dataChar = service.getCharacteristic(PHOTO_DATA_UUID)
        val ctrlChar = service.getCharacteristic(PHOTO_CTRL_UUID)
        if (dataChar == null || ctrlChar == null) {
            Log.w(tag, "FA10 缺 FA12/FA11 ($reason)")
            return
        }
        photoDataChar = dataChar
        photoCtrlChar = ctrlChar
        enableNotify(g, dataChar, "FA12_$reason")
    }

    private fun cleanupGattState(keepBound: Boolean) {
        gatt = null
        writeChar = null
        notifyChar = null
        photoCtrlChar = null
        photoDataChar = null
        photoFa12NotifyEnabled = false
        fff2NotifyEnabled = false
        pendingSubscribeFa12AfterFff2 = false
        fa12CccdRetryCount = 0
        servicesDiscoveryRequested = false
        currentMtu = 23
        postConnectSetupDone = false
        _isConnected.value = false
        _isConnecting.value = false
        _commandReady.value = false
        if (!keepBound) {
            _connectedName.value = null
            _connectedMac.value = null
            _firmwareVersion.value = ""
        }
        clearAiPhotoState(keepTerminal = true)
        refreshFullyLinked()
    }

    private fun enableNotify(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        label: String
    ) {
        val setOk = g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: run {
            Log.w(tag, "$label 无 CCCD setNotify=$setOk")
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val writeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        Log.i(tag, "写 $label CCCD setNotify=$setOk writeOk=$writeOk")
    }

    private fun nextSeq(): Byte {
        seq = ((seq.toInt() + 1) and 0xFF).toByte()
        return seq
    }

    private fun writeCommand(packet: ByteArray, reason: String) {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null) {
            Log.w(tag, "写指令失败无特征: $reason")
            return
        }
        Log.d(tag, "写指令 $reason len=${packet.size}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            ch.value = packet
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private fun writePhotoCtrl(payload: ByteArray, reason: String) {
        val g = gatt
        val ch = photoCtrlChar
            ?: g?.getService(PHOTO_SERVICE_UUID)?.getCharacteristic(PHOTO_CTRL_UUID)
        if (g == null || ch == null) {
            Log.w(tag, "写 FA11 失败: $reason")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            ch.value = payload
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
        Log.i(tag, "已写 FA11 ($reason) hex=${payload.joinToString("") { "%02X".format(it) }}")
    }

    private fun dispatchNotify(uuid: UUID, data: ByteArray) {
        when (uuid) {
            PHOTO_DATA_UUID -> handleAiPhotoFa12Packet(data)
            NOTIFY_UUID -> handleFff2Frame(data)
            else -> Unit
        }
    }

    private fun handleFff2Frame(data: ByteArray) {
        if (data.size < 7) return
        if (data[0] != 0x55.toByte() || data[1] != 0xAA.toByte()) return
        val cmdByte = data[3]
        val type = data[4]
        val len = (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0xFF) shl 8)
        val payload = if (data.size >= 7 + len) data.copyOfRange(7, 7 + len) else byteArrayOf()

        when (cmdByte) {
            cmd.getDeviceInfoCmd -> handleDeviceInfoPayload(payload)
            cmd.deviceStatusNotifyCmd -> handleDeviceStatusNotify(payload)
            cmd.aiPhotoStatusCmd, cmd.photoReadyCmd -> handlePhotoStatus(payload)
        }
        if (type == cmd.notify || type == cmd.response) {
            Log.d(tag, "FFF2 cmd=0x${"%02X".format(cmdByte)} len=$len")
        }
    }

    private fun handleDeviceInfoPayload(payload: ByteArray) {
        if (payload.size < 2) return
        when (payload[0]) {
            cmd.subFirmwareInfo -> {
                val len = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
                if (payload.size >= 2 + len && len > 0) {
                    val version = payload.copyOfRange(2, 2 + len)
                        .toString(Charsets.UTF_8)
                        .trim { it <= ' ' || it == '\u0000' }
                    if (version.isNotBlank()) {
                        _firmwareVersion.value = version
                        Log.i(tag, "固件版本(设备上报): $version")
                    }
                } else {
                    Log.w(tag, "固件版本 TLV 长度异常: declaredLen=$len payloadSize=${payload.size}")
                }
            }
        }
    }

    /** 0x11 状态上报中也可能带固件 TLV */
    private fun handleDeviceStatusNotify(payload: ByteArray) {
        var offset = 0
        while (offset + 2 <= payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val len = payload[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > payload.size) break
            if (type == (cmd.subFirmwareInfo.toInt() and 0xFF) && len > 0) {
                val version = payload.copyOfRange(offset + 2, offset + 2 + len)
                    .toString(Charsets.UTF_8)
                    .trim { it <= ' ' || it == '\u0000' }
                if (version.isNotBlank()) {
                    _firmwareVersion.value = version
                    Log.i(tag, "固件版本(状态上报): $version")
                }
            }
            offset += 2 + len
        }
    }

    private fun handlePhotoStatus(payload: ByteArray) {
        if (payload.isEmpty()) return
        when (payload[0].toInt() and 0xFF) {
            0x01 -> { // START
                var fileSize = 0
                if (payload.size >= 5) {
                    fileSize = (payload[1].toInt() and 0xFF) or
                        ((payload[2].toInt() and 0xFF) shl 8) or
                        ((payload[3].toInt() and 0xFF) shl 16) or
                        ((payload[4].toInt() and 0xFF) shl 24)
                }
                onAiPhotoStart(fileSize)
                _aiPhotoBleEvents.tryEmit(
                    AiPhotoBleEvent(
                        status = AiPhotoBleEvent.AiPhotoBleStatus.START,
                        fileSize = fileSize
                    )
                )
            }
            0x02 -> { // SUCCESS
                _aiPhotoBleEvents.tryEmit(
                    AiPhotoBleEvent(status = AiPhotoBleEvent.AiPhotoBleStatus.SUCCESS)
                )
            }
            0x03 -> emitAiFailed("眼镜返回传图失败")
        }
    }

    private fun onAiPhotoStart(fileSize: Int) {
        synchronized(aiPhotoStateLock) {
            if (fileSize <= 0) return
            aiPhotoBleTotalSize = fileSize
            aiPhotoBleBuffer = ByteArray(fileSize)
            aiPhotoBleCovered = BitSet(fileSize)
            aiPhotoAwaitingCrcConfirm = false
            val early = aiPhotoEarlyChunks.toList()
            aiPhotoEarlyChunks.clear()
            for ((offset, chunk) in early) {
                applyChunkLocked(offset, chunk)
            }
        }
        maybeFinishAiPhoto()
    }

    private fun handleAiPhotoFa12Packet(data: ByteArray) {
        if (!aiPhotoCmdSent) return
        if (data.size < 5) return
        val offset = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 24)
        val chunk = data.copyOfRange(4, data.size)
        if (chunk.isEmpty()) return
        synchronized(aiPhotoStateLock) {
            if (aiPhotoBleTotalSize <= 0 || aiPhotoBleBuffer == null) {
                aiPhotoEarlyChunks.add(offset to chunk)
                return
            }
            applyChunkLocked(offset, chunk)
        }
        maybeFinishAiPhoto()
    }

    private fun applyChunkLocked(offset: Int, chunk: ByteArray) {
        val buf = aiPhotoBleBuffer ?: return
        val bits = aiPhotoBleCovered ?: return
        val total = aiPhotoBleTotalSize
        if (offset < 0 || offset >= total) return
        val copyLen = minOf(chunk.size, total - offset)
        System.arraycopy(chunk, 0, buf, offset, copyLen)
        bits.set(offset, offset + copyLen)
        val covered = bits.cardinality()
        val progress = if (total > 0) covered.toFloat() / total else 0f
        _aiPhotoBleEvents.tryEmit(
            AiPhotoBleEvent(
                status = AiPhotoBleEvent.AiPhotoBleStatus.TRANSFER_PROGRESS,
                fileSize = total,
                progress = progress
            )
        )
    }

    private fun maybeFinishAiPhoto() {
        val (full, snapshot, total) = synchronized(aiPhotoStateLock) {
            val bits = aiPhotoBleCovered
            val buf = aiPhotoBleBuffer
            val size = aiPhotoBleTotalSize
            if (bits == null || buf == null || size <= 0 || aiPhotoAwaitingCrcConfirm) {
                return
            }
            val firstClear = bits.nextClearBit(0)
            val isFull = firstClear >= size
            Triple(isFull, if (isFull) buf.copyOf() else null, size)
        }
        if (!full || snapshot == null) return
        val crc = CRC32().also { it.update(snapshot) }.value
        synchronized(aiPhotoStateLock) {
            aiPhotoAwaitingCrcConfirm = true
        }
        emitAiComplete(snapshot, total, aiPhotoBleFileName)
        val payload = ByteArray(5)
        payload[0] = 0x03
        payload[1] = (crc and 0xFFL).toByte()
        payload[2] = ((crc shr 8) and 0xFFL).toByte()
        payload[3] = ((crc shr 16) and 0xFFL).toByte()
        payload[4] = ((crc shr 24) and 0xFFL).toByte()
        writePhotoCtrl(payload, "complete_crc")
    }

    private fun emitAiComplete(image: ByteArray, fileSize: Int, fileName: String) {
        val shouldEmit = synchronized(aiPhotoStateLock) {
            if (aiPhotoLocalDelivered) false else {
                aiPhotoLocalDelivered = true
                true
            }
        }
        if (!shouldEmit) return
        val event = AiPhotoBleEvent(
            status = AiPhotoBleEvent.AiPhotoBleStatus.COMPLETE,
            fileSize = fileSize,
            fileName = fileName,
            progress = 1f,
            imageData = image
        )
        _aiPhotoBleTerminal.value = event
        _aiPhotoBleEvents.tryEmit(event)
    }

    private fun emitAiFailed(message: String) {
        val event = AiPhotoBleEvent(
            status = AiPhotoBleEvent.AiPhotoBleStatus.FAILED,
            errorMessage = message
        )
        _aiPhotoBleTerminal.value = event
        _aiPhotoBleEvents.tryEmit(event)
        clearAiPhotoState(keepTerminal = true)
    }

    private fun clearAiPhotoState(keepTerminal: Boolean) {
        synchronized(aiPhotoStateLock) {
            aiPhotoBleBuffer = null
            aiPhotoBleCovered = null
            aiPhotoBleTotalSize = 0
            aiPhotoBleFileName = ""
            aiPhotoCmdSent = false
            aiPhotoLocalDelivered = false
            aiPhotoAwaitingCrcConfirm = false
            aiPhotoEarlyChunks.clear()
        }
        if (!keepTerminal) {
            _aiPhotoBleTerminal.value = null
        }
    }
}
