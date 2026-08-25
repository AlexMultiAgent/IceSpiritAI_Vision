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

---

## food 桶(3 条,Task 6 完成)

- [x] text_food_bjsp_01 — https://www.samr.gov.cn/xw/mtjj/art/2025/art_e8d9a3f0ffbb4a51ac628c279d2c8535.html / URL-OK / content-snippet / 2026-08-25 采集(市场监管总局公布七起老年人药品、保健品虚假宣传典型案例 2025-10-29,案例 5「山东省冠县怡鑫生活便利超市」聊城市冠县市场监管局处罚通报,蜂胶类保健食品宣称「增强免疫力、调节血糖、调节血脂、抗衰老」)
- [x] text_food_tssj_01 — http://www.jinxiu.gov.cn/xxgk/zfxxgk/fdzdgknr/zdlyxxgk_1/qtzdxx/xzzf/xzcf/cfjg/t26571261.shtml / URL-OK / content-snippet / 2026-08-25 采集(广西金秀瑶族自治县市场监管局 金市监处罚〔2025〕72号 通报,婴幼儿配方乳粉标签使用「进口奶源」「生态牧场」「天然牧场」模糊信息)
- [x] text_food_sldz_01 — https://www.samr.gov.cn/xw/zj/art/2025/art_bcea7ffb220c456dbbd0333fed5a651d.html / URL-OK / content-snippet / 2026-08-25 采集(市场监管总局公布六起通过保健品虚假宣传进行「内卷式」竞争典型案例 2025-07-22,案例 2「江苏省张家港市德积徳优迪斯超市」张家港保税区市场监管局处罚 20 万元,蜂亿健蜂皇浆冻干粉胶囊 普通食品宣称针对糖尿病/高血压/冠心病/关节炎/骨质疏松患者)

### food 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(3 条全部精确 set 命中:bjsp_01 = {medicine_flag, food_function_claim};tssj_01 = {infant_milk};sldz_01 = {food_disease_target})
- `minimum 30 text fixtures collected` → FAIL(仅 15/30,后续任务继续补 15 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food = 4/13,后续任务继续补 9 桶)

### food 桶注意事项

