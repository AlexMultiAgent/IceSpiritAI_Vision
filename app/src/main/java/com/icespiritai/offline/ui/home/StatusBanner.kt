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
import com.icespiritai.offline.ui.theme.DarkError
import com.icespiritai.offline.ui.theme.DarkOnError
import com.icespiritai.offline.ui.theme.DarkSuccess
import com.icespiritai.offline.ui.theme.DarkWarning
import com.icespiritai.offline.ui.theme.LightError
import com.icespiritai.offline.ui.theme.LightOnError
import com.icespiritai.offline.ui.theme.LightSuccess
import com.icespiritai.offline.ui.theme.LightWarning

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
        StatusBannerKind.Success -> if (isDark) DarkSuccess.copy(alpha = 0.2f) to DarkSuccess else LightSuccess.copy(alpha = 0.2f) to LightSuccess
        StatusBannerKind.Warning -> if (isDark) DarkWarning.copy(alpha = 0.2f) to DarkWarning else LightWarning.copy(alpha = 0.2f) to LightWarning
        StatusBannerKind.Violation -> if (isDark) DarkError.copy(alpha = 0.2f) to DarkError else LightError.copy(alpha = 0.2f) to LightError
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