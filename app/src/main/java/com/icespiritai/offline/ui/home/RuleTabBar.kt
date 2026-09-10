package com.icespiritai.offline.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icespiritai.offline.R

/**
 * Stable test tags for [RuleTabBar] composables. Declared in **main**
 * source (not test source) because production composables in this file
 * need to attach `Modifier.testTag(...)` directly — test sources are not
 * visible from main in Gradle's source-set split.
 *
 * Per-tab icon testTags follow the codebase convention established by
 * [com.icespiritai.offline.ui.settings.AppearanceSection] (`theme_SYSTEM`,
 * `theme_DARK`, `theme_LIGHT`) — a base constant +
 * a hardcoded `SCREAMING_SNAKE_CASE` transliteration of `RuleTab.name`
 * (`AdSignage` → `AD_SIGNAGE`). Note this is **not** `name.uppercase()`
 * (which would yield `ADSIGNAGE`); the per-enum underscore is intentional
 * to match the convention used elsewhere in this codebase.
 *
 * The constants AND the [leadingIconTag] mapping helper live in this
 * object so adding a 3rd tab is a one-spot change: extend the constants
 * block AND the `when` in [leadingIconTag] in the same place, and
 * production + tests stay locked together.
 */
object RuleTabBarTestTags {
    const val PILL_LEADING_ICON = "ruleTabBar_pill_leading_icon"
    const val PILL_LEADING_ICON_AD_SIGNAGE = "${PILL_LEADING_ICON}_AD_SIGNAGE"
    const val PILL_LEADING_ICON_FOOD_LABELING = "${PILL_LEADING_ICON}_FOOD_LABELING"

    /**
     * Production mapping from a [RuleTab] to its per-pill leading-icon
     * testTag. Mirrors the `PILL_LEADING_ICON_*` constants above —
     * `PillTab` calls this once instead of `when`-ing on the enum in
     * layout code. When adding a new [RuleTab], add both the constant
     * above AND a branch here.
     */
    fun leadingIconTag(tab: RuleTab): String = when (tab) {
        RuleTab.AdSignage -> PILL_LEADING_ICON_AD_SIGNAGE
        RuleTab.FoodLabeling -> PILL_LEADING_ICON_FOOD_LABELING
    }
}

enum class RuleTab(val titleRes: Int, val tabIcon: ImageVector) {
    AdSignage(R.string.tab_ad_law, Icons.Outlined.Verified),
    FoodLabeling(R.string.tab_food_label, Icons.Outlined.LocalDining),
}

/**
 * Soft-color chip tab bar. Each tab is a [Surface] with `RoundedCornerShape(50)`
 * (full pill), `tertiaryContainer` fill when selected and `surfaceVariant`
 * when unselected, with a per-tab leading icon (derived from [RuleTab.tabIcon])
 * and `labelLarge` Medium label text. The soft container contrasts gently with
 * the flat title above, replacing the previous "strong pill" segmented pattern
 * that looked like an isolated button on Idle.
 *
 * Each pill exposes `Role.Tab` semantics via [Modifier.clickable] so
 * [RuleTabBarTest] (which counts `Role.Tab` nodes) and screen readers both
 * keep working. Each pill also exposes `SemanticsProperties.Selected` so
 * tests and a11y tooling can distinguish the active pill from its siblings
 * without inspecting color. Per-tab icon testTags
 * ([RuleTabBarTestTags.PILL_LEADING_ICON_AD_SIGNAGE] /
 * [RuleTabBarTestTags.PILL_LEADING_ICON_FOOD_LABELING]) let tests verify the
 * icon swap (Verified for AdSignage, LocalDining for FoodLabeling).
 *
 * **Visibility contract** ([visibleTabs]):
 * - The caller (typically the ViewModel's `visibleFeatures: StateFlow<Set<RuleTab>>`)
 *   injects which tabs to render. Iterate order = [RuleTab.entries] order
 *   (AdSignage → FoodLabeling), so the food-labeling tab always sits to the
 *   right of the ad-signage tab.
 * - **Required, no default.** The system default everywhere else
 *   (`SettingsRepository.visibleFeatures`, the VM's `initialValue`) is
 *   `RuleTab.entries.toSet()` (both tabs visible). A `setOf(AdSignage)`
 *   default here would silently disable the v0.1.69 dual-tab feature for
 *   any caller that forgets to thread the param — `HomeTopBar` is updated
 *   to pass it through; Task 5 (HomeScreen) will eventually wire
 *   `vm.visibleFeatures.collectAsState().value` from the source of truth.
 * - Empty set → an empty [Row] is rendered (no crash). Callers are responsible
 *   for the "at least one visible" guard (see [com.icespiritai.offline.settings.SettingsViewModel.setFeatureVisible]).
 *
 * **Selection coercion invariant**: if `selected !in visibleTabs` — e.g. the
 * user was on FoodLabeling, hid it via Settings, then returned to Home —
 * the bar coerces `selected` to `RuleTab.entries.first { it in visibleTabs }`
 * (the leftmost visible tab) so at least one pill is always highlighted.
 * Callers do **not** need to coordinate `_currentTab` with `visibleFeatures`:
 * the VM's `setTab` already rejects hidden-tab transitions
 * ([com.icespiritai.offline.IceSpiritVisionViewModel.setTab] returns `false`),
 * so this coercion only fires in the race where Settings hid the *current*
 * tab between VM emission and composable recomposition.
 *
 * **Why `FoodLabeling` enum stays**: it is the canonical "add another
 * visual-discernment domain" template — `FoodLabelRuleMatcher` + domain field
 * + knowledge base + category display all live alongside `AdSignage`. Dropping
 * the enum would erase the v0.1.10 commitment to keep the door open for
 * "广告招牌模式 → 其他视觉判别域" replication (CLAUDE.md §产品方向).
 */
@Composable
fun RuleTabBar(
    visibleTabs: Set<RuleTab>,
    selected: RuleTab,
    onSelect: (RuleTab) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val a11y = stringResource(R.string.tab_switch_desc)
    // Coerce `selected` against the current `visibleTabs`. If the user's
    // current tab was just hidden via Settings, fall back to the leftmost
    // visible tab so the bar always renders at least one highlighted pill.
    // `firstOrNull` returns null only when `visibleTabs` is empty — in that
    // case the forEach below iterates zero times and the row is empty,
    // which is the documented "no crash" behaviour for an empty set.
    val effectiveSelected = if (selected in visibleTabs) selected
        else RuleTab.entries.firstOrNull { it in visibleTabs } ?: selected
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = a11y },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleTab.entries.filter { it in visibleTabs }.forEach { tab ->
            val isSelected = (tab == effectiveSelected)
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
        modifier = Modifier
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { selected = isSelected },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = tab.tabIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(16.dp)
                    .testTag(RuleTabBarTestTags.leadingIconTag(tab)),
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