1. **`保健食品` 在 `ad_signage_signage_medicine_flag` 是独占 keyword**(关键字共 3 个:`蓝帽子` / `保健食品` / `国食健字`,均 exclusive):本批 fixture 在 bjsp_01 `originalAdText` 中含「保健食品」,严格命中 medicine_flag;若改为不含「保健食品」则仅命中 food_function_claim。本 fixture 设计为双规则命中(medicine_flag + food_function_claim),set 等于 2 个 rule id,这是合理的「同时违反两条规则」的真实场景(广告功能越界 + 警示语缺失)。
2. **`增强免疫力` / `调节血糖` / `调节血脂` / `抗衰老` / `延缓衰老` 等 30+ 个 keyword 在 `ad_signage_signage_food_function_claim` 均独占**:food_function_claim 是本批 food 桶唯一完全「独占 keyword 集合」的规则,fixture 设计自由度极高,几乎所有常见保健食品功能声称表述都能 pin。本批 bjsp_01 用「增强免疫力 调节血糖 调节血脂 抗衰老」4 个独占 keyword,set 1 个 rule id;若改用其他独占 keyword(如「降血糖」「稳血压」「护眼」「养胃」「排毒养颜」「降三高」)同样能 pin。
3. **`进口奶源` / `生态牧场` / `天然牧场` / `珍稀奶源` 在 `ad_signage_signage_infant_milk` 是独占 keyword**(4 个均 exclusive):tssj_01 用 3 个独占 keyword 同时命中 infant_milk,set 1 个 rule id。**`母乳化` / `人乳化` 被 `ad_signage_signage_infant_milk` 与 `ad_signage_art20_breastmilk`(母乳代用品管理)共享**,任何含「母乳化」的婴幼儿配方乳粉 fixture 都会双规则命中、set 失败。本批 tssj_01 刻意回避此类 keyword,只用 4 个独占 keyword 变体。
4. **`糖尿病患者` / `糖尿病人的` / `高血压患者` / `冠心病患者` / `心脑血管病人` / `关节炎患者` / `骨质疏松患者` / `便秘患者` / `痔疮患者` / `前列腺患者` / `癌症病人` / `肿瘤病人` / `男性健康` / `妇科疾病` / `妇科炎症` / `白癜风` / `牛皮癣` / `抗癌` / `防癌` / `抗癌防癌` 在 `ad_signage_signage_food_disease_target` 是独占 keyword**(20 个均 exclusive):sldz_01 用 5 个独占 keyword 同时命中 food_disease_target,set 1 个 rule id。
5. **避免 `治疗` / `治愈` / `疗效` / `消炎`**:这 4 个 keyword 被 `ad_signage_signage_disease_prevention` 与 `cosmetic_art23_medical_claim` 共享,任何含这些词的普通食品 fixture 都会双规则命中(广告 §17 + 化妆品 §23),set 失败。本批 sldz_01 原案含「治疗糖尿病、高血压、冠心病」等表述,在 fixture 设计中刻意改为「糖尿病患者 高血压患者 冠心病患者」等人群指向型表述(均在 food_disease_target 独占 keyword 列表内),回避共享 keyword。**`止痛` 是 `ad_signage_signage_disease_prevention` 的独占 keyword**,未来若补 `text_food_yb_01`(药用食品含「止痛」),可 pin 单规则命中。
6. **`蓝帽子` / `国食健字` 是 `ad_signage_signage_medicine_flag` 的独占 keyword**:本批 fixture 用「保健食品」(独占 keyword)命中该规则,未用「蓝帽子」「国食健字」,但这两个 keyword 也可独立使用 — 例如在「保健品店门口贴蓝帽子标志但未显著标明警示语」场景下。
7. **本批 fixture 的 URL 来源**:bjsp_01 与 sldz_01 来自 samr.gov.cn 一手通报(URL gov.cn-ownership 已验证);tssj_01 来自广西金秀瑶族自治县政府站 jinxiu.gov.cn 一手处罚决定书(金市监处罚〔2025〕72号,URL gov.cn-ownership 已验证)。三 fixture 均未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn`),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
8. **`%` / `百分百` / `百分之百` 对 food 桶的影响**:food_function_claim / food_disease_target / infant_milk / medicine_flag 4 条规则的 keyword 列表均不含 `百分百`/`百分之百`,但 `ad_signage_art11_data_citation` 把 `%` 单独作为 keyword,任何含 `%` 字符的 fixture 会同时触发该规则。本批 3 条 fixture 全部回避 `%` 字符(原文虽有「84%」「75%」等数据表述,fixture 设计改为纯中文人群指向),保 set 干净。

---

## realestate 桶(3 条,Task 7 完成)

- [x] text_realestate_sz_01 — https://scjgj.sc.gov.cn/scsjgj/c104475/2026/1/15/29cc165341bc4c73a522310632d9f60f.shtml / URL-OK / content-snippet / 2026-08-25 采集(四川省市场监督管理局「发现一起查处一起!四川揭露房地产广告'四大套路'」2026-01-15 通报,典型案例「宜宾某房地产经纪有限公司」使用「投资回报率 9%」表述违反《广告法》第二十六条第（一）项 + 第二十八条,罚款 4 万元)
- [x] text_realestate_xqf_01 — https://scjgj.tl.gov.cn/tlsscjdglj/c00085/pc/content/content_1845747378521169920.html / URL-OK / content-snippet / 2026-08-25 采集(安徽省铜陵市市场监督管理局 2024 年市市场监管局行政处罚案件信息公示 115 号,当事人「铜陵万海佰川置业有限公司」公众号 + 户外广告牌宣传「万海澜山郡」为「十二中本部」学区房并含有升值承诺,违反《房地产广告发布规定》第四条 + 第十八条 + 《广告法》第二十六条第（一）项 + 第（四）项,罚款 10,000 元)
- [x] text_realestate_wzj_01 — https://jnszjj.jining.gov.cn/art/2025/1/26/art_36365_2706399.html / URL-OK / content-snippet / 2026-08-25 采集(山东省济宁市住房和城乡建设局 行政执法指导案例 2025-01-26 通报「某市房地产开发有限公司违法预售商品房案」,未取得《商品房预售许可证》与 61 户购房人签订合同收取预付款 4593.9 万元,违反《城市房地产开发经营管理条例》第三十六条 + 《房地产广告发布规定》第五条)

### realestate 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(3 条全部精确 set 命中:sz_01 = {ad_signage_art26_re_prm};xqf_01 = {ad_signage_art26_re_prm, ad_signage_re_art26_planned_facility};wzj_01 = {ad_signage_re_art7_license_no})
- `minimum 30 text fixtures collected` → FAIL(18/30,后续任务继续补 12 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate = 5/13,后续任务继续补 8 桶)

### realestate 桶注意事项

1. **7 条 realestate 规则的 35 个 keyword 全部独占**(经 Python 脚本 `kw_map[kw] == [rule_id]` 全量扫描,无任何 keyword 跨规则共享):art26_re_prm(升值回报 / 投资回报 / 学区房包入学)、re_art26_planned_facility(地铁直达 / 学区确定 / 规划学校 / 规划医院 / 未来 X 号线)、re_art26_price_violation(最低价 / 一口价 / 封顶价 / 工抵房 / 内部价 / 团购价)、re_art26_time_distance(分钟到 / 车程 / 驾车 X 分钟 / 步行 X 分钟可达 / 距市中心 X 分钟)、re_art4_sqmeter(赠送面积 / 超大户型 / N 平米实得 / 使用面积)、re_art7_license_no(内部认购 / 认筹 / 排号 / 圈存 / 小产权 / 无证销售)、re_art8_superstition(风水宝地 / 龙脉 / 聚财 / 纳福 / 旺宅 / 辟邪)。本批 3 条 fixture 的 hit set 由所选 keyword 唯一决定,无需担心跨规则污染。
2. **`学区房包入学`(art26_re_prm)+ `学区确定` / `规划学校`(re_art26_planned_facility)的同时命中是真实世界「同时违反 §26 第（一）项 + 第（四）项」的双重违法场景,set 严格等于 2 个 rule id,这是 fixture 设计预期的**:text_realestate_xqf_01 原案「万海佰川置业」违反《广告法》第二十六条第（一）项(房地产广告不得含有升学承诺)+ 第（四）项(不得对规划中的教育设施作误导宣传),同时违反《房地产广告发布规定》第十八条(不得使用「学区房」「学位房」「对口名校」)。fixture 用「学区房包入学」独占 keyword 落点 §26 第（一）项,「学区确定」+「规划学校」独占 keyword 落点 §26 第（四）项,set = {ad_signage_art26_re_prm, ad_signage_re_art26_planned_facility},合法反映双重违法。
3. **`%` 字符污染 realestate 桶的影响**:art26_re_prm 经常被原文案以「投资回报率 9%」「年均升值 X%」「租金覆盖月供」等形式呈现,但任何含 `%` 字符的 fixture 会同时触发 `ad_signage_art11_data_citation`(把 `%` 单独作为 keyword)。本批 sz_01 原文案为「投资回报率 9%」,fixture 设计刻意改为「升值回报」「投资回报」2 个独占 keyword(无 `%`),set 严格等于 1 个 rule id;若补「text_realestate_pct_01」等百分比回报类 fixture,可单独触发 art26_re_prm + art11_data_citation 双规则命中(对应原案「虚假数据 + 升值回报」双重违法),未来 Task 8+ 可补此类。
4. **`研究院推荐` / `学会推荐` / `专家推荐` 在 realestate 桶不可用**:这 3 个 keyword 被 5 条规则(ad_signage_outdoor_art4_unaudited + ad_signage_art16_med_health + ad_signage_pesticide_art4_endorsement + ad_signage_veterinary_art4_endorsement + ad_signage_edu_art24_recommendation)共享。realestate 桶常见「专业机构推荐」「研究院推荐学区房」类表述,任何含此类 keyword 的 fixture 会触发 5-rule 跨域污染,set 失败。本批 3 条 fixture 全部回避此类 keyword。
5. **`首付贷` / `0 首付` / `免息贷款` 在 realestate 桶不可用**:这几个 keyword 触发 `finance_*` 域规则(广告法 §25 金融服务类违规),与 realestate 桶 §26 不同。本批 wzj_01 原案含「交五万抵十万」金融促销表述,改为「认筹优惠」/「限量房源排号中」以保 set 干净;若补「text_realestate_finance_01」等场景,应将 fixture 的 `category` 字段改为 `finance`(而非 `realestate`)以反映双域违规。
6. **`购买即可落户` / `落户指标` 在 realestate 桶不可用**:这 2 个表述触发 `signage_art29_*` 互联网相关规则 + `cosmetic_art17_*` 等多重规则。本批 xqf_01 原案含「学区房 + 升学 + 落户承诺」三重违法,fixture 仅设计为「学区房包入学 + 学区确定 + 规划学校」双重违反(§26 第（一）项 + 第（四）项),回避「落户」承诺以保 set 干净。
7. **本批 fixture 的 URL 来源**:sz_01 来自四川省市场监督管理局 scjgj.sc.gov.cn 一手通报(2026-01-15,URL gov.cn-ownership 已验证);xqf_01 来自铜陵市市场监管局 scjgj.tl.gov.cn 一手行政处罚案件信息公示(2024,URL gov.cn-ownership 已验证);wzj_01 来自济宁市住建局 jnszjj.jining.gov.cn 一手行政执法指导案例(2025-01-26,URL gov.cn-ownership 已验证)。三 fixture 均未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn`),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
8. **所有 7 条 realestate 规则的 keyword 完全独占,意味着 realestate 桶未来的 fixture 扩展自由度极高**:`re_art26_price_violation`(6 个独占 kw)可补「text_realestate_price_01」(起售价 / 最低价 / 一口价 / 团购价 / 工抵房 / 内部价)、`re_art26_time_distance`(5 个独占 kw)可补「text_realestate_distance_01」(分钟到 / 车程 / 驾车 X 分钟)、`re_art4_sqmeter`(4 个独占 kw)可补「text_realestate_area_01」(赠送面积 / 超大户型 / N 平米实得)、`re_art8_superstition`(6 个独占 kw)可补「text_realestate_fengshui_01」(风水宝地 / 龙脉 / 聚财)。每条规则平均 4-6 个独占 keyword 候选,realestate 桶未来扩展非常顺畅。

