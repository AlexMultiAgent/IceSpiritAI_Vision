package com.aiglass.zhangwen.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth
import com.aiglass.zhangwen.config.BoundDeviceStore
import com.aiglass.zhangwen.ui.theme.GovBlue
import com.aiglass.zhangwen.ui.theme.GovTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bt = remember { GlassesBluetooth.get() }
    val connected by bt.isConnected.collectAsState()
    val ready by bt.isCommandChannelReady.collectAsState()
    val mac by bt.connectedDeviceMac.collectAsState()
    val name by bt.connectedDeviceName.collectAsState()
    val firmware by bt.firmwareVersion.collectAsState()

    val displayMac = mac?.takeIf { it.isNotBlank() }
        ?: BoundDeviceStore.getMac(context)
        ?: "未绑定"
    val displayName = name?.takeIf { it.isNotBlank() }
        ?: BoundDeviceStore.getName(context)
        ?: "—"
    val displayFw = firmware.ifBlank {
        if (connected) "获取中…" else "未连接，无法获取"
    }

    LaunchedEffect(connected, ready) {
        if (connected && ready) {
            bt.requestFirmwareVersion()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("眼镜信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(
                "显示当前绑定眼镜的固件版本与 MAC 地址。",
                color = GovTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            ListItem(
                headlineContent = { Text("设备名称") },
                supportingContent = { Text(displayName) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("眼镜 MAC 地址") },
                supportingContent = { Text(displayMac) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("眼镜固件版本") },
                supportingContent = { Text(displayFw) }
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { bt.requestFirmwareVersion() },
                enabled = connected && ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("刷新固件版本")
            }
            if (!connected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "请先绑定并连接眼镜后再刷新固件版本。",
                    color = GovTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
