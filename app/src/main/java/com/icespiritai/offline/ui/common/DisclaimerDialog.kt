package com.icespiritai.offline.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

/**
 * 首次启动免责声明对话框(spec §6.4)。
 *
 * - AlertDialog (Material3),surfaceContainerHigh 背景,16dp corner
 * - title: 使用提示(titleLarge,经 MaterialTheme → EditorialFontFamily 透传)
 *   v0.1.67: 之前 KDoc 写「Source Han Serif SC Bold 经 MaterialTheme 透传」是空话 —
 *   Type.kt 11 个 TextStyle 全部无 fontFamily,实际渲染 Roboto。v0.1.67 接入
 *   EditorialFontFamily (= FontFamily.Serif),给 Latin 衬线 + CJK 走系统 CJK
 *   fallback 的 Editorial 观感;真正的 Source Han Serif SC OTF 留待后续 build
 *   pipeline 单独 step 投放 res/font/source_han_serif_sc.ttf。
 * - body: 3 段 bodyMedium
 * - 唯一 positive button "我了解",click → onAcknowledge()
 * - setCancelable(false) 通过 properties.dismissOnBackPress / dismissOnClickOutside 实现
 */
@Composable
fun DisclaimerDialog(
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* no-op; setCancelable(false) */ },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(
                    text = stringResource(R.string.tts_disclaimer_ack),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.tts_disclaimer_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tts_disclaimer_body_3),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}