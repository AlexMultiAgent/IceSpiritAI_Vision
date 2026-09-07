package com.icespiritai.offline.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icespiritai.offline.R
import com.icespiritai.offline.tts.TtsState

/**
 * Compact home header: a tight Column of { title row + tab row } on a
 * single transparent [Surface], no Material 3 [TopAppBar] involved.
 *
 * v0.1.X+4: 增加 TTS 朗读按钮(spec §6.1)。视觉矩阵:
 *   - Disabled → 不渲染
 *   - Idle/非 Complete → 灰禁,无 accent border
 *   - InitFailed → 灰禁 + "朗读功能不可用" a11y
 *   - Complete + Idle → VolumeUp + accent 1px border + "朗读识别结果;AI 识别仅供参考" a11y
 *   - Complete + Speaking → Stop + accent 1px border + "停止朗读" a11y
 *
 * Crossfade 用 220ms(Phase 3 §6.3 motion token)。
 *
 * Settings gear 仍在 CenterEnd,朗读按钮紧贴其左侧 8dp gap(共 Row 在 Box 内)。
 */
@Composable
fun HomeTopBar(
    selectedTab: RuleTab,
    onSelectTab: (RuleTab) -> Unit,
    tabEnabled: Boolean,
    onOpenSettings: () -> Unit,
    ttsState: TtsState = TtsState.Disabled,
    isAnalysisComplete: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TtsIconButton(
                        ttsState = ttsState,
                        isAnalysisComplete = isAnalysisComplete,
                        onSpeakToggle = onSpeakToggle,
                    )
                    val a11ySettings = stringResource(R.string.settings_button_desc)
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics { contentDescription = a11ySettings },
                    ) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    }
                }
            }
            RuleTabBar(selected = selectedTab, onSelect = onSelectTab, enabled = tabEnabled)
        }
    }
}

@Composable
private fun TtsIconButton(
    ttsState: TtsState,
    isAnalysisComplete: Boolean,
    onSpeakToggle: () -> Unit,
) {
    if (ttsState is TtsState.Disabled) return  // 不渲染

    val (icon, a11y, enabled, accentBorder) = when {
        ttsState is TtsState.InitFailed -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_init_failed_desc),
            false, false,
        )
        ttsState is TtsState.Speaking -> Quadruple(
            Icons.Default.Stop,
            stringResource(R.string.tts_button_stop_desc),
            true, true,
        )
        isAnalysisComplete -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_desc),
            true, true,
        )
        else -> Quadruple(
            Icons.AutoMirrored.Filled.VolumeUp,
            stringResource(R.string.tts_button_disabled_desc),
            false, false,
        )
    }

    IconButton(
        onClick = onSpeakToggle,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (accentBorder) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) else Modifier
            )
            .semantics { contentDescription = a11y },
    ) {
        Crossfade(
            targetState = ttsState is TtsState.Speaking,
            animationSpec = tween(durationMillis = 220),
            label = "tts-icon-crossfade",
        ) { isSpeaking ->
            Icon(
                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
