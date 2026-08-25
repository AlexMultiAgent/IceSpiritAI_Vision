package com.icespiritai.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R
import com.icespiritai.offline.domain.Severity
import com.icespiritai.offline.ui.theme.DarkIceChatError
import com.icespiritai.offline.ui.theme.DarkIceChatOnError
import com.icespiritai.offline.ui.theme.DarkIceChatOnPositive
import com.icespiritai.offline.ui.theme.DarkIceChatOnWarning
import com.icespiritai.offline.ui.theme.DarkIceChatPositive
import com.icespiritai.offline.ui.theme.DarkIceChatWarning
import com.icespiritai.offline.ui.theme.LightIceChatError
import com.icespiritai.offline.ui.theme.LightIceChatOnError
import com.icespiritai.offline.ui.theme.LightIceChatOnPositive
import com.icespiritai.offline.ui.theme.LightIceChatOnWarning
import com.icespiritai.offline.ui.theme.LightIceChatPositive
import com.icespiritai.offline.ui.theme.LightIceChatWarning

@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    val (bg: Color, fg: Color) = when (severity) {
        Severity.Info -> resolveSeverityColors(DarkIceChatWarning, DarkIceChatOnWarning, LightIceChatWarning, LightIceChatOnWarning)
        Severity.Warning -> resolveSeverityColors(DarkIceChatWarning, DarkIceChatOnWarning, LightIceChatWarning, LightIceChatOnWarning)
        Severity.Violation -> resolveSeverityColors(DarkIceChatError, DarkIceChatOnError, LightIceChatError, LightIceChatOnError)
        Severity.Positive -> resolveSeverityColors(DarkIceChatPositive, DarkIceChatOnPositive, LightIceChatPositive, LightIceChatOnPositive)
    }
    val label = when (severity) {
        Severity.Info -> stringResource(R.string.hit_severity_info)
        Severity.Warning -> stringResource(R.string.hit_severity_warning)
        Severity.Violation -> stringResource(R.string.hit_severity_violation)
        Severity.Positive -> stringResource(R.string.hit_severity_positive)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(PaddingValues(horizontal = 8.dp, vertical = 2.dp)),
    )
}

@Composable
private fun resolveSeverityColors(
    darkBg: Color,
    darkFg: Color,
    lightBg: Color,
    lightFg: Color,
): Pair<Color, Color> {
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    return if (isDark) darkBg to darkFg else lightBg to lightFg
}