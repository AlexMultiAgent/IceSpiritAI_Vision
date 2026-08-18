# 冰灵锐目 — 冰灵家族(冰灵慧语)主题对齐 设计

- **状态**:Draft
- **创建日期**:2026-08-18
- **作者**:Claude(经 user 审批)
- **影响范围**:`app/src/main/java/com/icespiritai/offline/ui/theme/*`、`app/src/main/res/values/strings.xml`

---

## 1. 背景

冰灵锐目(IceSpiritAI_Vision)主题原本走自有 slate 系(`#0F172A` / `#E2E8F0`)+ 三态 `ThemeMode`(SYSTEM / DARK / LIGHT)。三态 label 是业务向文案:"深色(现场) / 浅色(归档) / 跟随系统"。

冰灵慧语(IceSpiritAI_Chat)2026-08-18 完成了 `textSize` 与 `cornerRadius` 收敛,并定下了家族视觉 token 系统(`ice_chat_*` 调色板 + `ice_radius_*` 圆角 + `ice_padding_*` 间距)。ThemeMode label 是诗意向:"深色雪夜 / 浅色冰月 / 跟随系统"(注:慧语 `ThemeMode.kt` 字面量与 `strings.xml` 一处不一致是慧语自身的 bug,本设计不动慧语)。

**目标**:让冰灵锐目视觉风格与冰灵慧语同家族,用户跨项目使用认知一致。

## 2. 决策记录

| 决策 | 选择 | 备选 |
|---|---|---|
| 对齐深度 | **Variant B**(重命名 + 色板对齐) | A(只重命名)/ C(再加 dimens/typography) |
| ThemeMode label | **跟 strings.xml 用「冰月」** | 跟 ThemeMode.kt 字面量"水月" / 另选新名 |
| 暗色 ice_* 来源 | **Vision 推导一套 dark ice_*** | 走 Material3 dark 默认 / 保留现有 slate |
| Color val 前缀 | **`IceChat*`(跟慧语 1:1 对齐)** | `IceVision*` / `IceXxx*`(保留前缀) |
| Corner radius token | **引入 `ice_radius_*`** | 本次不动 shape |
| 字符串 key 前缀 | **不动**(`settings_appearance_*` 保留) | 改为 `settings_theme_*` 对齐慧语 |
| ThemeMode 枚举名 | **不动**(`SYSTEM/LIGHT/DARK`) | — |
| 品牌名 / 类前缀 | **不动**(`IceSpiritVisionActivity` / `Theme.IceSpiritOffline`) | — |

## 3. 设计

### 3.1 Color token(`app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt`)

val 名按慧语 `ice_chat_*` 1:1 对齐,hex 直接照抄。`D` / `L` 前缀保留以维持现有 Color.kt 风格。

| 新 val | Material3 slot | 对齐慧语 token | light | dark |
|---|---|---|---|---|
| `IceChatBg(D/L)` | `background` | `ice_chat_bg` | `#F4F8FB` | `#08131B` |
| `IceChatPanel(D/L)` | `surface` | `ice_chat_panel` | `#FFFFFF` | `#11212C` |
| `IceChatPanelSoft(D/L)` | `surfaceVariant` | `ice_chat_panel_soft` | `#EAF2F7` | `#1A2D3A` |
| `IceChatPanelStrong(D/L)` | `surfaceContainerHigh` | `ice_chat_panel_strong` | `#D6E2EC` | `#243748` |
| `IceChatOnBg(D/L)` | `onSurface` | `ice_chat_on_bg` | `#0B1E26` | `#E0F0F8` |
| `IceChatOnBgMuted(D/L)` | `onSurfaceVariant` | `ice_chat_on_bg_muted` | `#5A6E78` | `#7A95A3` |
| `IceChatOnBgSubtle(D/L)` | (扩展 slot) | `ice_chat_on_bg_subtle` | `#9DA9B0` | `#4A5C66` |
| `IceChatOnBgDisabled(D/L)` | (扩展 slot) | `ice_chat_on_bg_disabled` | `#9DA9B0` | `#4A5C66` |
| `IceChatOnBgPlaceholder(D/L)` | (扩展 slot) | `ice_chat_on_bg_placeholder` | `#A8B4BB` | `#6A7C86` |
| `IceChatAccent(D/L)` | `primary` | `ice_chat_accent` | `#1F3A52` | `#A8C0D0` |
| `IceChatAccentSecondary(D/L)` | `secondary` | `ice_chat_accent_secondary` | `#5A7090` | `#7DA4BD` |
| `IceChatOnAccent(D/L)` | `onPrimary` | `ice_chat_send_text` | `#FFFFFF` | `#08131B` |
| `IceChatDivider(D/L)` | `outline` | `ice_chat_divider` | `#141F3A52`(新 accent,α 8%) | `#264FC0E8`(α 15%) |
| `IceChatWarning(D/L)` | (Severity 严重度) | `ice_warning` | `#B04030` | `#E08570` |
| `IceChatOnWarning(D/L)` | (Severity 严重度) | (派生,白/暗墨) | `#FFFFFF` | `#08131B` |
| `IceChatPositive(D/L)` | (StatusBanner Success) | `ice_positive` | `#2C8A6B` | `#5FC2A0` |
| `IceChatError(D/L)` | `error` | `ice_error` | `#D32F2F` | `#FFFF6B6B` |
| `IceChatOnError(D/L)` | `onError` | `ice_on_error` | `#FFFFFF` | `#08131B` |