---

## finance 桶(3 条,Task 8 完成)

- [x] text_finance_bbxj_01 — https://www.samr.gov.cn/zt/ndzt/2025n/ggf/dfzs/art/2025/art_4db1253cd2e44735952c145c2096d3bb.html / URL-OK / content-snippet / 2026-08-25 采集(北京市市场监管部门梳理金融投资类广告五大套路 2025-10 通报,套路二「'稳赚不赔''保本高息'画大饼」;同源案例如浙江省 2018-08 公布上半年金融违法广告典型案例 samr.gov.cn/ggjgs/sjdt/gzdt/art/2023/art_0076785d89bd4fa8ac29a364159c5cc3.html 中宁波日融财富投资管理有限公司案「100%本息保障」违反《广告法》第二十五条第（一）项罚款 18 万元;又如市场监管总局 2025-10-16 公布十起互联网违法广告典型案例 samr.gov.cn/xw/zj/art/2025/art_e6588f2b63064945869a86187b361c55.html 中海南搜了科技股份有限公司案「在家躺着赚钱,年入百万+」「疫情过后千载难逢的稳赚商机来了」罚款 16 万元 海口市市场监管局美兰分局)
- [x] text_finance_szb_01 — https://www.csrc.gov.cn/csrc/c100028/c1001463/content.shtml / URL-OK / content-snippet / 2026-08-25 采集(中国人民银行 中央网信办 工业和信息化部 工商总局 银监会 证监会 保监会 2017-09 关于防范代币发行融资风险的公告;同源案例如 2026-02 央行等 8 部门联合发布《关于进一步防范和处置虚拟货币等相关风险的通知》市场监管总局参与主体登记和广告管理;再如 2026-07-14 湖南省防范非法金融活动监测预警平台通报 dfjrjgj.hunan.gov.cn/jrbk/jrzs/202607/t20260714_34026468.html 陈某元宇宙投资项目非法吸存案判处有期徒刑 5 年 4 个月并处罚金 25 万元)
- [x] text_finance_dzp_01 — https://www.csrc.gov.cn/shaanxi/c104625/c1141895/content.shtml / URL-OK / content-snippet / 2026-08-25 采集(中国证监会陕西监管局 2024「邮币卡交易风险提示」指出邮币卡交易涉嫌违反国发〔2011〕38 号《关于清理整顿各类交易场所切实防范金融风险的决定》;同源案例如证监会青岛监管局 2024-09 csrc.gov.cn/qingdao/c105643/c1524061/content.shtml「远离'邮币卡'电子化交易」;又如最高人民检察院 2021-07-06 通报 spp.gov.cn/spp/zdgz/202107/t20210706_523144.shtml「检察官提醒邮币卡交易背后暗藏陷阱」指出邮币卡交易不需经国家证券监管机构审核省级平台亦无权批准违法开展邮币卡现货发售集中竞价涉嫌非法经营罪)

