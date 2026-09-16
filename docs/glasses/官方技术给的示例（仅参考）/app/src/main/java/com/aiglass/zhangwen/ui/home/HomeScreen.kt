package com.aiglass.zhangwen.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiglass.zhangwen.R
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth
import com.aiglass.zhangwen.service.BluetoothLeService
import com.aiglass.zhangwen.service.PhotoCaptureService
import com.aiglass.zhangwen.service.PhotoCaptureState
import com.aiglass.zhangwen.ui.theme.GovBlue
import com.aiglass.zhangwen.ui.theme.GovSuccess
import com.aiglass.zhangwen.ui.theme.GovTextSecondary
import com.aiglass.zhangwen.ui.theme.GovWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBindClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onIdentifyResult: (photoPath: String, resultText: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bt = remember { GlassesBluetooth.get() }
    val photoService = remember { PhotoCaptureService(context.applicationContext) }

    val connected by bt.isConnected.collectAsState()
    val connecting by bt.isConnecting.collectAsState()
    val ready by bt.isCommandChannelReady.collectAsState()
    val deviceName by bt.connectedDeviceName.collectAsState()
    val captureState by photoService.captureState.collectAsState()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val intent = Intent(context, BluetoothLeService::class.java)
        runCatching { context.startForegroundService(intent) }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        onDispose {
            runCatching { context.unbindService(conn) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = connectionLabel(connected, connecting, ready, deviceName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                connected && ready -> GovSuccess
                                connecting -> GovWarning
                                else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GovBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBindClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (connected) "管理眼镜设备" else "绑定眼镜设备")
            }

            PrimaryActionButton(
                title = "普通拍照",
                subtitle = "仅控制眼镜快门拍照，不回传、不识图",
                icon = Icons.Outlined.CameraAlt,
                enabled = !busy && connected && ready,
                onClick = {
                    scope.launch {
                        busy = true
                        statusMessage = "正在下发拍照指令…"
                        photoService.resetState()
                        val ok = photoService.captureOnly()
                        busy = false
                        val state = photoService.captureState.value
                        statusMessage = when {
                            ok && state is PhotoCaptureState.Success ->
                                state.message.ifBlank { "已控制眼镜拍照" }
                            state is PhotoCaptureState.Error -> state.message
                            else -> "拍照失败"
                        }
                    }
                }
            )

            PrimaryActionButton(
                title = "拍照识物",
                subtitle = "BLE 传图后调用视觉模型识别",
                icon = Icons.Outlined.TravelExplore,
                enabled = !busy && connected && ready,
                onClick = {
                    scope.launch {
                        busy = true
                        statusMessage = "正在识物…"
                        photoService.resetState()
                        val ok = photoService.captureAndRecognize()
                        busy = false
                        val state = photoService.captureState.value
                        if (ok && state is PhotoCaptureState.Success) {
                            statusMessage = null
                            onIdentifyResult(
                                state.photoFile?.absolutePath.orEmpty(),
                                state.aiResult
                            )
                        } else {
                            statusMessage =
                                (state as? PhotoCaptureState.Error)?.message ?: "识物失败"
                        }
                    }
                }
            )

            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = when (captureState) {
                            PhotoCaptureState.SendingCommand -> "正在下发拍照指令…"
                            PhotoCaptureState.WaitingForPhoto -> "等待眼镜回传图片…"
                            PhotoCaptureState.Recognizing -> "正在识别…"
                            else -> statusMessage ?: "处理中…"
                        },
                        color = GovTextSecondary
                    )
                }
            } else if (!statusMessage.isNullOrBlank()) {
                Text(text = statusMessage!!, color = GovTextSecondary)
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GovBlue)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private fun connectionLabel(
    connected: Boolean,
    connecting: Boolean,
    ready: Boolean,
    name: String?
): String = when {
    connecting -> "正在连接…"
    connected && ready -> "已连接 · ${name ?: "眼镜"}"
    connected -> "已连接（通道初始化中）· ${name ?: "眼镜"}"
    else -> "未连接眼镜"
}