**取舍说明:**

- `surfaceContainerHigh` 是 Material3 1.2+ 引入 slot,`HitCard.kt` 已用,保留
- `secondaryContainer` / `tertiaryContainer` / `primaryContainer` / `errorContainer` 不直接定义,让 Material3 从 surface/onSurface 默认派生
- 慧语 `ice_chat_composer_bg` / `ice_chat_assistant_stroke` / `ice_chat_chat_bubble_*` 是 chat 专用,Vision 跳过
- `IceChatOnBgSubtle/Disabled/Placeholder` 在慧语是 2026-08-18 新增的"扩展 slot",Vision 一并引入,后续视觉若用到可直接调
- `IceChatWarning` / `IceChatOnWarning` / `IceChatPositive` 是 Severity 严重度(StatusBanner Success / Warning / Violation + SeverityBadge Info / Warning / Violation)直接 val 引用所需的语义色,**不进 Material3 colorScheme**,作为独立 token 暴露给业务代码
- `IceChatDivider` light hex 用 `#141F3A52`(新 accent `ice_chat_accent = #1F3A52`,α 8%)而非 spec 旧表里的 `#140F8AB8`(旧 accent `#0F8AB8`)。两者 RGB 不同 hue,选择新 accent 以保证 divider 与 accent 系调色板一致

### 3.2 Theme.kt(`app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt`)

`DarkScheme` / `LightScheme` 的 `darkColorScheme(...)` / `lightColorScheme(...)` 构造点 val 引用同步改名。函数 `IceSpiritVisionTheme(themeMode, content)` 不动,`ThemeMode.toDarkTheme()` 不动。

新增 `shapes = Shapes(...)` 字段(详见 §3.3)。

### 3.3 Shape(`app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt`,新文件)

```kotlin
package com.icespiritai.offline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val IceRadiusCard: Dp = 12.dp
val IceRadiusChip: Dp = 16.dp
val IceRadiusDialog: Dp = 20.dp
val IceRadiusPill: Dp = 24.dp

internal val IceSpiritShapes = Shapes(
    extraSmall = RoundedCornerShape(IceRadiusChip),   // Chip
    small      = RoundedCornerShape(IceRadiusCard),
    medium     = RoundedCornerShape(IceRadiusCard),
    large      = RoundedCornerShape(IceRadiusDialog),
    extraLarge = RoundedCornerShape(IceRadiusPill),
)
```

`IceSpiritVisionTheme` 内 `MaterialTheme(colorScheme = ..., shapes = IceSpiritShapes, typography = IceSpiritTypography, content = content)`。

`HitCard.kt` 若硬编码 dp,改用 `MaterialTheme.shapes.medium` 自动跟随,或显式用 `IceRadiusCard`。

### 3.4 ThemeMode label(`app/src/main/res/values/strings.xml`)

| key | 旧值 | 新值 |
|---|---|---|
| `settings_appearance_system` | `跟随系统` | `跟随系统`(不动) |
| `settings_appearance_light` | `浅色` | `浅色冰月` |
| `settings_appearance_dark` | `深色` | `深色雪夜` |

`key` 不动(Vision 现有 `settings_appearance_*` 前缀;改 key 会牵动 `SettingsRepository` 持久化反序列化路径,scope 外)。`ThemeMode.kt` 枚举名 `SYSTEM/LIGHT/DARK` 不动,Vision 通过 `stringResource(R.string.settings_appearance_*)` 渲染 label,天然规避慧语 "水月 vs 冰月" 自相矛盾 bug。