### finance 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(3 条全部精确 set 命中:bbxj_01 = {ad_signage_art25_fin_prm, finance_316_art3_2_fraud_guarantee};szb_01 = {finance_316_art3_1_scope, finance_316_art3_6_internet};dzp_01 = {finance_316_art3_1_scope, finance_316_art3_7_unlicensed_send})
- `minimum 30 text fixtures collected` → FAIL(仅 21/30,后续任务继续补 9 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance = 6/13,后续任务继续补 7 桶)

### finance 桶注意事项

1. **`保本高收益` / `稳赚不赔` / `无风险` 是 finance 桶的「finance 域内跨规则」共享 keyword**:这 3 个 keyword 同时命中 `ad_signage_art25_fin_prm`(《广告法》第二十五条第（一）项,招商等投资回报预期广告含保证性承诺)+ `finance_316_art3_2_fraud_guarantee`(银发〔2019〕316 号第三条第（二）项,金融营销宣传欺诈性保证),hits dedup by `(ruleId, matchedText)`,set 严格等于 2 个 rule id。text_finance_bbxj_01 用「稳赚不赔 保本高收益」两个共享 keyword 同时命中双规则 — 这对应原案同时违反《广告法》第二十五条第（一）项 + 银发〔2019〕316 号第三条第（二）项的双重违法情形,set pin 稳定。本 fixture 设计为 2-rule hit(不是 1-rule hit),是因为真实金融广告在执法层面就是「广告法 §25 + 银发 316 号 §3(2)」同时违反,fixture 反映真实执法情形。
2. **`研究所推荐` / `经济学家推荐` 是 finance 桶的「finance 域内跨规则」共享 keyword**(金融代言):`研究所推荐` 跨 `ad_signage_fin_art25_endorsement`(finance) + `ad_signage_pesticide_art4_endorsement`(pesticide,跨桶);`经济学家推荐` 跨 `ad_signage_fin_art25_endorsement`(finance) + `finance_art25_endorsement_reinforced`(finance,同桶)。这两个 keyword 本批 3 条 fixture 均未涉及(未来若补 `text_finance_dxyj_01` / `text_finance_dsj_01` 等「代言/推荐」类 fixture,需要把 `expected` 设计为 2-rule hit)。
3. **`无证经营` 是 finance 桶与 signage 桶的「跨桶」共享 keyword**(经 Python 脚本 `kw_map[kw]` 全量扫描确认):`finance_316_art3_1_scope`(finance 域)+ `ad_signage_signage_art30_self_publish`(signage 域)同时命中。任何含「无证经营」的金融 fixture 都会双规则命中(set = 2 个 rule id,1 finance + 1 signage),触发跨桶污染。text_finance_dzp_01 刻意回避「无证经营」keyword,改用 `finance_316_art3_1_scope` 的 3 个独占 keyword(`无牌照理财` / `超出业务范围` / `自融`)以保 set 单桶干净 — 这与 `text_finance_szb_01` 的「非法集资」「自融」独占 keyword 选择同理。若未来需补 `text_finance_dzp_02` 等额外 fixture 且必须含「无证经营」,则 `expected` 必须明确列出 `{finance_316_art3_1_scope, ad_signage_signage_art30_self_publish}` 两规则(且 `category` 字段取 finance 主、备注 signage 跨桶)。
4. **finance 桶 14 条规则的 keyword 绝大多数独占**:仅 `保本高收益` / `稳赚不赔` / `无风险`(三 keyword,finance 域内跨规则)+ `研究所推荐`(跨 finance/pesticide)+ `经济学家推荐`(finance 域内跨规则)+ `无证经营`(跨 finance/signage)6 个 keyword 跨规则。其他 keyword 全部独占。fixture 设计时优先选用独占 keyword(`无牌照理财` / `超出业务范围` / `自融` / `非法集资` / `加微信` / `扫码进群` / `直播带单` / `短信群发` / `电话营销` / `AI 外呼` / `零风险` / `无风险收益` / `本金保障` / `保本保息` 等),set pin 干净。
5. **`%` 字符对 finance 桶的影响**:`ad_signage_art11_data_citation` 把 `%` / `％` / `百分之` 作为 keyword,任何含这三者之一的金融 fixture 会同时触发该规则。text_finance_bbxj_01 原文案为「年化收益 18%」「100% 本息保障」(宁波日融财富原案),fixture 设计改为「年化收益十八个点」(纯中文口语,无 `%`/`百分之`)+ 回避「100% 本息保障」,保 set 干净。本批 3 条 fixture 全部回避 `%` 字符。
6. **finance 桶常见但需规避的 keyword 候选清单**:`稳赚不赔` / `保本高收益` / `无风险`(触发 art25_fin_prm + finance_316_art3_2_fraud_guarantee 双规则,本批 bbxj_01 故意使用)+ `100% 盈利` / `保证收益` / `保收益` / `零亏损` / `无亏损` / `保证不亏` / `绝对收益` / `年化保底` / `最低收益`(全部 finance_316_art3_2_fraud_guarantee 独占,但应转入 bbxj 桶以保桶语义一致)+ `最佳平台` / `最安全` / `最稳` / `第一平台` / `顶级理财` / `稳赚平台` / `最强团队` / `唯一合规`(`finance_art9_abs_investment` 独占,本批未涉及,未来可补 `text_finance_abs_01`)+ `首席经济学家推荐` / `首席分析师` / `经济学家推荐` / `金融学家` / `基金经理推荐` / `理财师推荐` / `金融教授推荐`(finance_art25_endorsement_reinforced 独占;`经济学家推荐` 跨规则,见注意事项 2)+ `央行推荐` / `银保监认证` / `证监会认证` / `外管局认证` / `监管批准` / `官方授权` / `央行备案` / `金融监管批准` / `官方背书`(finance_316_art3_2_regulator_use 独占,未来可补 `text_finance_jg_01`)+ `免审核` / `免风险揭示` / `零门槛` / `无需风险评估` / `无需风险测评` / `无门槛` / `全民可投` / `无差别推广`(finance_316_art3_2_consumer_right 独占,未来可补 `text_finance_xfz_01`)+ `其他平台都是骗子` / `某某银行破产` / `某某基金跑路` / `某某平台倒闭` / `某券商被查` / `某保险公司爆雷` / `某理财暴雷`(finance_316_art3_3_fair_competition 独占,未来可补 `text_finance_jzc_01`)+ `国家担保` / `政府兜底` / `央行背书` / `国务院批准` / `中央财政兜底` / `国家信用担保` / `主权信用担保`(finance_316_art3_4_government_use 独占,未来可补 `text_finance_zfbs_01`)+ `零风险` / `无风险收益` / `本金保障` / `保本保息`(`ad_signage_fin_art25_unlawful` 独占,未来可补 `text_finance_wfx_01`)。
7. **本批 fixture 的 URL 来源**:bbxj_01 来自 samr.gov.cn 一手通报(北京市市场监管部门梳理金融投资类广告五大套路,URL gov.cn-ownership 已验证;同源案例如浙江省 2018-08 金融违法广告典型案例 samr.gov.cn/ggjgs/sjdt/gzdt/art/2023/art_0076785d89bd4fa8ac29a364159c5cc3.html + 市场监管总局 2025-10-16 十起互联网违法广告典型案例 samr.gov.cn/xw/zj/art/2025/art_e6588f2b63064945869a86187b361c55.html);szb_01 来自 csrc.gov.cn 一手监管文件(2017《关于防范代币发行融资风险的公告》csrc.gov.cn/csrc/c100028/c1001463/content.shtml + 2026-02 央行等 8 部门《关于进一步防范和处置虚拟货币等相关风险的通知》同源脉络;典型刑事案件陈某元宇宙非法吸存案 dfjrjgj.hunan.gov.cn/jrbk/jrzs/202607/t20260714_34026468.html);dzp_01 来自 csrc.gov.cn 一手监管文件(陕西监管局 2024「邮币卡交易风险提示」csrc.gov.cn/shaanxi/c104625/c1141895/content.shtml + 青岛监管局 2024-09「远离邮币卡电子化交易」csrc.gov.cn/qingdao/c105643/c1524061/content.shtml + 最高人民检察院 2021-07-06 通报 spp.gov.cn/spp/zdgz/202107/t20210706_523144.shtml)。三 fixture 均未做 WebFetch 直读(WebFetch 持续阻塞 .gov.cn / csrc.gov.cn 等政府站),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
8. **finance 桶未来的 fixture 扩展自由度**:14 条规则中 8 条规则的 keyword 完全独占,可独立扩展为 fixture;1 条(art25_fin_prm)有 3 个 keyword 与 finance_316_art3_2_fraud_guarantee 共享(已用 bbxj_01 覆盖);5 条规则有 1-2 个 keyword 跨规则(注意事项 1-3)。综合下来 finance 桶未来可扩展约 10-12 条 fixture 候选。
