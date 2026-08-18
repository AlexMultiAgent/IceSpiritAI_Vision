package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatOnError
import com.icespiritai.offline.ui.theme.DarkIceChatPositive
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatOnError
import com.icespiritai.offline.ui.theme.LightIceChatPositive
import com.icespiritai.offline.ui.theme.LightIceChatWarning

enum class StatusBannerKind { Idle, Loading, Success, Warning, Violation }

@Composable
fun StatusBanner(
    kind: StatusBannerKind,
    text: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val (bg, fg) = when (kind) {
        StatusBannerKind.Idle -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Loading -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBannerKind.Success -> if (isDark) DarkIceChatPositive.copy(alpha = 0.2f) to DarkIceChatPositive else LightIceChatPositive.copy(alpha = 0.2f) to LightIceChatPositive
        StatusBannerKind.Warning -> if (isDark) DarkIceChatWarning.copy(alpha = 0.2f) to DarkIceChatWarning else LightIceChatWarning.copy(alpha = 0.2f) to LightIceChatWarning
        StatusBannerKind.Violation -> if (isDark) DarkIceChatError.copy(alpha = 0.2f) to DarkIceChatError else LightIceChatError.copy(alpha = 0.2f) to LightIceChatError
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}