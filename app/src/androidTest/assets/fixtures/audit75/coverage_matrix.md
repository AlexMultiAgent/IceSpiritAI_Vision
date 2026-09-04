# 违规案例 audit75 — v0.1.57 新规则验证 覆盖矩阵

> 生成时间:2026-09-04(基于真机 OCR + AdSignageRuleMatcher v0.1.58 实际命中)
> 真机:华为 nova 6 ANN-AN00 (AGQV023313008161, SDK 35)
> 规则库版本:v0.1.58(175 条,refresh 同 v0.1.57)
> fixture 数:4 + coverage_matrix.md

**真机命中汇总**:**ANY_HIT=4/4**(4 张图均命中 ≥1 条规则,达阈值),**TARGET_RULE_HIT=0/4**(0 张图命中 4 条 v0.1.57 新规则 — **存在规则库 gap,见 §3**)。

**性能**:
- cold_ms=2638(1 张图,模型预热)
- warm_total_ms=7103, warm_avg_ms=2367(3 张图均耗时)
- 共 4 张图总耗时 ~10s

## §1 元数据 / 目标规则

audit dir:`app/src/androidTest/assets/fixtures/audit75/`

**4 条 v0.1.57 新规则**(本次 audit75 验证目标):

| 规则 ID | 类别 | 严重度 | 关键词空间 |
|---|---|---|---|
| `ad_signage_art9_citation_radish` | absolute | Violation | 某市第一 / 本省第一 / 某县第一 / 某区第一 / 全城第一 / 全市第一 / 全区第一 / 全县第一 / 某省销量第一 |
| `ad_signage_signage_party_leader_commercial` | signage | Violation | 主席同款 / 主席推荐 / 主席代言 / 总理同款 / 领导人形象 / 主席卡通 / 总理卡通 / 领导人卡通 |
| `ad_signage_signage_special_supply` | signage | Violation | 特供 / 专供 / 内部特供 / 内部专供 / 机关特供 / 国宴特供 / 国宾专供 / 国宴专用(`categoryAnchorsAbsent=[特许经营,特许加盟]` 屏蔽加盟广告) |
| `ad_signage_signage_minor_under14_pester_parent` | minor | Violation | 妈妈我要 / 爸爸买 / 家长快买 / 妈妈快买 / 哭闹要(`categoryAnchors=[儿童,宝宝,小学生,中学生,婴幼儿,母婴,奶粉,玩具]`) |

## §2 示例图 → 规则

| 文件名 | 桶 | 严重度 | 关联规则数 | 规则 ID 列表 | 状态 |
|---|---|---|---:|---|---|
| `02_名师教育申论班_龙江第一_绝对化用语.jpg` | `已识别` | `命中` | 3 | `ad_signage_art11_data_citation`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top` | 已覆盖(老规则);**未触发** v0.1.57 新规则 — 见 §3 #1 |
| `33_公考培训宋尚案老师_某省直机关在职领导_身份.png` | `已识别` | `命中` | 2 | `ad_signage_edu_art24_public_servant_endorsement`, `ad_signage_edu_art24_public_servant_endorsement` | 已覆盖(老规则);**未触发** v0.1.57 新规则 — 见 §3 #2 |
| `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` | `已识别` | `命中` | 6 | `ad_signage_art11_data_citation`, `ad_signage_edu_art24_public_servant_endorsement`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_art9_abs_top`, `ad_signage_art28b_fake_data` | 已覆盖(老规则);**未触发** v0.1.57 新规则 — 见 §3 #1 |
| `88_傲云精酿橡木桶啤酒_国宾礼遇暗示国家级_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art22_tob_alc` | 已覆盖(老规则 — 啤酒广告);**未触发** v0.1.57 新规则 — 见 §3 #3 |

### §2.1 OCR 文本片段(便于人工复核)

| 文件 | OCR 关键片段 | 与 v0.1.57 新规则的关系 |
|---|---|---|
| `02_..._龙江第一.jpg` | 「名师学子必第一 / 黑龙江省2023年度各级机关考试录用公务员 / 名师教育」 | 「必第一」是教育承诺,不含「本省第一 / 某省第一」字面,art9_citation_radish 关键词过窄 |
| `33_..._某省直机关在职领导.png` | 「某省直机关在职中层领导 / 公务员 / 公考面试官」 | 中层干部 not 国家领导人形象,party_leader_commercial 关键词空间不覆盖中层 |
| `61_..._哈尔滨排名第一.jpg` | 「三元教育 / 哈尔滨地区排名第一 / 通过率高达75%」 | 「地区排名第一」≠ 「全市第一」字面,art9_citation_radish 关键词过窄 |
| `88_..._国宾礼遇.jpg` | 「傲云精酿 / 国宾礼遇 / 烟台傲云啤酒有限公司」 | 「国宾礼遇」≠ 「国宾专供」字面,special_supply 关键词空间不覆盖 "礼遇" 措辞 |

## §3 v0.1.57 新规则 gap / Stage 6 候选

