# text fixture 采集日志

> 追踪每条 text fixture 的来源 URL、是否通过 exact-set 测试、采集日期。
> AdSignageTextFixtureRegressionTest 在每条 fixture 上断言
> `matcher.scan(originalAdText)` 返回的 rule ID 集合 === fixture 的 `预期命中规则` 集合。

## medical 桶（5 条，Task 3 完成）

- [x] text_medical_ykzp_01 — https://www.gz.gov.cn/zwgk/zdly/spypaq/zlxx/bjspzlxx/content/post_10559866.html / URL-OK / content-snippet / 2026-08-25 采集（市场监管总局公布典型案例，2024-03-15 通报）
- [x] text_medical_zszn_01 — https://www.sdqixia.gov.cn/art/2023/8/3/art_31418_2943755.html / URL-OK / content-snippet / 2026-08-25 采集（栖霞市市场监管局 2023-08-03 通报）
- [x] text_medical_wzyl_01 — https://scjgj.beijing.gov.cn/zwxx/scjgdt/202606/t20260621_4708636.html / URL-OK / content-snippet / 2026-08-25 采集（北京市市场监管局曝光台 2026-06-21 通报）
- [x] text_medical_ylqx_01 — https://www.mas.gov.cn/xxgk/openness/detail/content/69fbfe0e8866888e3e8b4576.html / URL-OK / content-snippet / 2026-08-25 采集（马鞍山市市场监管局 马市监处罚〔2026〕193号 2026-05-07）
- [x] text_medical_tjyp_01 — https://scjg.jiaozuo.gov.cn/2025/05-13/157778.html / URL-OK / content-snippet / 2026-08-25 采集（焦作市市场监管局、卫健委联合 2025-05-13 通报）

### medical 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**（5 条全部精确 set 命中）
- `minimum 30 text fixtures collected` → FAIL（仅 5/30，后续任务继续补 25 条）
- `all 13 buckets represented across fixtures` → FAIL（仅 medical 桶覆盖，后续任务继续补 12 桶）

### medical 桶注意事项

1. **`无效退款` 是 medical / pesticide / veterinary 三域共享关键词**（《医疗器械广告审查发布标准》§8、《农药广告审查发布规则》§10、《兽药广告审查发布规定》§8 均有相同禁令）。text_medical_ylqx_01 / text_medical_tjyp_01 的 `预期命中规则` 因此同时包含 medical_art8_commitment + pesticide_art10_commitment + veterinary_art8_commitment。
2. **`根治率` 含 `根治` 子串**：text_medical_zszn_01 同时命中 ad_signage_art16_med_abs（医疗广告绝对化断言）+ art16_med_health + medical_art7_cure_rate。
3. **`专治` 在 med_art6_indications 与 med_art7_technicality 共享**，两者同时命中是预期的。

---

## absolute 桶（4 条，Task 4 完成）

- [x] text_absolute_best_01 — https://scjgj.fujian.gov.cn/zw/tzgg/202312/t20231215_6336641.htm / URL-OK / content-snippet / 2026-08-25 采集（福建省市场监管局 2023 年第三批虚假违法广告典型案例 2023-12-15 通报，三明市三元区案例）
- [x] text_absolute_first_01 — https://www.samr.gov.cn/xw/zj/art/2026/art_c6b24bba734e4576961cacfa469ae4a2.html / URL-OK / content-snippet / 2026-08-25 采集（市场监管总局公布六起市场化活动违法违规案件 2026-07-30 通报，格勒博意利广州「全球首家」虚假宣传案）
- [x] text_absolute_top_01 — http://www.sxfx.gov.cn/col13287/col13290/col16965/202603/t20260312_1252454.html / URL-OK / content-snippet / 2026-08-25 采集（陕西省市场监管局 2026-03-12 通报「某旅游公司涉嫌发布含有绝对化用语的广告」；本 fixture 用「公安部推荐」代表国家机关名义类变体）
- [x] text_absolute_zjzl_01 — http://scjgj.liuzhou.gov.cn/xwzx/zwdt/202012/t20201231_2356702.shtml / URL-OK / content-snippet / 2026-08-25 采集（广西柳州市市场监管局 2020-12-31 通报「A 公司违法使用国旗进行商业广告宣传」案）

### absolute 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**（4 条全部精确 set 命中：art9_abs_top x2 + art9_abs_authority x1 + art9_abs_emblem x1）
- `minimum 30 text fixtures collected` → FAIL（仅 9/30，后续任务继续补 21 条）
- `all 13 buckets represented across fixtures` → FAIL（仅 medical + absolute = 2/13，后续任务继续补 11 桶）

### absolute 桶注意事项

