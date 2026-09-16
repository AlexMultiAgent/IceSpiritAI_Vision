package com.aiglass.zhangwen.ui.bind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth
import com.aiglass.zhangwen.ui.theme.GovBlue
import com.aiglass.zhangwen.ui.theme.GovSuccess
import com.aiglass.zhangwen.ui.theme.GovTextSecondary

/**
 * 绑定页：对齐原项目「App 内完成」流程。
 * 点绑定 → 未配对先系统配对框 → BLE GATT → 拉经典 ACL。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindDeviceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bt = remember { GlassesBluetooth.get() }
    val scanning by bt.isScanning.collectAsState()
    val devices by bt.discoveredDevices.collectAsState()
    val connected by bt.isConnected.collectAsState()
    val connecting by bt.isConnecting.collectAsState()
    val classicBonding by bt.isClassicBonding.collectAsState()
    val classicConnecting by bt.isClassicConnecting.collectAsState()
    val classicBonded by bt.isClassicBonded.collectAsState()
    val classicConnected by bt.isClassicConnected.collectAsState()
    val fullyLinked by bt.isFullyLinked.collectAsState()
    val deviceName by bt.connectedDeviceName.collectAsState()
    val bindStatus by bt.bindStatusMessage.collectAsState()
    var permissionHint by remember { mutableStateOf<String?>(null) }

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    fun hasAllPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            permissionHint = null
            bt.refreshBluetoothState()
            bt.startScan()
        } else {
            permissionHint = "需要蓝牙与定位相关权限才能扫描眼镜"
        }
    }

    val busy = connecting || classicBonding || classicConnecting

    DisposableEffect(Unit) {
        onDispose { bt.stopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("绑定眼镜") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (busy) {
                            bt.endFirstBindClassicConnect("pairing_back")
                            bt.disconnect(keepBound = true)
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GovBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            InAppBindGuideBanner(
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!hasAllPermissions()) {
                            launcher.launch(permissions)
                        } else {
                            bt.refreshBluetoothState()
                            bt.startScan()
                        }
                    },
                    enabled = !scanning && !busy
                ) { Text(if (scanning) "扫描中…" else "开始扫描") }
                OutlinedButton(onClick = { bt.stopScan() }, enabled = scanning) {
                    Text("停止")
                }
            }

            if (connected || fullyLinked) {
                Spacer(Modifier.height(12.dp))
                Text(
                    buildString {
                        append("当前：")
                        append(deviceName ?: "眼镜")
                        when {
                            fullyLinked -> append("（已完成绑定）")
                            connected && classicBonded -> append("（BLE 已连 · 经典已配对）")
                            connected -> append("（BLE 已连）")
                        }
                    },
                    color = GovSuccess
                )
                OutlinedButton(
                    onClick = { bt.unbind() },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("解绑并断开") }
            }

            if (busy || !bindStatus.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = bindStatus
                        ?: when {
                            classicBonding -> "正在配对音频通道（蓝牙）…"
                            connecting -> "正在连接低功耗蓝牙…"
                            classicConnecting -> "正在配对音频通道（蓝牙）…"
                            else -> "处理中…"
                        },
                    color = GovTextSecondary
                )
            }
            permissionHint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Text("发现的设备", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices, key = { it.address }) { device ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                bt.stopScan()
                                bt.bindDevice(device.address, device.name)
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${device.address}  ·  RSSI ${device.rssi}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GovTextSecondary
                                )
                                Text(
                                    if (device.classicBonded) "经典蓝牙已配对"
                                    else "点击绑定（App 内完成 BLE + 经典）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (device.classicBonded) GovSuccess else GovBlue
                                )
                            }
                            Button(
                                onClick = {
                                    bt.stopScan()
                                    bt.bindDevice(device.address, device.name)
                                },
                                enabled = !busy
                            ) { Text("绑定") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InAppBindGuideBanner(onOpenBluetoothSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GovBlue.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = GovBlue)
                Text(
                    "在 App 内绑定",
                    style = MaterialTheme.typography.titleMedium,
                    color = GovBlue
                )
            }
            Text(
                "在下方找到眼镜后点击「绑定」，App 将为您建立 BLE 与经典蓝牙连接。",
                style = MaterialTheme.typography.bodyMedium,
                color = GovTextSecondary
            )
            OutlinedButton(onClick = onOpenBluetoothSettings) {
                Text("打开系统蓝牙设置")
            }
        }
    }
}