ANY_HIT 阈值(60/N=15)虽达标(4/4),但 TARGET_RULE_HIT=0/4 揭示**4 条新规则全部缺 keyword coverage** 或 **fixture 不在规则 keyword 空间内**。**Stage 6 二阶段扩展候选**(留给 v0.1.59 单独 PR):

### §3 #1 — `art9_citation_radish` keyword 扩展

**当前关键词**:`某市第一 / 本市第一 / 本省第一 / 某县第一 / 某区第一 / 全城第一 / 全市第一 / 全区第一 / 全县第一 / 某省销量第一`

**fixture OCR 漏字样式**:
- 「名师学子必第一」(audit75 fixture 02)— 「必第一」不在关键词空间
- 「哈尔滨地区排名第一」(audit75 fixture 61)— 「地区排名第一」不在关键词空间

**Stage 6 候选**:
- 扩 keyword 加 `地区排名第一` / `地市第一` / `区域第一` / `学子必第一`(AC substring 命中兜底 OCR 漏字)
- 扩 keyword 加 `XX地区第一` 通配变体(参考 v0.1.49 「中国第品牌」扩展为 「中国第一品牌」 经验)

### §3 #2 — `party_leader_commercial` fixture 重选

**问题**:fixture 33 OCR 内容是 「某省直机关在职中层领导」,**不是国家领导人**(主席/总理/政治局常委)形象商业代言。`party_leader_commercial` 关键词空间 (主席同款/主席推荐/.../领导人卡通) **不覆盖 中层干部字面**。`edu_art24_public_servant_endorsement` 已正确触发,提示规则库对「公职人员在商业广告中代言」已有覆盖(更广义)。

**Stage 6 候选**:
- **方案 A**(扩 fixture):找一张真正含「主席同款/领导人形象/国家领导人卡通」的图(违规案例/ 中暂无,需要用户新增)
- **方案 B**(扩 keyword):扩 keyword 加 `机关在职领导` / `在职中层领导` / `在职干部` — **风险**:与现有 `edu_art24_public_servant_endorsement` 规则语义重叠,会双触
- **推荐**:方案 A(v0.1.59 fixture 扩列时新增)

### §3 #3 — `special_supply` keyword 扩展

**fixture 88 OCR** = 「国宾礼遇」,不含特供/专供字面。规则关键词不含 `国宾礼遇` / `国家级礼遇` / `国礼` 等变体。

**Stage 6 候选**:
- 扩 keyword 加 `国宾礼遇` / `国礼` / `国家级礼遇` / `接待用酒` — 与 `art9_abs_top` 的「国家级」/ `signage_diplomatic_event_endorsement` 的「东盟10国贵宾礼」语义交叉,需谨慎
- 评估是否属于「特供专供」行政执法边界:按国家市场监管总局《关于禁止以「特供」「专供」国家机关的名义进行商业营销的宣传告知书》,「国宾礼遇」不直接构成「特供专供」违法,但有相近误导性
- **推荐**:`special_supply` 当前 keyword 空间已覆盖行政执法核心 case,**不扩** — fixture 88 是真负例(国宾礼遇 ≠ 特供专供)

### §3 #4 — `minor_under14_pester_parent` fixture 缺位

`违规案例/` 中**无「妈妈/哭闹/爸爸买/宝宝要/孩子要」字样的广告图**。audit75 仅 4 张图,未覆盖此规则。

**Stage 6 候选**:
- 用户新增 1-2 张 fixture(母婴/儿童食品广告含「妈妈我要」)
- 留 v0.1.59 fixture 扩列时再补

## §4 缺位说明(v0.1.58 follow-up)

`ad_signage_signage_minor_under14_pester_parent` 规则已在 v0.1.57 落地 175 条规则库,但**无真机 fixture 验证**:
- 关键词空间(`妈妈我要 / 爸爸买 / 家长快买 / 妈妈快买 / 哭闹要`)+ anchor gate(`儿童/宝宝/小学生/中学生/婴幼儿/母婴/奶粉/玩具`)覆盖了 14 岁以下儿童哭闹要家长买的广告场景,但 staging 无对应图
- v0.1.59 fixture 扩列时优先补:`母婴店广告 + 「妈妈我要」字面` / `儿童食品 + 「爸爸买」字面` / `奶粉广告 + 「妈妈快买」字面`

## §5 数据源 / 可重跑命令

```bash
# Stage 4 重跑命令(已测过 2026-09-04 16:33)
export JAVA_HOME="/c/Users/37311/.gradle/jdks/jdk-17.0.18+8"
adb shell pm clear com.icespiritai.vision
adb logcat -c
adb logcat -v time Audit75E2E:I '*:S' > /tmp/audit75_e2e_$(date +%Y%m%d_%H%M%S).log &
./gradlew.bat connectedDebugAndroidTest \
  -PmodelProfile=ice_ocr_rules \
  -Pandroid.testInstrumentationRunnerArguments.class=com.icespiritai.offline.rules.AdSignageAudit75ImageE2ETest
```

logcat TAG = `Audit75E2E`,主通道 RESULT_JSON_BEGIN/END 之间包含完整 per_image 数据,host 端可正则提取。