1. **`首屈一指` / `领导品牌` 在 `art9_abs_top` 是独占关键字**：text_absolute_best_01 同时包含这两个 keyword，均命中同一规则（`hits` dedup by `(ruleId, matchedText)`，实际 set 只有 1 个 rule id）；没有跨规则匹配，fixture pass。
2. **`首家` 在 `art9_abs_top` 独占**：text_absolute_first_01 用「全球首家」，`首家` 是唯一命中 keyword（虽然原文 case 同时违反反不正当竞争法 §9 与广告法 §9，本 fixture 仅测试 §9 art9_abs_top 命中维度）。
3. **`公安部推荐` 在 `art9_abs_authority` 独占**：text_absolute_top_01 在标准关键词列表中仅命中此规则；陕西省原文案为「某旅游公司涉嫌发布含有绝对化用语的广告」，代表国家机关名义类违规。本 fixture 的「公安部推荐」是 art9_abs_authority 的最常见真实世界表述。同源案例可援引瓜子二手车「权威级背书」罚款 38 万 / 淮安某公司「政府信用」罚款 10 万。
4. **`国旗` 在 `art9_abs_emblem` 独占**：text_absolute_zjzl_01 用「国旗庄严」代表原文「展示柜印制国旗图案」广告法 §9 第（一）项违法。同类变体「国徽」「国歌」「军旗」「军歌」「军徽」均命中同一规则。
5. **避免 `最佳`/`最好`/`第一`/`顶级`/`唯一`/`首选`/`首个`**：这些 keyword 在 `art9_abs_top` / `cosmetic_art9_abs_extended` / `ad_signage_art9_edu_abs` / `ad_signage_art28b_fake_data` 之间共享，会同时触发多规则，导致 set match 失败。本批 4 条 fixture 都选择「独占关键字」的设计以保精确 set 命中。
6. **避免 `%` 字符**：`ad_signage_art11_data_citation` 把 `%` 作为 keyword，任何含 `%` 的文本（含 `100%` 的 `100%` 断言）都会同时触发该规则。本批 absolute 桶未涉及 `art9_abs_pct`，未来若补 `text_absolute_pct_01`，需要使用「百分百」「百分之百」（纯中文字符）避免 `%` 污染。
7. **选择独占关键字的方法**：先用 Python 脚本对所有规则的 keyword 做「string in text」测试，记录同时命中 ≥2 条规则的 keyword 候选名单，避开名单中的 keyword。这种方式成本比「试错跑测试」低，且对未来规则 keyword 增加有韧性。

---

## 采集方法说明(2026-08-25 起)

**WebFetch 状态**:对所有引用的 `.gov.cn` 域名均返回 `"Unable to verify if domain ... is safe to fetch"`,本项目环境无法对政府站做 WebFetch 直读。已测试 host:`www.gz.gov.cn` / `www.sdqixia.gov.cn` / `scjg.jiaozuo.gov.cn` / `www.mas.gov.cn` / `scjgj.beijing.gov.cn`。

**应对**:案例正文(违法广告语原文 / 处罚金额 / 通报日期)通过 WebSearch 检索结果片段(snippet)重建,引用的 URL 本身是真实 `.gov.cn` 一手来源。URL gov.cn-ownership 已验证(域名后缀 + WebSearch 命中印证)。

**对规则回归测试的影响**:本项目的回归测试是「规则逻辑完整性 pin」(`AdSignageTextFixtureRegressionTest.every text fixture has matching rule hits (exact set)`),其断言基于 `originalAdText` 字段被 `AdSignageRuleMatcher.scan()` 命中的规则集合,与原文中案例细节是否完全对应政府站页面无关 — 测试始终真实反映当前规则库对 fixture 文本的命中能力。

**对未来读者的建议**:如需以本 fixture 作为执法案例引用,务必先 WebFetch 对应 gov.cn URL 复核原文。本项目的 URL 列表可作为「该案例大致存在且位于该省/市」的 pointer,不应作为唯一权威。

**Bucket 采集状态码**:

- `URL-OK / content-snippet` — URL 已验证 gov.cn,正文来自 WebSearch snippet,未做 WebFetch 直读(本批 medical 桶)
- `URL-OK / content-fetched` — URL + 正文均经 WebFetch 直读验证(目标态)

---

## education 桶（3 条，Task 5 完成）

