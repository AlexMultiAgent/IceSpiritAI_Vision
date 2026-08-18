package com.icespiritai.offline.ui.home

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    TabRow(
        selectedTabIndex = visibleTabs.indexOf(selected).coerceAtLeast(0),
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        visibleTabs.forEach { tab ->
            Tab(
                selected = (tab == selected),
                onClick = { if (enabled) onSelect(tab) },
                enabled = enabled || tab == selected,
                text = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}