## 4. 影响范围

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/icespiritai/offline/ui/theme/Color.kt` | 30 个 val 改名 + 30 个 hex 重写(15 个 token × light/dark) |
| `app/src/main/java/com/icespiritai/offline/ui/theme/Theme.kt` | `darkColorScheme(...)` / `lightColorScheme(...)` 内 30 个 val 引用同步改名 + 加 `shapes = IceSpiritShapes` |
| `app/src/main/java/com/icespiritai/offline/ui/theme/Shape.kt` | 新建,4 个 `IceRadius*` 常量 + `IceSpiritShapes` |
| `app/src/main/res/values/strings.xml` | `settings_appearance_light/dark` 两条值 |

**不需要改的文件**(Material3 colorScheme 键名不变,业务代码 0 行改动):
- `SeverityBadge.kt` / `HitCard.kt` / `StatusBanner.kt` / `HighlightOverlay.kt` / `IceSpiritNavHost.kt` / `HomeScreen.kt` / `ResultPanel.kt` / `UpdateSection.kt`(全部走 `MaterialTheme.colorScheme.{X}`,值自动传导)
- `AppearanceSection.kt`(走 `stringResource(R.string.settings_appearance_*)`,key 不动)
- `ThemeMode.kt`(枚举名 / toNightMode() 不动)

## 5. WCAG 自检

| 组合 | 对比度 | 标准 |
|---|---|---|
| `IceChatError(#D32F2F)` on `IceChatBg(#F4F8FB)` light | ~4.83:1 | AA pass(≥4.5) |
| `IceChatError(#FFFF6B6B)` on `IceChatBg(#08131B)` dark | ~5.4:1 | AA pass(慧语 2026-08-18 theme hardening 已自算) |
| `IceChatOnBg(#0B1E26)` on `IceChatBg(#F4F8FB)` light | ~14.6:1 | AAA pass |
| `IceChatOnBg(#E0F0F8)` on `IceChatBg(#08131B)` dark | ~14.5:1 | AAA pass |

其余组合(secondaryContainer / tertiaryContainer 等派生值)由 Material3 默认保证,不在本设计内手动调。

## 6. 验证

1. **构建**:`./gradlew.bat assembleDebug -PmodelProfile=shell` 通过
2. **单元测试**:`./gradlew.bat testDebugUnitTest` 通过(Compose 测试断言不依赖具体颜色,值变更不影响)
3. **视觉门禁**(用户现场过):
   - 三态 ThemeMode 各过一遍(系统跟随 / 强制深 / 强制浅)
   - 关键屏清单:`HomeScreen` 顶栏 / `StatusBanner` / `HitCard` 严重度三色 / `ResultPanel` 错误文字 / `UpdateSection` 四种 container / `SettingsScreen` 三态 label
   - 关键对比:`IceChatError` 红、`IceChatAccent` 深墨、`IceChatPanelSoft` 浅灰在亮 / 暗下都肉眼可读
4. **跨项目对照**:同款截图(`HomeScreen` 主屏)与冰灵慧语视觉风格肉眼一致
5. **回归门禁**:第 1 + 2 都过即可

## 7. 风险与权衡

| 风险 | 缓释 |
|---|---|
| Vision 现有 slate 系(`#0F172A` / `#020617`)用户已习惯,改成 ice 系(背景 `#F4F8FB` / `#08131B`)视觉变化明显,需要用户现场确认 | §6 视觉门禁必走,且文档钉 WCAG 对比度数据 |
| `surfaceContainerHigh` 是 Material3 1.2+ slot,旧 Compose 版本不支持 | build-stack 锁 Compose Material3 1.2+,确认 baseline `docs/knowledge/build-stack-2026-08.md` |
| `HitCard.kt` 若硬编码 shape dp,改 shape 时漏掉 | §4 列出,实施时单独 grep `RoundedCornerShape` / `dp` 调用核对 |
| 慧语 ThemeMode.kt / strings.xml 的"水月 vs 冰月" bug 不在本设计 scope | 不动慧语;在 Vision 上天然规避 |

## 8. 后续(Phase 2+,不属本 spec)

- 慧语 ThemeMode.kt 与 strings.xml 的「水月 vs 冰月」统一(应另提 issue / spec)
- 把 Vision 的 Color.kt token 收敛到单一 `values/colors.xml` + Compose 读 XML(若要硬复刻慧语 XML 资源结构;本设计无必要)
- Vision 引入 `ice_padding_*` spacing token(慧语已在 dimens),如需 Compose Dp 常量另起 spec

---

## 9. Spec 自审

1. **Placeholder 扫描**:无 TBD / TODO,所有 token / hex / shape 已给定
2. **内部一致性**:§3.1 token 表 ↔ §3.2 Theme.kt 引用 ↔ §4 改动表 ↔ §5 WCAG 自检 ↔ §6 验证清单 五处口径一致
3. **范围**:单 PR 可完成(改 4 个文件,新增 1 个文件),不需分解
4. **歧义**:「保持」类表述(SettingsRepository 持久化 key)明确指 `settings_appearance_*` 不动;container slot 派生规则指 Material3 默认;shape 与现有 `RoundedCornerShape` 关系指「硬编码改用 IceRadius* 或 MaterialTheme.shapes」

---

## 10. 验收 Gate

- [ ] Color.kt 30 val 改名 + hex 与 §3.1 表逐行核对
- [ ] Theme.kt `darkColorScheme` / `lightColorScheme` 30 val 引用同步
- [ ] Shape.kt 新建,`IceSpiritShapes` 接到 `IceSpiritVisionTheme`
- [ ] strings.xml `settings_appearance_light/dark` 值替换
- [ ] `assembleDebug -PmodelProfile=shell` 通过
- [ ] `testDebugUnitTest` 通过
- [ ] 视觉门禁(三态 × 关键屏)用户现场过
- [ ] 跨项目对照(与慧语同屏肉眼一致)用户现场过