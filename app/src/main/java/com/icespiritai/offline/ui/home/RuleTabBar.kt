package com.icespiritai.offline.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

/**
 * Stable test tags for [RuleTabBar] composables. Declared in **main**
 * source (not test source) because production composables in this file
 * need to attach `Modifier.testTag(...)` directly — test sources are not
 * visible from main in Gradle's source-set split.
 */
object RuleTabBarTestTags {
    const val PILL_LEADING_ICON = "ruleTabBar_pill_leading_icon"
}

enum class RuleTab(val titleRes: Int) {
    AdSignage(R.string.tab_ad_law),
    FoodLabeling(R.string.tab_food_label),
}

/**
 * 食品标识 tab 入口当前**不向用户暴露**,仅保留 [RuleTab.FoodLabeling] enum
 * 项 + 完整代码路径(规则 / 加载器 / ViewModel 路由 / 测试 / KB markdown),以
 * 保持"广告招牌模式 → 食品标识模式"的可复制性。当前 `visibleTabs` 列表
 * 只渲染 [RuleTab.AdSignage],后续打磨 ad_signage 域规则成熟后,需启用
 * 食品标识时改回 `RuleTab.entries.toList()` 即可。
 *
 * 不要删除 `FoodLabeling` enum 项:那会让整套可复用模板(双 matcher /
 * domain 字段 / 知识库 / 类别显示 / 证据包导出)一并丢失,等于砍掉了
 * "成熟后可最大限度能套用扩展到食品标识 等其他视觉判别功能"这条路。
 */
private val visibleTabs: List<RuleTab> = listOf(RuleTab.AdSignage)

/**
 * Soft-color chip tab bar. Each tab is a [Surface] with `RoundedCornerShape(50)`
 * (full pill), `tertiaryContainer` fill when selected and `surfaceVariant`
 * when unselected, with a leading [Icons.Outlined.Verified] icon and
 * `labelLarge` Medium label text. The soft container contrasts gently with
 * the flat title above, replacing the previous "strong pill" segmented
 * pattern that looked like an isolated button on Idle.
 *
 * Each pill exposes `Role.Tab` semantics via [Modifier.clickable] so
 * [RuleTabBarTest] (which counts `Role.Tab` nodes) and screen readers
 * both keep working. The [RuleTabBarTestTags.PILL_LEADING_ICON] testTag
 * lets tests verify the Verified icon renders.
 */
@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.tab_switch_desc)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = a11y },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleTabs.forEach { tab ->
            val isSelected = (tab == selected)
            PillTab(
                tab = tab,
                isSelected = isSelected,
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled || isSelected,
            )
        }
    }
}

@Composable
private fun PillTab(
    tab: RuleTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(
            enabled = enabled,
            role = Role.Tab,
            onClick = onClick,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(16.dp)
                    .testTag(RuleTabBarTestTags.PILL_LEADING_ICON),
            )
            Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}