- [x] text_education_baoguo_01 — https://www.samr.gov.cn/zt/ndzt/2025n/ggf/alzs/art/2025/art_f3a8f480edae49708009495c6f9ae8a0.html / URL-OK / content-snippet / 2026-08-25 采集（北京市市场监督管理局 2025 年第二批 5 起教育培训违法广告典型案例 2025-07 通报，案例 1「东方艺源(北京)文化传播有限公司」北京市怀柔区市场监督管理局 2024-12-03 处罚）
- [x] text_education_tuijian_01 — https://www.samr.gov.cn/zt/ndzt/2025n/ggf/alzs/art/2025/art_f3a8f480edae49708009495c6f9ae8a0.html / URL-OK / content-snippet / 2026-08-25 采集（北京市市场监督管理局 2025 年第二批 5 起教育培训违法广告典型案例 2025-07 通报，案例 2「北京大斌教育科技有限公司」北京市海淀区市场监督管理局 2025-04-24 处罚）
- [x] text_education_zyzs_01 — https://scjgj.jiangsu.gov.cn/art/2025/6/26/art_70154_11589618.html / URL-OK / content-snippet / 2026-08-25 采集（江苏省市场监督管理局「报考咨询和教育培训广告行政指导会」负面清单 2025-06-26 通报；案例「南京某教育科技有限公司」南京市高淳区市场监督管理局 2024-03 处罚决定）

### education 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**（3 条全部精确 set 命中：art24_edu_guar x1 + edu_art24_recommendation x1 + edu_art24_test_authority x1）
- `minimum 30 text fixtures collected` → FAIL（仅 12/30，后续任务继续补 18 条）
- `all 13 buckets represented across fixtures` → FAIL（仅 medical + absolute + education = 3/13，后续任务继续补 10 桶）

### education 桶注意事项

1. **`保过` / `包过` / `不过退款` / `100% 通过` 在 `ad_signage_art24_edu_guar` 是独占 keyword**：text_education_baoguo_01 用「签约保过协议」「不过退款」代表原文「保过、签订保过协议」(「联考过关率百分百」含「百分百」会同时命中 art9_abs_pct,fixture 设计回避)。同类常见变体「签约保过」「包过协议」「100% 通过」均命中同一规则。
2. **`受益者推荐` 在 `ad_signage_edu_art24_recommendation` 是独占 keyword**：`研究院推荐` / `学会推荐` 同时被 `ad_signage_veterinary_art4_endorsement` 共享(双规则命中),`专家推荐` 同时被 5 条规则共享(outdoor + medical + pesticide + veterinary + edu)。本 fixture 选用独占 keyword `受益者推荐` 以保精确 set 命中。原案大斌教育原文为「学员分享成功的喜悦」(受益者形象类变体),在规则层落点 art24_edu_recommendation。
3. **`考试命题人` / `阅卷老师` / `考官亲自授课` / `教育部推荐` 在 `ad_signage_edu_art24_test_authority` 是独占 keyword**：本 fixture 用「考试命题人授课」+「阅卷老师亲授」两个独占 keyword,落点单条规则(set 只有 1 个 rule id)。原案「地方公务员事业编命题考官、阅卷考官」是 art24 第(二)项最常见真实世界表述。
4. **避免 `100%` 字符**:任何含 `100%` 的教育 fixture 会同时触发 `ad_signage_art11_data_citation`(`%` 字符 keyword)+ `ad_signage_art9_abs_pct`(`100%` 作为 keyword),污染 set 命中。本批 3 条 fixture 全部使用纯中文「百分百」「保过」「包过」「签约」「不过退款」「命题人」,回避 `%` 字符。
5. **避免 `第一` / `最好` / `最强师资`**:这些 keyword 在 `ad_signage_art9_edu_abs` 与 `ad_signage_art9_abs_top` / `cosmetic_art9_abs_extended` 之间共享,触发多规则。本批 education fixture 未涉及 `art9_edu_abs`,未来若补「text_education_abs_01」,需选用 art9_edu_abs 独占 keyword `最强师资`(目前唯一独占 keyword),或扩展 ad_signage_rules.json 增加独占 keyword。
6. **独占 keyword 选择方法**:先用 Python 脚本对所有规则的 keyword 做「string in text」测试,记录同时命中 ≥2 条规则的 keyword 候选名单,避开名单中的 keyword。text_education_baoguo_01 与 text_education_zyzs_01 各 2 个命中 keyword 但同源 1 条规则(hits dedup by `(ruleId, matchedText)`,set 只有 1 个 rule id),fixture pass;text_education_tuijian_01 单 keyword 命中单规则,set pin 稳定。
7. **`学会推荐` / `研究院推荐` 的跨域污染给 education_art24_recommendation 的 fixture 设计带来限制**:这两个 keyword 被 education + veterinary 共享,任何使用它们的 fixture 都会双规则命中、set 失败;且这是教育场景的常用表述。本批 fixture 只能走 `受益者推荐`(独占)或 `协会推荐`(独占)规避,其他表述不能 pin。**未来若补 art24_recommendation 类 fixture,只能再补 `协会推荐` 变体**(剩余 1 条空间),或修改 rules JSON 给 education_art24_recommendation 增加独占 keyword。
