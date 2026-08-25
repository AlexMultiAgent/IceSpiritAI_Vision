package com.icespiritai.offline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

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

@Composable
fun RuleTabBar(
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.tab_switch_desc)
    val selectedIndex = visibleTabs.indexOf(selected).coerceAtLeast(0)
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = a11y },
        containerColor = Color.Transparent,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                // Compose BOM 2026.08.00 only exposes the single-arg
                // Modifier.tabIndicatorOffset(position) helper — there is
                // no (position, height) overload, so we render the 3dp
                // indicator via Box to match the spec's intended height.
                val pos = tabPositions[selectedIndex]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = pos.left)
                        .width(pos.width)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        },
    ) {
        visibleTabs.forEach { tab ->
            val isSelected = (tab == selected)
            Tab(
                selected = isSelected,
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled || isSelected,
                text = {
                    Text(
                        text = stringResource(tab.titleRes),
                        style = if (isSelected)
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        else
                            MaterialTheme.typography.bodyLarge,
                    )
                },
            )
        }
    }
}