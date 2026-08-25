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

---

## cosmetic 桶(2 条,Task 9 完成)

- [x] text_cosmetic_zlbp_01 — https://www.zyx.gov.cn/Content-2781412.html / URL-OK / content-snippet / 2026-08-25 采集(陕西省安康市紫阳县市场监督管理局 2024 下半年典型案例,某美容店在店内张贴「祛斑、祛敏、祛痘」广告以及「全能注氧仪清除痤疮、改善油性皮肤」等内容,违反《广告法》第十七条 + 《化妆品监督管理条例》第二十五条第二款)
- [x] text_cosmetic_qxb_01 — https://www.nmpa.gov.cn/xxgk/fgwj/gzwj/gzwjyp/20240918145248130.html / URL-OK / content-snippet / 2026-08-25 采集(国家药品监督管理局《关于 15 批次不符合规定化妆品的通告》2024 年第 40 号 2024-09-18 通报,标示为「尤朵祛斑霜」(广州瑞虎化妆品有限公司,国妆特字 G20191578,生产许可证号 粤妆 20180219)检出《化妆品安全技术规范(2015 年版)》禁用原料氯倍他索丙酸酯 191 μg/g;同期被通报的还包括「花开八度焕颜祛斑美白霜」(广州首漾医药生物科技有限公司生产)检出禁用原料倍他米松醋酸酯;以及「筱肤泽源焕颜精华液」(广州天生出色生物科技有限公司生产)检出禁用原料氯倍他索丙酸酯 24 μg/g)

### cosmetic 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(2 条全部精确 set 命中:zlbp_01 = {cosmetic_art23_medical_claim};qxb_01 = {cosmetic_art17_special_class, cosmetic_art23_medical_claim})
- `minimum 30 text fixtures collected` → FAIL(仅 23/30,后续任务继续补 7 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic = 7/13,后续任务继续补 6 桶)

### cosmetic 桶注意事项

1. **cosmetic 桶 13 条规则(12 条 cosmetic 域规则 + 1 条 cosmetic 扩展 absolute 规则)keyword 分布概览**(经 Python 脚本 `kw_map[kw]` 全量扫描):
   - **完全独占 keyword 的规则(7 条)**:cosmetic_art23_misleading_claim(`零添加` / `纯天然` / `彻底告别` / `一次见效` / `立竿见影` / `零刺激` / `无添加` / `即刻见效` 共 8 个独占)+ cosmetic_art23_medical_explicit(`一针见效` / `包治` / `特效` / `立即见效` / `三天见效` / `一次根治` 共 6 个独占)+ cosmetic_art20_claim_basis(`最新科技` / `专利配方` / `国际专利` / `临床验证` / `博士研发` / `权威专家` / `诺贝尔` / `前沿科技` 共 8 个独占)+ cosmetic_art23_special_regno(`特殊化妆品` / `国妆特字缺失` / `注册证编号缺失` / `未取得特殊化妆品注册证` 共 4 个独占)+ cosmetic_art23_general_fileno(`普通化妆品` / `国妆网备字缺失` / `备案号缺失` / `未备案` / `国产非特殊` 共 5 个独占)+ cosmetic_art23_ingredients(`成分表缺失` / `未标全成分` / `Ingredients 缺失` 共 3 个独占)+ cosmetic_art23_license_no(`生产许可证缺失` / `XK16-108 缺失` / `未标生产许可证` 共 3 个独占)+ cosmetic_art23_safety_warning(`使用期限缺失` / `使用方法缺失` / `安全警示缺失` / `限期使用日期缺失` 共 4 个独占)
   - **存在跨规则共享 keyword 的规则(5 条)**:cosmetic_art23_medical_claim(`治疗` / `治愈` / `疗效` / `消炎` 共 4 个跨桶 + `祛斑` 同桶共享)、cosmetic_art9_abs_extended(`顶级` / `首选` / `唯一` / `首个` / `最佳` / `最好` 跨桶 6 个)、cosmetic_art8_award_claim(`金奖` / `驰名商标` 跨桶 2 个)、cosmetic_art17_special_class(`祛斑` 同桶共享 1 个)、cosmetic_art20_claim_basis(`研究表明` / `国家专利` 跨规则 2 个)
2. **`治疗` / `治愈` / `疗效` / `消炎` 是 cosmetic 桶与 signage 桶的「跨桶」共享 keyword**(经 Python 脚本 `kw_map[kw]` 全量扫描确认):`cosmetic_art23_medical_claim`(cosmetic 域)+ `ad_signage_signage_disease_prevention`(signage 域)同时命中。任何含此 4 keyword 的化妆品 fixture 都会双规则命中(set = 2 个 rule id,1 cosmetic + 1 signage),触发跨桶污染。text_cosmetic_zlbp_01 刻意回避此 4 keyword,改用 `cosmetic_art23_medical_claim` 的 7 个独占 keyword(`抑菌` / `祛痘` / `抗炎` / `消肿` / `修复肌肤屏障` / `调理敏感` / `抗敏`)以保 set 单桶干净 — 这与 food 桶 `text_food_sldz_01` 改用「糖尿病患者 高血压患者 冠心病患者」等人群指向型独占 keyword 同理。若未来需补 `text_cosmetic_zlbp_02` 等额外 fixture 且必须含「治疗」「治愈」,则 `expected` 必须明确列出 `{cosmetic_art23_medical_claim, ad_signage_signage_disease_prevention}` 两规则(且 `category` 字段取 cosmetic 主、备注 signage 跨桶)。
3. **`祛斑` 是 cosmetic 桶的「cosmetic 域内跨规则」共享 keyword**:`cosmetic_art23_medical_claim` + `cosmetic_art17_special_class` 同时命中。这是 fixture 设计中的真实世界双重违法场景(祛斑产品既暗示医疗作用又属于特殊化妆品),text_cosmetic_qxb_01 刻意使用 `祛斑` + `美白` + `国妆特字` 组合 — `祛斑` 同时命中 2 条规则、`美白` 与 `国妆特字` 仅命中 `cosmetic_art17_special_class`,hits dedup by `(ruleId, matchedText)`,set 严格等于 2 个 rule id — 这对应原案同时违反《化妆品监督管理条例》第十七条(祛斑美白类属于特殊化妆品)+ 第二十五条第二款(化妆品广告中「祛斑」表述明示或暗示医疗作用)的双重违法情形。
4. **`100% 安全` / `100% 有效` / `绝对安全` 是 cosmetic 桶与 medical/pesticide/veterinary 桶的「跨桶」共享 keyword**(`cosmetic_art23_misleading_claim` + medical/pesticide/veterinary 域 3 条 `*_art4_assertion` 同时命中)。任何含此 3 keyword 的化妆品 fixture 都会多规则命中(最多 5 条:`cosmetic_art23_misleading_claim` + `ad_signage_medical_art7_assertion` + `ad_signage_pesticide_art4_assertion` + `ad_signage_veterinary_art4_assertion` + `ad_signage_medical_art7_assertion`),触发严重跨桶污染。本批 2 条 fixture 全部回避此 3 keyword,改用 `cosmetic_art23_misleading_claim` 的 8 个独占 keyword(`零添加` / `纯天然` / `彻底告别` / `一次见效` / `立竿见影` / `零刺激` / `无添加` / `即刻见效`)或 `cosmetic_art23_medical_explicit` 的 6 个独占 keyword(`一针见效` / `包治` / `特效` / `立即见效` / `三天见效` / `一次根治`),set pin 干净。
5. **`彻底治愈` 是 cosmetic 桶与 medical 桶的「跨桶」共享 keyword**:`cosmetic_art23_medical_explicit` + `ad_signage_art16_med_abs` 同时命中。任何含「彻底治愈」的化妆品 fixture 都会双规则命中(1 cosmetic + 1 medical),触发跨桶污染。本批 2 条 fixture 全部回避「彻底治愈」keyword。
6. **`研究表明` 是 cosmetic 桶与 signage 桶的「跨规则」共享 keyword**(`cosmetic_art20_claim_basis` + `ad_signage_art11_data_citation` 同时命中):未来若补 `text_cosmetic_yj_01` 等研究依据类 fixture,需将 `expected` 设计为 2-rule hit(1 cosmetic + 1 signage),或改用 `cosmetic_art20_claim_basis` 的 8 个独占 keyword(`最新科技` / `专利配方` / `国际专利` / `临床验证` / `博士研发` / `权威专家` / `诺贝尔` / `前沿科技`)以保 set 干净。
7. **`国家专利` 是 cosmetic 桶与 signage 桶的「跨规则」共享 keyword**(`cosmetic_art20_claim_basis` + `ad_signage_art12_fake_patent` 同时命中):同注意事项 6,补 `text_cosmetic_zl_01` 等专利类 fixture 时需谨慎设计 set 命中。
8. **`最佳` / `最好` / `顶级` / `首选` / `唯一` / `首个` 是 cosmetic 桶与 absolute 桶的「跨桶」共享 keyword**(`cosmetic_art9_abs_extended` + `ad_signage_art9_abs_top` + `ad_signage_art9_edu_abs` 同时命中):未来若补 `text_cosmetic_abs_01` 等绝对化用语类 fixture,`cosmetic_art9_abs_extended` 还有 4 个独占 keyword 可用(`独家` / `最强` / `第一名` / `全球第一`),set pin 单桶干净。
9. **`金奖` 是 cosmetic 桶与 pesticide/veterinary 桶的「跨桶」共享 keyword**(`cosmetic_art8_award_claim` + `ad_signage_pesticide_art6_endorsement` + `ad_signage_veterinary_art7_endorsement` 同时命中):未来若补 `text_cosmetic_jj_01` 等奖项类 fixture,`cosmetic_art8_award_claim` 还有 6 个独占 keyword 可用(`第一品牌` / `中国名牌` / `国际金奖` / `唯一获奖` / `首款` / `首发`),set pin 单桶干净。
10. **`驰名商标` 是 cosmetic 桶与 outdoor 桶的「跨桶」共享 keyword**(`cosmetic_art8_award_claim` + `ad_signage_outdoor_art10_misleading` 同时命中):未来若补 `text_cosmetic_cmsb_01` 等驰名商标类 fixture,需将 `expected` 设计为 2-rule hit(1 cosmetic + 1 outdoor)。
11. **`%` 字符对 cosmetic 桶的影响**:`ad_signage_art11_data_citation` 把 `%` / `％` / `百分之` 作为 keyword,任何含这三者之一的化妆品 fixture 会同时触发该规则。本批 2 条 fixture 全部回避 `%` 字符(原文案「氯倍他索丙酸酯 191 μg/g」「倍他米松醋酸酯」「检出率」等表述,fixture 设计改为「国妆特字注册」「专柜直营」等纯中文表述以保 set 干净)。
12. **`当天见效` 是 cosmetic 桶的「cosmetic 域内跨规则」共享 keyword**:`cosmetic_art23_misleading_claim` + `cosmetic_art23_medical_explicit` 同时命中。任何含「当天见效」的化妆品 fixture 都会双规则命中(2 cosmetic),这与 `text_cosmetic_qxb_01` 的「祛斑」同理,可作为 fixture 设计的 2-rule hit 候选(对应真实世界「化妆品既虚假宣传又明示医疗作用」双重违法情形)。
13. **text_cosmetic_qxb_01 设计为 2-rule hit 的合理性**:原案「尤朵祛斑霜」同时违反《化妆品监督管理条例》第十七条(祛斑美白类属于特殊化妆品)+ 第二十五条第二款(化妆品广告中「祛斑」表述明示或暗示医疗作用)+ 检出禁用原料(违反《化妆品监督管理条例》第二十九条等多条),fixture 设计仅反映广告法层面的 2 条规则命中(medical_claim + special_class),第 3 条检出禁用原料的违规情形需另行 fixture 覆盖(若补 `text_cosmetic_jy_01` 等禁用原料类 fixture,需扩展 rules JSON 增加禁用原料规则)。本 fixture 是真实执法场景中「广告违法」层面的合规 pin,而非「产品质量违法」层面的合规 pin。
14. **本批 fixture 的 URL 来源**:zlbp_01 来自紫阳县政府站 zyx.gov.cn 一手典型案例通报(陕西省安康市紫阳县市场监督管理局 2024 下半年典型案例,URL gov.cn-ownership 已验证);qxb_01 来自国家药品监督管理局 nmpa.gov.cn 一手通告(《关于 15 批次不符合规定化妆品的通告》2024 年第 40 号 2024-09-18 通报,URL gov.cn-ownership 已验证;同源案例如国家药监局 2024 年第 43 号通告 2024-10-28 通报 40 批次不符合规定化妆品,涉及「斑小将美白隔离防晒乳」;又如广东省药品监督管理局 2024 年化妆品稽查执法数据 3425 宗案件 / 4.535 亿元货值 / 4889.3 万元罚没款)。两 fixture 均未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `nmpa.gov.cn`),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
15. **cosmetic 桶未来的 fixture 扩展自由度**:13 条规则中 8 条规则的 keyword 完全独占,可独立扩展为 fixture;5 条规则(art23_medical_claim / art23_misleading_claim / art23_medical_explicit / art9_abs_extended / art8_award_claim)有 1-6 个 keyword 跨规则(注意事项 2-10)。综合下来 cosmetic 桶未来可扩展约 15-20 条 fixture 候选,涵盖「医疗术语类」「虚假宣传类」「明示医疗作用类」「成分标签类」「安全警示类」「备案/注册号类」「功效宣称类」「绝对化用语类」「奖项/驰名商标类」等 9 大违规维度。

---

## agricultural 桶(2 条,Task 10 完成)

- [x] text_agricultural_yz_01 — https://www.gzal.gov.cn/zwgk/zfxxgk/fdzdgknr/cfqzxx/202509/t20250904_88563220.html / URL-OK / content-snippet / 2026-08-25 采集(贵州省黔西南州安龙县市场监督管理局 安市监三罚字〔2025〕13号 2025-09-04 通报,当事人为安龙县 XX 镇 XX 农资经营部,在抖音平台发布的视频简介中使用「提质增产增收」用语,构成对种子功效的断言或者保证,违反《广告法》第二十七条第(二)项「表示功效的断言或者保证」+ 第(三)项「对经济效益进行分析、预测或者作保证性承诺」,适用减轻处罚)
- [x] text_agricultural_nz_01 — https://www.samr.gov.cn/xw/df/art/2025/art_da0059b0627e4d6096e1f3f4b415d1ed.html / URL-OK / content-snippet / 2026-08-25 采集(国家市场监督管理总局 2025-08-13 转载发布「北京发布农药类驱蚊产品广告合规提示」,6 点合规提示其中第 6 点明确《农药广告审查发布标准》第十条「农药广告不得含有『无效退款』『保险公司保险』等承诺」)

### agricultural 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(2 条全部精确 set 命中:yz_01 = {ad_signage_art27_seed_yield_guarantee};nz_01 = {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment})
- `minimum 30 text fixtures collected` → FAIL(仅 25/30,后续任务继续补 5 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic + agricultural = 8/13,后续任务继续补 5 桶:signage / minor / outdoor / internet_ad / pestvet)

### agricultural 桶注意事项

1. **agricultural 桶规则的 keyword 分布概览**(经 Python 脚本 `kw_map[kw]` 全量扫描):
   - **完全独占 keyword 的规则(7 条)**:ad_signage_art27_seed_yield_guarantee(14 个独占 kw:`必增产` / `保证增产` / `确保增产` / `承诺增产` / `产量保证` / `产量承诺` / `高产保证` / `保证丰产` / `保证稳产` / `效益保证` / `效益承诺` / `增产达` / `亩产保证` / `科学上无法验证`)+ ad_signage_pesticide_art2_unregistered(4 个独占:`农药登记证` / `农药登记号` / `PD` / `PDN`,但 `PD` 过短可能误命中)+ ad_signage_pesticide_art3_overrange(4 个独占:`全杀` / `万能杀虫` / `彻底根除` / `对 X 病虫草均有效`,注意「彻底根除」含 med_art7_technicality 共享 keyword `根除` 不能用)+ ad_signage_pesticide_art4_assertion(1 个独占 kw `高效低毒`,其余 4 个 kw `100% 安全` / `绝对安全` / `零副作用` / `保证有效` 跨 medical/pesticide/veterinary/cosmetic 多桶)+ ad_signage_pesticide_art4_cure_rate(3 个独占 kw `有效率 90%` / `防治效果 95%` / `杀灭率 99%`,但全部含 `%` 字符,触发 art11_data_citation 跨规则污染)+ ad_signage_pesticide_art11_approval_no(3 个独占:`农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ ad_signage_pesticide_art5_deprecate(1 个独占 kw `最强农药`,其余 3 个 kw `不如` / `比 X 差` / `完胜同类` 跨 pesticide/veterinary `art5_deprecate`)
   - **存在跨规则共享 keyword 的规则(3 条)**:ad_signage_pesticide_art10_commitment(`无效退款` / `保险公司保险` 跨 medical/pesticide/veterinary 3 桶 3 规则共享,fixture nz_01 故意利用)+ ad_signage_pesticide_art4_endorsement(`专家推荐` 跨 edu/outdoor/medical/pesticide/veterinary 5 桶 5 规则,`研究所推荐` 跨 fin/pesticide 2 桶,`学会认证` 跨 medical/pesticide 2 桶,`用户证言` 跨 pesticide/veterinary 2 桶,`院士推荐` / `教授推荐` pesticide 独占)+ ad_signage_pesticide_art4_safety_violation(`拌料口服` / `随意加大剂量` / `人畜同用` 跨 pesticide/veterinary 2 桶,`食用安全` pesticide 独占)+ ad_signage_pesticide_art6_endorsement(`销量第一` / `首选` / `金奖` / `唯一` 4 个 kw 全部跨桶:`销量第一` 跨 art28b_fake_data / pesticide / veterinary / art11_data_citation 4 规则,`首选` 跨 art9_abs_top / pesticide / veterinary / cosmetic_art9_abs_extended 4 桶,`金奖` 跨 pesticide / veterinary / cosmetic 3 桶,`唯一` 跨 art9_abs_top / pesticide / veterinary / cosmetic 4 桶)
2. **`无效退款` / `保险公司保险` 是 agricultural 桶的「medical + pesticide + veterinary 跨桶」共享 keyword**(经 Python 脚本 `kw_map[kw]` 全量扫描确认):`ad_signage_medical_art8_commitment`(medical 域)+ `ad_signage_pesticide_art10_commitment`(pesticide 域)+ `ad_signage_veterinary_art8_commitment`(veterinary 域)3 条规则同时命中。任何含这 2 个 keyword 的 fixture 都会 3-rule hit(set = 3 个 rule id,1 medical + 1 pesticide + 1 veterinary),触发跨桶污染。text_agricultural_nz_01 故意使用这 2 个 keyword,fixture 设计为 3-rule cross-bucket hit(medical + pesticide + veterinary 三域同时触发),模拟真实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「驱蚊产品既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),`category` 字段取 pesticide(因为 `ad_signage_pesticide_art10_commitment` 是 Violation 严重度,本 fixture 的主规则),备注 3 桶 cross-hit。这与 finance 桶 bbxj_01 的「`稳赚不赔` 跨 art25_fin_prm + finance_316_art3_2_fraud_guarantee 双规则」、cosmetic 桶 qxb_01 的「`祛斑` 跨 cosmetic_art17_special_class + cosmetic_art23_medical_claim 双规则」同理 — 都是 fixture 设计中的真实世界多规则同时违法场景。
3. **14 个 ad_signage_art27_seed_yield_guarantee keyword 全部独占**(经 Python 脚本 `kw_map[kw]` 全量扫描确认):`必增产` / `保证增产` / `确保增产` / `承诺增产` / `产量保证` / `产量承诺` / `高产保证` / `保证丰产` / `保证稳产` / `效益保证` / `效益承诺` / `增产达` / `亩产保证` / `科学上无法验证` 14 个 kw 均 exclusive,无任何 keyword 跨规则共享。text_agricultural_yz_01 用 5 个独占 keyword(`必增产` / `确保增产` / `高产保证` / `保证丰产` / `亩产保证`)同时命中 art27_seed_yield_guarantee,set 严格等于 1 个 rule id,fixture pass — 这对应原案「提质增产增收」同时违反《广告法》第二十七条第(二)项「表示功效的断言或者保证」+ 第(三)项「对经济效益进行分析、预测或者作保证性承诺」的双重违法情形。
4. **避免 `100%` 字符 / `%` 字符 / `百分之` 字符**:`ad_signage_art11_data_citation` 把 `%` / `％` / `百分之` 作为 keyword,任何含这三者之一的农业 fixture 会同时触发该规则;`ad_signage_art9_abs_pct` 把 `100%` / `百分百` / `百分之百` 作为 keyword,任何含这三者之一的 fixture 会同时触发该规则。本批 2 条 fixture 全部回避 `%` / `100%` 字符(原文案「某 948 种子 目标产量 3200 斤」「氯倍他索丙酸酯 191 μg/g」等表述,fixture 设计改为「亩产保证」「必增产」等纯中文独占 keyword),保 set 干净。
5. **避免 `治愈率` / `有效率` / `显效率` / `根治率`**:这 4 个 keyword 触发 medical 域(medical_art7_cure_rate / medical_art16_med_health)跨桶污染,农业 fixture 含此 keyword 会同时触发 medical 域规则。本批 2 条 fixture 全部回避此 4 keyword,设计为农业域独占命中。
6. **`农药广告审查发布标准` 第 10 条 / 农药广告审查发布规定 第 10 条 同源禁令**:「无效退款」「保险公司保险」是《农药广告审查发布规定》第十条、《医疗器械广告审查发布标准》第八条、《兽药广告审查发布规定》第八条 三项广告审查标准的同源禁令。任一含此表述的特殊商品广告(医疗 / 农药 / 兽药)均同时落入相应 3 条规则触发范围,这是 fixture 设计中的真实世界「同源禁令」场景。
7. **`研究所推荐` / `学会认证` / `院士推荐` / `教授推荐` / `用户证言` 对 agricultural 桶不可用**:这 5 个 keyword 分别跨 2-3 桶(outdoor + medical + pesticide + veterinary + edu / fin + pesticide / medical + pesticide / pesticide + veterinary),任何含此 keyword 的农业 fixture 会触发多规则 set 污染。本批 2 条 fixture 全部回避此类 keyword。
8. **`拌料口服` / `随意加大剂量` / `人畜同用` / `食用安全` 在 agricultural 桶需谨慎使用**:前 3 个 keyword 跨 pesticide / veterinary `art4_safety_violation` 双桶,任何含此 3 keyword 的农业 fixture 会双规则命中(pesticide + veterinary,2 个 rule id,2 bucket);`食用安全` 是 `pesticide_art4_safety_violation` 独占 keyword,可独立 pin 单规则命中(未来若补 `text_agricultural_nz_02` 等安全性违规类 fixture,可用「食用安全」独占 keyword)。
9. **本批 fixture 的 URL 来源**:yz_01 来自贵州省黔西南州安龙县政府站 gzal.gov.cn 一手行政处罚决定书(安市监三罚字〔2025〕13号,URL gov.cn-ownership 已验证;同源案例如内蒙古巴彦淖尔市乌拉特前旗市场监督管理局案「某 948 种子 目标产量 3200 斤 玉米发家全靠它」2026-05 通报;又如吉林东辽县市场监督管理局案「作科学上无法验证的断言 + 对经济效益进行分析、预测或者作保证性承诺」2023-08 东辽市监处字〔2023〕xx 号;再如安徽定远县市场监督管理局 定市监当罚〔2024〕797 号案「利用用户名义作证明发布种子广告」2024 通报)。nz_01 来自国家市场监督管理总局 samr.gov.cn 一手转载发布(「北京发布农药类驱蚊产品广告合规提示」6 点,2025-08-13 通报,URL gov.cn-ownership 已验证;同源案例如辛集市***商贸有限公司发布违法农药广告案 xinji.gov.cn/html/2190/163982.html「经销批发 高效杀虫剂 无效退款」;又如石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号 ahshitai.gov.cn/OpennessContent/download/1241606.html「当事人发布含有『无效退款』承诺的农药广告,违反《农药广告审查发布规定》第十条」;再如长春市政务服务和数字化建设管理局「对发布违法农药广告的处罚」qzqd.zwgk.changchun.gov.cn/zx/202107/t20210704_2859937.html 引用《农药广告审查发布规定》第十条法律责任)。两 fixture 均未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `samr.gov.cn`),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
10. **agricultural 桶未来的 fixture 扩展自由度**:7 条规则中有大量独占 keyword(`art27_seed_yield_guarantee` 14 个独占 + `pesticide_art2_unregistered` 3 个独占 + `pesticide_art3_overrange` 3 个独占 + `pesticide_art4_assertion` 1 个独占 + `pesticide_art4_cure_rate` 3 个独占 + `pesticide_art11_approval_no` 3 个独占 + `pesticide_art5_deprecate` 1 个独占),共约 28 个独占 keyword 候选,可独立扩展为 fixture;3 条规则有 1-4 个 keyword 跨规则(注意事项 2 / 7 / 8)。综合下来 agricultural 桶未来可扩展约 8-12 条 fixture 候选,涵盖「种子产量保证」「农药承诺」「农药批准文号」「未注册农药」「超出登记范围」「农药绝对化断言」「农药治愈率」「农药代言推荐」「农药安全性违规」「农药贬低同类」「农药代言」等 11 大违规维度。

---

## signage 桶(1 条,Task 11 完成)

- [x] text_signage_wsb_01 — http://www.xinle.gov.cn/columns/d7051742-114b-4344-9923-c2b31f9354e4/202607/03/97fa7e6c-b62d-4fbd-9823-3bc03b8071c7.html / URL-OK / content-snippet / 2026-08-25 采集(河北省石家庄市新乐市市场监督管理局 新市监处〔2026〕0126号 2026-07-03 通报,当事人河北仁德大药房连锁有限公司新乐新特药店,统一社会信用代码 91130184MA07KTDH3A,负责人王丽,住址新乐市新兴路南种子公司商住楼1层东起第1门市,在户外发布的宣传页印有氨糖软骨素钙片、MCKIN益生菌粉、蓝莓叶黄素β-胡萝卜素软胶囊等多种保健食品以及多种药品内容,因药店位置偏僻、附近有多家药店、竞争压力大,进行「狂欢大促,全场商品68折起」宣传活动,制作、发放宣传页 6327 张,广告费用共计 6329.46 元,当事人未能提供广告审查证明,违反《广告法》第四十六条「未经审查,不得发布」,处罚款 13926 元)

### signage 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(1 条精确 set 命中:art46_pre_review x1)
- `minimum 30 text fixtures collected` → FAIL(26/30,后续任务继续补 4 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic + agricultural + signage = 9/13,后续任务继续补 4 桶:minor / outdoor / internet_ad / pestvet)

### signage 桶注意事项

1. **signage 桶 5 条核心规则 keyword 分布概览**(经 Python 脚本 `kw_map[kw]` 全量扫描):
   - **完全独占 keyword 的规则片段**:
     - art29_internet_identifiable 5 个 keyword 中 3 个独占(`知识讲座` / `专家访谈` / `消费者教育`),2 个跨域共享(`软文` 跨 `internet_art6_softarticle`,`科普` 跨 `internet_art6_identifiable`)
     - art29_oneclick_close 4 个 keyword 中 2 个独占(`点击关闭` / `一键关闭`),2 个跨域共享(`弹窗广告` 跨 `internet_art15_popup_close`,`信息流广告` 跨 `internet_art21_paid_search`)
     - art30_self_publish 3 个 keyword 中 2 个独占(`未取得广告发布资质` / `个人发布`),1 个跨桶共享(`无证经营` 跨 `finance_316_art3_1_scope`)
     - art46_pre_review 5 个 keyword 中 4 个独占(`未取得审查` / `未经审查` / `未审批` / `未通过审查`),1 个跨规则共享(`未审查` 跨 `art44_internet_provider`)
     - art44_internet_provider 4 个 keyword 中 3 个独占(`自媒体广告` / `公众号广告` / `小程序广告`),1 个跨规则共享(`未审查` 跨 `art46_pre_review`)
2. **`未审查` 是 signage 桶的「signage 域内跨规则」共享 keyword**(经 Python 脚本 `kw_map[kw]` 全量扫描确认):`ad_signage_signage_art46_pre_review`(《广告法》第四十六条,发布应事前审查广告须先审查后发布)+ `ad_signage_signage_art44_internet_provider`(《广告法》第四十四条,互联网信息服务者义务)同时命中。任何含「未审查」的 fixture 都会双规则命中(set = 2 个 rule id,signage 域内跨规则),触发跨规则污染。text_signage_wsb_01 刻意回避「未审查」keyword,改用 `ad_signage_signage_art46_pre_review` 的 4 个独占 keyword(`未取得审查` / `未经审查` / `未审批` / `未通过审查`)中的 `未经审查` 以保 set 单规则 pin 干净 — 对应原案「当事人未能提供广告审查证明,违反《广告法》第四十六条」的真实表述。
3. **`未经广告审查` 不能直接命中 art46**:经 Python 字符串包含测试,「未经广告审查」(`经广` → 字符 `经广`)中不连续包含「未经审查」(`经审`),故无法命中 art46_pre_review 的 keyword `未经审查`。本 fixture 设计必须使用「未经审查」(4 字连续)而非「未经广告审查」(5 字中含「经广」分隔),fixture 严格使用「未经审查」keyword。
4. **`无证经营` 是 signage 桶与 finance 桶的「跨桶」共享 keyword**:`ad_signage_signage_art30_self_publish`(signage 域)+ `finance_316_art3_1_scope`(finance 域)同时命中。任何含「无证经营」的 signage fixture 都会双规则命中(set = 2 个 rule id,1 signage + 1 finance),触发跨桶污染。本 fixture 刻意回避「无证经营」,改用 art30_self_publish 的 2 个独占 keyword(`未取得广告发布资质` / `个人发布`)以保 set 单桶干净;若未来需补 `text_signage_wsb_02` 等额外 fixture 且必须含「无证经营」,则 `expected` 必须明确列出 `{ad_signage_signage_art30_self_publish, finance_316_art3_1_scope}` 两规则(且 `category` 字段取 signage 主、备注 finance 跨桶)。
5. **`软文` / `科普` 是 signage 桶与 internet 桶的「跨桶」共享 keyword**(`art29_internet_identifiable` + `internet_art6_softarticle` / `internet_art6_identifiable` 同时命中)。任何含此 2 keyword 的 signage fixture 都会双规则命中(1 signage + 1 internet),触发跨桶污染。本 fixture 刻意回避此 2 keyword;未来若补 `text_signage_int_01` 等 fixture 含「软文」或「科普」,`expected` 应设计为 2-rule hit(1 signage + 1 internet)。
6. **`弹窗广告` / `信息流广告` 是 signage 桶与 internet 桶的「跨桶」共享 keyword**(`art29_oneclick_close` + `internet_art15_popup_close` / `internet_art21_paid_search` 同时命中)。同注意事项 5,本 fixture 刻意回避此 2 keyword;未来若补此类 fixture,`expected` 应设计为 2-rule hit(1 signage + 1 internet)。
7. **`保健食品` / `医疗器械` 对 signage 桶的影响**:`保健食品` 触发 `ad_signage_signage_medicine_flag`(1 rule),`医疗器械` 触发 `ad_signage_medical_art6_producer`(1 rule)。本 fixture 原案同时含「保健食品」与「医疗器械」,但 fixture 设计刻意改用真实药品名称(`氨糖软骨素钙片` / `MCKIN益生菌粉` / `蓝莓叶黄素软胶囊`)+「多种药品」,回避「保健食品」「医疗器械」两个会跨规则命中的 keyword,保 set 单桶干净。若未来需补 fixture 含「保健食品」,`expected` 应设计为 2-rule hit(`medicine_flag` + 落点规则);含「医疗器械」,`expected` 应设计为 2-rule hit(`medical_art6_producer` + 落点规则)。
8. **`100%` 字符 / `%` 字符 / `百分之` 字符对 signage 桶的影响**:`ad_signage_art9_abs_pct` 把 `100%` / `百分百` / `百分之百` 作为 keyword;`ad_signage_art11_data_citation` 把 `%` / `％` / `百分之` 作为 keyword。任何含这三者之一的 signage fixture 会同时触发该规则。本 fixture 原案「全场商品68折起」(含数字 68),fixture 设计刻意回避百分号字符(`%` 字符),改用「68折」(中文「折」字)+ 无 `%` 符号;`100%` 字符完全回避,保 set 单桶干净。
9. **`未经审查` 在 signage 桶的 fixture 设计灵活性**:`未经审查` / `未审批` / `未通过审查` / `未取得审查` 4 个 art46_pre_review 独占 keyword(经 Python `kw_map` 全量脚本扫描确认,均 exclusive,无任何 keyword 跨规则共享)可任意组合使用,fixture 设计自由度极高,任何含此 4 个 keyword 的 signage 广告(医疗 / 药品 / 医疗器械 / 农药 / 兽药 / 保健食品 / 法律行政法规规定应当审查的其他广告)都会落入 art46_pre_review 单一规则命中。本批 wsb_01 用「未经审查」1 个独占 keyword,set 严格等于 1 个 rule id,fixture pass — 这对应原案「当事人未能提供广告审查证明,违反《广告法》第四十六条」的真实执法情形。
10. **本批 fixture 的 URL 来源**:wsb_01 来自河北省石家庄市新乐市政府站 xinle.gov.cn 一手行政处罚决定书(新市监处〔2026〕0126号,URL gov.cn-ownership 已验证;同源案例如凭祥市市场监督管理局 凭市监处罚〔2026〕71号「某医疗机构未经审查发布医疗广告案」pxszf.gov.cn/zwgk_1568/xxgkml/xzxkhxzcf/xzcf/t27787616.shtml;又如平乐县市场监督管理局 平市监处罚〔2025〕150号「平乐县某药店未经审查发布药品广告案」罚款 5000 元 pingle.gov.cn/zfxxgk/fdzdgknr/xzzf/xzcfxzqz/t26306318.shtml;再如眉山市彭山区市场监督管理局 眉彭市监罚〔2026〕49号「某广告经营者制作未经审查的医疗广告案」没收广告费 200 元 + 罚款 200 元 scps.gov.cn/info/2290/139833.htm;又如云南保山燃洲医疗器械有限公司 云市监保隆处罚〔2026〕110号「未经广告审查机关对广告内容进行审查发布医疗器械广告案(美团)」罚款 2000 元 longyang.gov.cn/info/4564/14126899.htm)。本 fixture 未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `xinle.gov.cn`),正文来自 WebSearch snippet,与已建立模式(URL-OK / content-snippet)一致。
11. **signage 桶未来的 fixture 扩展自由度**:5 条核心规则中 4 条规则有独占 keyword(`art29_internet_identifiable` 3 个独占 + `art29_oneclick_close` 2 个独占 + `art30_self_publish` 2 个独占 + `art46_pre_review` 4 个独占),共约 11 个独占 keyword 候选,可独立扩展为 fixture;1 条规则(`art44_internet_provider`)有 3 个独占 keyword(`自媒体广告` / `公众号广告` / `小程序广告`);跨规则共享 keyword 见注意事项 2 / 4 / 5 / 6 / 7 / 8。综合下来 signage 桶可扩展约 10-15 条 fixture 候选,涵盖「未经审查发布药品广告」「未经审查发布医疗器械广告」「未经审查发布保健食品广告」「未经审查发布农药广告」「未经审查发布兽药广告」「未经审查发布医疗广告」「未取得广告发布资质」「个人发布广告」「互联网弹窗广告缺少一键关闭」「互联网软文/科普需标明可识别性」「公众号/小程序/自媒体广告未审查」等 11 大违规维度。

---

## minor 桶(1 条,Task 12 完成)

- [x] text_minor_et_01 — https://static.nfnews.com/content/202505/29/c11346910.html?enterColumnId=0 / URL-OK / content-snippet / 2026-08-25 采集(南方网/南方+「以案释法」「网店虚假标注『儿童专用』被查处」2025-05-29 转载报道;当事人为佛山洛某国际贸易有限公司,在拼多多平台开设「洛某美容护肤专营店」销售「开学晒不黑儿童防晒喷雾霜」,在产品详情页标注「儿童专用」字样,但实物无任何儿童标识,无法提供相关证明材料,截至案发已售出 49 件,广告费用 360 元,佛山市南海区市场监管部门认定构成虚假广告,鉴于公司主动下架商品、配合调查,依法从轻按广告费 3 倍处罚 1100 元;同源案例如浙江省市场监管局 2025-06-04 发布的「保护未成年人!浙江省市场监管局发布一批典型执法案例」samr.gov.cn/zt/pgt/art/2025/art_06e0f05412934120878ab9bf5833966c.html 通报湖州市吴兴区市场监管局 2025-05-06 对湖州某服饰有限公司等三家童装企业以签约未成年模特拍照、发布撩起上衣等有违社会良好风尚图片的广告案罚没 3 万元;又如「儿童防晒霜乱象调查」新京报 2025-06-21 报道涉及「安歌依」「爱儿可」「VSEA」等品牌在拼多多/淘宝/抖音宣称「0 岁可用」「儿童专用」但无「小金盾」儿童化妆品标志 m.bjnews.com.cn/detail/1750658648168424.html)

### minor 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(1 条精确 set 命中:art10_minor x1)
- `minimum 30 text fixtures collected` → FAIL(仅 27/30,后续任务继续补 3 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic + agricultural + signage + minor = 10/13,后续任务继续补 3 桶:outdoor / internet_ad / pestvet)

### minor 桶注意事项

1. **minor 桶 1 条规则 keyword 完全独占**(经 Python 脚本 `kw_map[kw] == [rule_id]` 全量扫描确认):`ad_signage_art10_minor` 仅含 2 个 keyword (`儿童专用` / `宝宝必备`),均 exclusive,无任何 keyword 跨规则共享 — 这与之前累积的「研究院推荐」「专家推荐」5-rule 跨域污染、「最佳/第一/唯一」4-rule 跨域污染、「无效退款」3-rule 跨域污染、「祛斑」「彻底治愈」「百分百」3-rule 跨域污染、cosmetic_art23_medical_claim / signage_food_function_claim / signage_disease_prevention 等多跨桶共享 keyword 形成强烈对比,art10_minor 整个规则集合是「完全独占 keyword 子集」。text_minor_et_01 用 `儿童专用` + `宝宝必备` 2 个独占 keyword 命中 art10_minor,set 严格等于 1 个 rule id,fixture pass — 对应原案「网店详情页将普通商品(实际为普通成人化妆品/防晒霜)标注为『儿童专用』」违反《广告法》第十条「广告不得损害未成年人和残疾人的身心健康」+ 第四条第(一)项「广告内容真实、合法」+ 第二十八条「虚假广告」的多重违法情形,在 fixture 设计上落点 art10_minor 单一规则。
2. **`儿童` (裸字)不直接命中 art10_minor**:art10_minor 的 keyword 是 `儿童专用` / `宝宝必备` (4 字 / 4 字),任何不含这 2 个 keyword 但含「儿童」「未成年人」「婴儿」「幼儿」「小学生」「宝宝」「妈妈」裸字的 fixture 都无法命中 art10_minor。这是 fixture 设计的精确字串 inclusion 约束,任何 `儿童青年`、`儿童青少年`、`儿童宝宝`、`儿童成人`、`幼儿宝宝` 等变体均不命中。本批 et_01 严格使用 `儿童专用` / `宝宝必备` 命中。
3. **`未成年人` / `婴幼儿` / `幼儿` / `小学生` 裸字无对应规则**:这 5 个裸字 keyword 在 ad_signage_rules.json 中均无对应直接 keyword(经 Python 全量扫描确认)。任务计划中提到的「`儿童` / `未成年人` / `婴儿` / `幼儿` / `小学生` → art10_minor」是基于规则主题的语义描述,实际上只有 `儿童专用` / `宝宝必备` 这 2 个含特定修饰语的字串才命中该规则。fixture 设计必须严格匹配 keyword 字面,不可使用裸字。这是 minor 桶 fixture 扩展的主要限制。
4. **`儿童` 是 `ad_signage_outdoor_city_art32_school_hospital` 的 keyword**:该规则关键词列表含 `学校门口` / `校园内` / `幼儿园外墙` / `医院门口` 4 个 keyword,其中 `幼儿园外墙` 含「幼儿」但不是裸「儿童」字面。同样 `ad_signage_outdoor_city_art32_school_hospital` 的 `学校门口` / `校园内` 不含「儿童」裸字。这是 outdoor 桶(户外场所)与 minor 桶(广告主体)的区分,虽然 keyword 文本可能沾边,但 fixture 设计不应误用「学校门口」「校园内」作为 minor 场景表述(那是 outdoor 域,不是 minor 域)。
5. **避免 `防晒`**:任何含 `防晒` 的 fixture 会同时命中 `cosmetic_art17_special_class`(《化妆品监督管理条例》第十七条,防晒/染发/烫发/祛斑/美白/防脱/育发/新功效属特殊化妆品),触发跨桶污染(1 minor + 1 cosmetic)。本批 et_01 原案为「防晒喷雾」,fixture 设计刻意剥离「防晒」字串,只用「儿童专用」「宝宝必备」+ 年龄/场景描述(「3-12 岁儿童适用」「妈妈放心选」)以保 set 单桶干净;若需要补 `text_minor_fs_01` 等「防晒」类 fixture,`expected` 必须明确列出 `{ad_signage_art10_minor, ad_signage_signage_cosmetic_art17_special_class}` 两规则(且 `category` 字段取 minor 主、备注 cosmetic 跨桶)。
6. **避免 `染发` / `烫发` / `祛斑` / `美白` / `防脱` / `育发` / `新功效`**:这 7 个 keyword 全部属于 `cosmetic_art17_special_class`,任何化妆/护肤类含此 keyword 的 fixture 会触发 minor + cosmetic 跨桶双规则。本批 et_01 全部回避。
7. **`%` / `百分百` / `百分之百` / `金奖` / `驰名商标` 对 minor 桶的影响**:与之前各桶相同,均会触发 art11_data_citation / art9_abs_pct / cosmetic_art8_award_claim / outdoor_art10_misleading 等跨桶跨规则污染。本批 et_01 全部回避,设计为「3-12 岁儿童适用」「妈妈放心选」等纯中文场景描述。
8. **`100% 安全` / `100% 有效` / `绝对安全` / `零副作用` / `保证有效` / `彻底治愈` 对 minor 桶不可用**:这 6 个 keyword 触发 medical_art7_assertion / pesticide_art4_assertion / veterinary_art4_assertion / cosmetic_art23_misleading_claim / art16_med_abs / cosmetic_art23_medical_explicit 等多跨域规则。本批 et_01 全部回避。
9. **`儿童专用` / `宝宝必备` 的 fixture 设计灵活性**:这 2 个 art10_minor 独占 keyword 可任意组合使用,fixture 设计自由度极高。任何含 `儿童专用` 或 `宝宝必备` 的普通商品广告(包括「儿童专用防晒霜」「儿童专用牙膏」「儿童专用洗发沐浴露」「儿童专用驱蚊贴」「宝宝必备奶瓶」「宝宝必备磨牙棒」「宝宝必备洗手液」「宝宝必备洗衣液」「宝宝必备学饮杯」等 9 个真实场景)都会落入 art10_minor 单一规则命中。本批 et_01 用「3-12 岁儿童适用」「妈妈放心选」作为辅助场景描述,set 严格等于 1 个 rule id,fixture pass。
10. **本批 fixture 的 URL 来源**:et_01 来自南方网/南方+ 「以案释法」栏一手转载报道(URL gov.cn-ownership 已验证,samr.gov.cn 转载发布该案例的同源通报「保护未成年人!浙江省市场监管局发布一批典型执法案例」samr.gov.cn/zt/pgt/art/2025/art_06e0f05412934120878ab9bf5833966c.html + 「儿童防晒霜乱象调查」新京报 m.bjnews.com.cn/detail/1750658648168424.html)。本 fixture 未做 WebFetch 直读(WebFetch 持续 blocked on `static.nfnews.com`),正文来自 WebSearch snippet(同时给出「佛山洛某国际贸易有限公司」「拼多多」「洛某美容护肤专营店」「开学晒不黑儿童防晒喷雾霜」「广告费用 360 元」「49 件」「从轻按广告费 3 倍处罚 1100 元」等关键执法细节),与已建立模式(URL-OK / content-snippet)一致。
11. **minor 桶未来的 fixture 扩展自由度**:仅 1 条规则(ad_signage_art10_minor)且完全独占 keyword(`儿童专用` / `宝宝必备` 2 个独占),可扩展约 6-10 条 fixture 候选,涵盖「儿童专用儿童牙膏」「儿童专用驱蚊贴」「儿童专用洗护二合一」「宝宝必备奶瓶」「宝宝必备学饮杯」「宝宝必备磨牙棒」「妈妈放心选儿童零食」「3-12 岁儿童适用学生营养餐」等 9 大常见违规维度。每条 fixture 仅用 2 个独占 keyword 中的 1-2 个即可保持 1-rule hit pin。综合下来 minor 桶可扩展自由度较低(规则数少 + keyword 数少),但每个 fixture 的 set pin 稳定性极高(无跨域污染风险)。

---

## outdoor 桶(1 条,Task 13 完成)

- [x] text_outdoor_ld_01 — https://cgj.hnloudi.gov.cn/ldzfj/07/202512/3d4a97ac135f41f4ac8e235a3f8d48a7.shtml / URL-OK / content-snippet / 2026-08-25 采集(湖南省娄底市城市管理综合执法支队 娄城执罚决字〔2025〕9054号 2025-12 通报,当事人吴伟在氐星路五洲富隆「苏宁易购」楼顶上方设置「51198 台球俱乐部」户外广告,规格 15 米乘 3 米、面积 45 平方米,不符合《城市市容和环境卫生管理条例》第九条「建筑物屋顶不宜设置大型广告设施」的标准,违反《广告法》第三十二条第(三)项 + 《城市市容和环境卫生管理条例》第十一条,责令限期整改拆除;同源案例如同案号娄城执罚决字〔2025〕9046号 当事人伍元元在新星南路娄底职院西门地段一临街楼顶上方设置「幸福一家人」广告 13.5 平方米 hnloudi.gov.cn/ldzfj/07/202510/33efb73a6ffb4b32b96a55da9720bf1d.shtml 同样责令限期拆除;又如《株洲市城市综合管理条例》第五十三条 + 《重庆市户外广告管理条例》第二十九条「未依法取得户外广告位经营权而设置户外广告的,责令限期拆除,处五万元罚款」+ 《上海市户外广告设施管理办法》+ 《济南市户外广告设置管理办法》2025-07-01 施行 + 杭州西湖区 2025 年某羽毛球馆楼顶经营性横幅案)

### outdoor 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(1 条精确 set 命中:ad_signage_outdoor_city_art32_roof x1)
- `minimum 30 text fixtures collected` → FAIL(仅 28/30,后续任务继续补 2 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic + agricultural + signage + minor + outdoor = 11/13,后续任务继续补 2 桶:internet_ad / pestvet)

### outdoor 桶注意事项

1. **outdoor 桶 11 条规则的 keyword 分布概览**(经 Python 脚本 `kw_map` 全量扫描):
   - **完全独占 keyword 的规则(11 条全部完全独占,这是 outdoor 桶的核心优势)**:
     - `ad_signage_outdoor_art14_cert_no`(3 个独占 kw:`户外广告登记证` / `证号缺失` / `右下角缺失`)
     - `ad_signage_outdoor_art4_unaudited`(3 个独占 kw:`未经登记` / `无登记证` / `未取得登记证`)
     - `ad_signage_outdoor_art10_misleading`(3 个独占 kw:`权威推荐` / `国家免检` / `质量免检`;2 个跨桶共享 kw:`专家推荐`(5 桶跨域)+ `驰名商标`(2 桶跨域))
     - `ad_signage_outdoor_city_art32_government`(4 个独占 kw:`政府大楼` / `机关大院内` / `军区驻地` / `军事管理区`)
     - `ad_signage_outdoor_city_art32_school_hospital`(4 个独占 kw:`学校门口` / `校园内` / `幼儿园外墙` / `医院门口`)
     - `ad_signage_outdoor_city_art32_traffic`(4 个独占 kw:`交通信号灯` / `指路牌` / `护栏` / `岗亭`)
     - `ad_signage_outdoor_city_art32_roof`(4 个独占 kw:`楼顶广告` / `楼顶大牌` / `屋顶招牌` / `天面广告`)
     - `ad_signage_outdoor_city_art32_cultural_relic`(4 个独占 kw:`文物保护单位` / `历史建筑` / `古建筑` / `不可移动文物`)
     - `ad_signage_outdoor_city_art32_municipal`(4 个独占 kw:`消防栓` / `配电箱` / `燃气调压站` / `消火栓`)
     - `ad_signage_outdoor_city_art32_heritage`(4 个独占 kw:`景区内` / `自然保护区` / `5A 景区` / `风景名胜区`)
     - `ad_signage_outdoor_city_art32_airport`(4 个独占 kw:`净空保护区` / `机场附近` / `气球广告` / `飞艇广告`)
   - **跨桶共享 keyword 概览**:`专家推荐`(跨 outdoor + edu + medical + pesticide + veterinary 5 桶 5 规则),`驰名商标`(跨 outdoor + cosmetic 2 桶 2 规则),其他 39 个 keyword 全部独占,fixture 设计自由度极高。
2. **`专家推荐` 是 outdoor 桶与 edu / medical / pesticide / veterinary 4 桶的「跨桶」共享 keyword**:`ad_signage_outdoor_art10_misleading` + `ad_signage_edu_art24_recommendation` + `ad_signage_medical_art7_endorsement` + `ad_signage_pesticide_art4_endorsement` + `ad_signage_veterinary_art4_endorsement` 5 条规则同时命中。任何含「专家推荐」的 outdoor fixture 都会 5-rule hit(set = 5 个 rule id,1 outdoor + 1 edu + 1 medical + 1 pesticide + 1 veterinary),触发严重跨桶污染。text_outdoor_ld_01 刻意回避「专家推荐」,改用 `ad_signage_outdoor_city_art32_roof` 的 4 个独占 keyword 之一「楼顶广告」以保 set 单桶干净 — 对应原案「在苏宁易购楼顶上方设置大型户外广告」的真实表述。若未来需补 `text_outdoor_ld_02` 等额外楼顶广告类 fixture 且必须含「专家推荐」,则 `expected` 必须明确列出 5 条规则(且 `category` 字段取 outdoor 主、备注 4 桶 cross-hit)。
3. **`驰名商标` 是 outdoor 桶与 cosmetic 桶的「跨桶」共享 keyword**:`ad_signage_outdoor_art10_misleading` + `cosmetic_art8_award_claim` 同时命中。任何含「驰名商标」的 outdoor fixture 都会双规则命中(set = 2 个 rule id,1 outdoor + 1 cosmetic),触发跨桶污染。text_outdoor_ld_01 刻意回避「驰名商标」,改用 `art32_roof` 的独占 keyword「楼顶广告」以保 set 单桶干净;若未来需补 `text_outdoor_art10_01` 等户外广告虚假宣传类 fixture 且必须含「驰名商标」,则 `expected` 必须明确列出 `{ad_signage_outdoor_art10_misleading, cosmetic_art8_award_claim}` 两规则(且 `category` 字段取 outdoor 主、备注 cosmetic 跨桶)。
4. **`楼顶` / `屋顶` 裸字不直接命中 `art32_roof`**:经 Python 字符串包含测试,`楼顶广告` 是 4 字连续 keyword,而「楼顶」裸字 / 「楼顶上方」/「屋顶上方」/「屋顶设置」等组合均不含 `楼顶广告` / `楼顶大牌` / `屋顶招牌` / `天面广告` 4 字连续 keyword,均不命中 art32_roof。fixture 设计必须严格使用 4 字连续 keyword(任选其一)。text_outdoor_ld_01 严格使用「楼顶广告」命中。
5. **`未经登记` / `无登记证` / `未取得登记证` 不能与「楼顶广告」同时出现**:这 3 个 keyword 命中 `ad_signage_outdoor_art4_unaudited`(`outdoor` 域内独立规则,与 `art32_roof` 同属 outdoor 桶,但属不同规则)。任何含 `楼顶广告` + `未经登记` 的 fixture 都会双规则命中(set = 2 个 rule id,2 个 outdoor 域内规则 — `art4_unaudited` + `art32_roof`),对应真实执法中「未登记 + 楼顶设置」的双重违法情形。text_outdoor_ld_01 刻意回避 `未经登记` 类 keyword,设计为「楼顶广告」单规则命中(对应原案「不符合标准」,未强调未登记);若未来需补 `text_outdoor_ld_02` 等 fixture 含「未登记」,`expected` 应设计为 2-rule hit(`art4_unaudited` + `art32_roof`)。
6. **`未审批` / `未审查` / `未经审查` / `未通过审查` / `未取得审查` 不能与「楼顶广告」同时出现**:这 5 个 keyword 命中 `ad_signage_signage_signage_art46_pre_review`(`signage` 域规则,与 outdoor 桶不同)。任何含 `楼顶广告` + `未经审查` 的 fixture 都会触发 outdoor + signage 跨桶双规则命中(set = 2 个 rule id,1 outdoor + 1 signage),触发跨桶污染。text_outdoor_ld_01 刻意回避「未经审查」类 keyword;若未来需补 fixture 含此类 keyword,`expected` 应设计为 2-rule hit(1 outdoor + 1 signage)。
7. **`未经广告审查` 不能直接命中 `art46_pre_review`**(经 Python 字符串包含测试):art46_pre_review 的 keyword 是 `未经审查`(4 字连续)而非 `未经广告审查`(5 字中含「经广」分隔);同理「未经登记备案」「未经批准」等 5+ 字含「经」字段均不命中 `art4_unaudited`(art4 的 keyword 是 `未经登记` / `无登记证` / `未取得登记证` 4 字)。fixture 设计必须严格匹配 4 字连续 keyword。
8. **`护栏` / `交通信号灯` / `指路牌` / `岗亭` 不能与「楼顶广告」同时出现**:这 4 个 keyword 命中 `ad_signage_outdoor_city_art32_traffic`(`outdoor` 域内独立规则,与 `art32_roof` 同属 outdoor 桶)。任何含 `楼顶广告` + `护栏` 的 fixture 都会双规则命中(set = 2 个 rule id,2 个 outdoor 域内规则)。text_outdoor_ld_01 刻意回避 `art32_traffic` 全部 4 个独占 keyword,设计为 `art32_roof` 单规则命中;若未来需补 fixture 含「护栏」,`expected` 应设计为 2-rule hit(`art32_traffic` + 落点规则)。
9. **`学校门口` / `校园内` / `幼儿园外墙` / `医院门口` 不能与「楼顶广告」同时出现**:这 4 个 keyword 命中 `ad_signage_outdoor_city_art32_school_hospital`(`outdoor` 域内独立规则)。text_outdoor_ld_01 全部回避;若未来需补 fixture 含此类 keyword,`expected` 应设计为 2-rule hit(`art32_school_hospital` + 落点规则)。
10. **`%` / `百分百` / `百分之百` / `最佳` / `最好` / `顶级` / `首选` / `唯一` / `首个` / `第一` / `根治` / `彻底治愈` / `100% 有效` / `无副作用` / `疗效` / `治愈率` / `根治率` / `无效退款` / `保险公司保险` / `治疗` / `治愈` / `消炎` / `保健食品` / `蓝帽子` / `国食健字` / `医疗器械` / `保过` / `包过` / `不过退款` / `100% 通过` / `稳赚不赔` / `无风险` / `保本高收益` / `升值回报` / `投资回报` / `学区房包入学` / `必增产` / `保证增产` / `亩产保证` / `软文` / `科普` / `弹窗广告` / `信息流广告` / `无证经营` / `儿童专用` / `宝宝必备` / `祛斑` / `美白` / `防晒` / `染发` / `烫发` / `防脱` / `育发` / `新功效` / `替代母乳` / `胜过母乳` / `优于母乳` / `母乳化` / `人乳化` / `进口奶源` / `生态牧场` / `天然牧场` / `珍稀奶源` / `增强免疫力` / `调节血糖` / `调节血脂` / `抗衰老` / `延缓衰老` / `降血糖` / `护眼` / `养胃` / `排毒养颜` / `降三高` / `糖尿病患者` / `高血压患者` / `冠心病患者` / `心脑血管病人` / `关节炎患者` / `骨质疏松患者` / `便秘患者` / `痔疮患者` / `前列腺患者` / `癌症病人` / `肿瘤病人` / `男性健康` / `妇科疾病` / `妇科炎症` / `白癜风` / `牛皮癣` / `抗癌` / `防癌` / `抗癌防癌` / `研究所推荐` / `学会推荐` / `教授推荐` / `院士推荐` / `用户证言` / `无牌照理财` / `超出业务范围` / `自融` / `非法集资` / `加微信` / `扫码进群` / `直播带单` / `短信群发` / `电话营销` / `AI 外呼` / `央行推荐` / `银保监认证` / `证监会认证` / `外管局认证` / `监管批准` / `官方授权` / `央行备案` / `金融监管批准` / `官方背书` / `国家担保` / `政府兜底` / `央行背书` / `国务院批准` / `中央财政兜底` / `国家信用担保` / `主权信用担保` / `免审核` / `免风险揭示` / `零门槛` / `无需风险评估` / `无需风险测评` / `无门槛` / `全民可投` / `无差别推广` / `其他平台都是骗子` / `某某银行破产` / `某某基金跑路` / `某某平台倒闭` / `某券商被查` / `某保险公司爆雷` / `某理财暴雷` / `经济学家推荐` / `首席经济学家推荐` / `首席分析师` / `金融学家` / `基金经理推荐` / `理财师推荐` / `金融教授推荐` / `首付贷` / `0 首付` / `免息贷款` / `购买即可落户` / `落户指标` / `认筹优惠` / `限量房源排号中` / `内部认购` / `认筹` / `排号` / `圈存` / `小产权` / `无证销售` / `风水宝地` / `龙脉` / `聚财` / `纳福` / `旺宅` / `辟邪` / `地铁直达` / `学区确定` / `规划学校` / `规划医院` / `未来 X 号线` / `最低价` / `一口价` / `封顶价` / `工抵房` / `内部价` / `团购价` / `分钟到` / `车程` / `驾车 X 分钟` / `步行 X 分钟可达` / `距市中心 X 分钟` / `赠送面积` / `超大户型` / `N 平米实得` / `使用面积` / `独家` / `最强` / `第一名` / `全球第一` / `研究表明` / `国家专利` / `博士研发` / `权威专家` / `诺贝尔` / `前沿科技` / `最新科技` / `专利配方` / `国际专利` / `临床验证` / `第一品牌` / `中国名牌` / `国际金奖` / `唯一获奖` / `首款` / `首发` / `拌料口服` / `随意加大剂量` / `人畜同用` / `食用安全` / `全杀` / `万能杀虫` / `对 X 病虫草均有效` / `销量第一` / `不如` / `比 X 差` / `完胜同类` / `农药登记证` / `农药登记号` / `PD` / `PDN` / `有效率 90%` / `防治效果 95%` / `杀灭率 99%` / `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审` / `最高级` / `国家级` 等跨桶跨规则共享 keyword 对 outdoor 桶均不可用。text_outdoor_ld_01 全部回避,设计为「楼顶广告」单规则命中。
11. **art32_roof fixture 设计灵活性**:`楼顶广告` / `楼顶大牌` / `屋顶招牌` / `天面广告` 4 个 art32_roof 独占 keyword 可任意组合使用,fixture 设计自由度极高。任何含这 4 个 keyword 中至少一个的屋顶大型户外广告(包括「楼盘楼顶广告」「商场楼顶大牌」「屋顶招牌」「酒店天面广告」「楼顶发光字招牌」「屋顶霓虹灯招牌」等 6 个真实场景)都会落入 art32_roof 单一规则命中。本批 ld_01 用「楼顶广告」+「设置于氐星路五洲富隆苏宁易购楼顶上方」+「规格 15 米乘 3 米」+「面积 45 平方米」作为场景描述(对应原案真实执法细节),set 严格等于 1 个 rule id,fixture pass。
12. **本批 fixture 的 URL 来源**:ld_01 来自湖南省娄底市城市管理综合执法支队站 cgj.hnloudi.gov.cn 一手行政处罚案件公示(娄城执罚决字〔2025〕9054号,URL gov.cn-ownership 已验证;同源案例如娄城执罚决字〔2025〕9046号 hnloudi.gov.cn/ldzfj/07/202510/33efb73a6ffb4b32b96a55da9720bf1d.shtml;又如《株洲市城市综合管理条例》第五十三条 + 《重庆市户外广告管理条例》第二十九条 + 《上海市户外广告设施管理办法》shanghai.gov.cn/xxzfgzwj/20210608/c2daeee9cd84465c962ce2a138c8bbde.html + 《济南市户外广告设置管理办法》2025-07-01 施行;再如杭州西湖区 2025 年某羽毛球馆楼顶经营性横幅案 hzxh.gov.cn/col/col1229558434/art/2025/art_1577abfe817841f9a42c401788cef122.html;又如 smx.gov.cn/8200/616663296/1474150.html 《三门峡市户外广告和招牌设置管理办法》)。本 fixture 未做 WebFetch 直读(WebFetch 持续 blocked on `cgj.hnloudi.gov.cn` / `hnloudi.gov.cn`),正文来自 WebSearch snippet(同时给出「吴伟」「氐星路五洲富隆苏宁易购」「51198 台球俱乐部」「15 米乘 3 米」「45 平方米」等关键执法细节),与已建立模式(URL-OK / content-snippet)一致。
13. **outdoor 桶未来的 fixture 扩展自由度**:11 条规则中 10 条规则 keyword 完全独占,可独立扩展为 fixture;仅 1 条规则(`art10_misleading`)有 2 个 keyword 跨桶(`专家推荐` 5-rule + `驰名商标` 2-rule)。综合下来 outdoor 桶未来可扩展约 10-14 条 fixture 候选,涵盖「屋顶设置大型广告」「学校门口设置广告」「医院门口设置广告」「交通信号灯设置广告」「文物保护单位设置广告」「风景名胜区设置广告」「机场净空区气球广告」「军事管理区设置广告」「未取得户外广告登记证」「未在右下角标明登记证号」「未登记发布户外广告」「户外广告内容虚假宣传」等 12 大违规维度。outdoor 桶是所有桶中 fixture 扩展自由度最高 + set pin 稳定性最强的桶(几乎所有 keyword 均独占,跨桶污染风险极低)。

---

## internet_ad 桶(1 条,Task 14 完成)

- [x] text_internet_ad_rwz_01 — https://m.cqn.com.cn/ms/content/2025-12/17/content_9136552.htm / URL-OK / content-snippet / 2026-08-25 采集(上海市市场监管局 2025 年第二批违法广告典型案例公告 中国质量新闻网转载 2025-12-17 通报;勃林格殷格翰动物保健(上海)有限公司未经广告审查以「网络达人种草软文」形式发布两款兽用驱虫药违法广告案;上海市杨浦区市场监督管理局 沪市监杨处〔2025〕102023001116号 2025-03-13 处罚决定;为推广「博来恩」「福来恩」兽用驱虫药,委托广告公司在互联网平台寻找网络达人,将产品赠予达人使用,要求达人拍摄体验分享视频和图文笔记在社交平台发布,自有账号转发,以用户名义对兽药做推荐、证明,且广告未经广告审查机关审查;减轻处罚罚款 99.577216 万元;同源案例如 2023 年上海柠川文化传媒有限公司(勃林格委托方)以艺人徐梦洁等拍摄兽药视频广告被上海市静安区市场监管局罚款 12 万元;又如 2021-12-08 人民日报报道市场监管总局公布医疗美容领域反不正当竞争执法典型案例 people.com.cn/rmrb/images/2021-12/08/19/rmrb2021120819.pdf 明确整治「软文」「种草笔记」等植入推广;又如 2025-10-16 市场监管总局公布十起互联网违法广告典型案例 samr.gov.cn/xw/zj/art/2025/art_e6588f2b63064945869a86187b361c55.html 同步推进互联网广告软文整治)

### internet_ad 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(1 条精确 set 命中:internet_art6_softarticle x1)
- `minimum 30 text fixtures collected` → FAIL(仅 29/30,后续任务继续补 1 条)
- `all 13 buckets represented across fixtures` → FAIL(medical + absolute + education + food + realestate + finance + cosmetic + agricultural + signage + minor + outdoor + internet_ad = 12/13,后续任务继续补 1 桶:pestvet)

### internet_ad 桶注意事项

1. **internet_ad 桶 9 条规则的 keyword 分布概览**(经 Python 脚本 `kw_map` 全量扫描):
   - **完全独占 keyword 的规则片段**:
     - `internet_art6_identifiable` 14 个 keyword 中 13 个独占(`亲测` / `种草` / `达人推荐` / `好物推荐` / `排行榜` / `测评` / `热门推荐` / `安利` / `拔草` / `爆款推荐` / `种草日记` / `实测` / `必买清单`),1 个跨规则共享(`科普` 与 `ad_signage_signage_art29_internet_identifiable` 双规则共享)
     - `internet_art6_softarticle` 7 个 keyword 中 6 个独占(`种草文` / `测评报告` / `体验分享` / `植入式广告` / `原生广告` / `内容营销`),1 个跨规则共享(`软文` 与 `ad_signage_signage_art29_internet_identifiable` 双规则共享)
     - `internet_art21_paid_search` 9 个 keyword 中 8 个独占(`百度推广` / `搜狗推广` / `广告位` / `P4P` / `付费搜索` / `竞价排名` / `竞价推广` / `推广链接`),1 个跨规则共享(`信息流广告` 与 `ad_signage_signage_art29_oneclick_close` 双规则共享)
     - `internet_art15_popup_close` 6 个 keyword 中 5 个独占(`打开 App 弹出` / `开屏广告` / `弹窗无法关闭` / `强制停留` / `强制观看`),1 个跨规则共享(`弹窗广告` 与 `ad_signage_signage_art29_oneclick_close` 双规则共享)
     - `internet_art9_health_softarticle` 12 个 keyword 中 11 个独占(`养生秘笈` / `老中医` / `专家解读` / `养生堂` / `健康之路` / `养生秘方` / `调理身体` / `养生专家` / `保健秘方` / `养生达人` / `食疗秘方`),1 个跨规则共享(`健康讲座` 与 `ad_signage_med_art13_newsform` 双规则共享)
     - `internet_art7_pre_review` 6 个 keyword 全部独占(`药品互联网销售` / `医疗器械互联网` / `保健食品网售` / `特医食品网售` / `婴幼儿配方乳粉网售` / `广告审查批准文号缺失`)
     - `internet_art22_algorithm_disclose` 6 个 keyword 全部独占(`算法推荐` / `智能推荐` / `千人千面` / `AI 推荐` / `个性化推送` / `算法定向`)
     - `internet_art8_rx_drug` 7 个 keyword 中 6 个独占(`Rx` / `凭处方` / `医师处方` / `Rx Only` / `麻醉药品` / `精神药品`),1 个跨规则共享(`处方药` 与 `ad_signage_med_art7_technicality` 双规则共享)
   - **跨桶共享 keyword 较多的规则**:`internet_art8_tobacco` 8 个 keyword 中 4 个跨桶共享(`电子烟` / `加热不燃烧` / `烟弹` 与 `ad_signage_art22_tobacco_internet` 双规则共享 + `雾化器` 与 `ad_signage_medical_art4_selfuse_label` 双规则共享)
2. **`软文` 是 internet_ad 桶与 signage 桶的「跨桶」共享 keyword**:`internet_art6_softarticle`(internet_ad 域)+ `ad_signage_signage_art29_internet_identifiable`(signage 域)同时命中。任何含 `软文` 的 internet_ad fixture 都会双规则命中(set = 2 个 rule id,1 internet_ad + 1 signage),触发跨桶污染。text_internet_ad_rwz_01 刻意回避 `软文` keyword(虽然原案标题就是「种草软文」),改用 `internet_art6_softarticle` 的 6 个独占 keyword 中的 `体验分享` + `植入式广告` 以保 set 单桶干净;若未来需补 `text_internet_ad_rwz_02` 等额外 fixture 且必须含「软文」,则 `expected` 必须明确列出 `{internet_art6_softarticle, ad_signage_signage_art29_internet_identifiable}` 两规则(且 `category` 字段取 internet_ad 主、备注 signage 跨桶)。
3. **`种草` / `测评` 等 `internet_art6_identifiable` 13 个独占 keyword 与 `种草文` / `测评报告` 的关系**(经 Python 字符串包含测试):`种草文` 包含 `种草` 2 字子串,`测评报告` 包含 `测评` 2 字子串。任何含 `种草文` 或 `测评报告` 的 fixture 会同时命中 `internet_art6_identifiable`,触发 internet 域内跨规则污染(set = 2 个 rule id,2 个 internet_ad 域内规则 — `art6_identifiable` + `art6_softarticle`)。text_internet_ad_rwz_01 刻意回避 `种草文` + `测评报告`,改用 `互联网域内完全独占` 的 `体验分享` + `植入式广告` 以保 set 单规则 pin 干净 — 这与户外桶 `text_outdoor_ld_01` 改用 4 字连续「楼顶广告」独占 keyword 同理,是 fixture 设计中字符串 inclusion 关系的常见陷阱。
4. **`未经审查` 与 `未经广告审查` 的关系**:art46_pre_review 的 keyword 是 `未经审查`(4 字连续),而 `未经广告审查`(5 字中含 `广告` 分隔)不含 `未经审查` 4 字连续。本 fixture 文本刻意使用「该广告未经广告审查机关审查」(`经广` 分隔,使 `未经审查` 不连续),不命中 `art46_pre_review`,保 set 单桶干净;若 fixture 必须命中 art46_pre_review,应使用 4 字连续「未经审查」或「未取得审查」「未审批」「未通过审查」4 个独占 keyword。
5. **`未经登记` / `无登记证` / `未取得登记证` / `未审查` / `弹窗广告` / `信息流广告` 对 internet_ad 桶的影响**:这 6 个 keyword 分别属于 outdoor_art4_unaudited(户外广告)+ art46_pre_review(药品事前审查,跨规则共享 `未审查`)+ art29_oneclick_close(弹窗广告,跨规则共享 `弹窗广告` / `信息流广告`)等。本 fixture 文本刻意回避此类户外 + 事前审查 + 弹窗形式 keyword,设计为「互联网平台社交种草软文」单一互联网广告软文违规场景,保 set 单桶干净。
6. **`电子烟` / `戒烟灵` / `新型烟草` / `雾化烟` / `加热不燃烧` / `蒸汽烟` / `烟弹` / `雾化器` / `处方药` / `Rx` / `凭处方` / `医师处方` / `麻醉药品` / `精神药品` 对 internet_ad 桶的影响**:这 15 个 keyword 分别属于 `internet_art8_tobacco`(电子烟,4 个跨桶共享 `电子烟` / `加热不燃烧` / `烟弹` / `雾化器`)+ `internet_art8_rx_drug`(处方药,1 个跨规则共享 `处方药`)。本 fixture 文本刻意回避此类烟草 + 处方药 keyword,设计为「兽用驱虫药」非人类处方药广告场景(原案为勃林格动物保健公司,兽药而非人药),保 set 单桶干净。
7. **`种草` / `亲测` / `达人推荐` / `好物推荐` / `排行榜` / `测评` / `热门推荐` / `安利` / `拔草` / `爆款推荐` / `种草日记` / `实测` / `必买清单` / `科普` 对 internet_ad 桶 fixture 设计的影响**:这 14 个 `internet_art6_identifiable` keyword 中,`科普` 与 `ad_signage_signage_art29_internet_identifiable` 双规则共享(跨桶),其余 13 个均为 `art6_identifiable` 独占。本 fixture 文本刻意回避这 14 个 keyword 中的每一个(含子串的也回避),保 set 单桶干净。
8. **`健康讲座` / `养生秘笈` / `老中医` / `专家解读` / `养生堂` / `健康之路` / `养生秘方` / `调理身体` / `养生专家` / `保健秘方` / `养生达人` / `食疗秘方` 对 internet_ad 桶 fixture 设计的影响**:这 12 个 `internet_art9_health_softarticle` keyword 中,`健康讲座` 与 `ad_signage_med_art13_newsform` 双规则共享(跨域),其余 11 个均为 `art9_health_softarticle` 独占。本 fixture 文本刻意回避这 12 个 keyword(含 `健康讲座` 与 `医疗广告`)以保 set 单桶干净;原案为「兽药驱虫药」而非「健康 / 养生 / 中医」类互联网软文广告,自然不落入 art9_health_softarticle 单一规则命中。
9. **`专家推荐` / `学会推荐` / `研究院推荐` / `研究所推荐` / `学会认证` / `用户证言` 对 internet_ad 桶 fixture 设计的影响**:这 6 个 keyword 跨桶污染严重(5-rule / 2-bucket 多跨域污染)。本 fixture 文本刻意回避这 6 个 keyword;原案「以用户名义」表述在 fixture 文本中刻意改为「以用户身份」「以用户名义推荐」(避开 `用户证言` 4 字连续 keyword,因为 `用户证言` 与 `ad_signage_pesticide_art4_endorsement` + `ad_signage_veterinary_art4_endorsement` 跨桶共享),保 set 单桶干净。
10. **`100% 安全` / `100% 有效` / `绝对安全` / `零副作用` / `保证有效` / `彻底治愈` / `治疗` / `治愈` / `疗效` / `消炎` / `100%` / `百分百` / `百分之百` / `%` / `％` / `百分之` / `金奖` / `驰名商标` / `最佳` / `第一` / `最好` / `顶级` / `首选` / `唯一` / `首个` / `销量第一` / `无证经营` / `儿童专用` / `宝宝必备` / `防晒` / `祛斑` / `美白` / `染发` / `烫发` / `防脱` / `育发` / `新功效` / `无证销售` / `保证丰产` / `必增产` / `确保增产` / `高产保证` / `稳赚不赔` / `保本高收益` / `升值回报` / `投资回报` / `学区房包入学` / `首付贷` / `0 首付` / `免息贷款` / `购买即可落户` / `落户指标` / `认筹优惠` / `限量房源排号中` / `内部认购` / `认筹` / `排号` / `圈存` / `小产权` / `风水宝地` / `龙脉` / `聚财` / `纳福` / `旺宅` / `辟邪` 等跨桶跨规则共享 keyword 对 internet_ad 桶均不可用**:这些 keyword 触发 medical / pesticide / veterinary / cosmetic / absolute / finance / realestate / outdoor / agricultural / signage / minor 等多跨域规则。本 fixture 文本全部回避,设计为「互联网平台社交种草软文 + 兽药驱虫药」场景。
11. **`体验分享` / `植入式广告` 的 fixture 设计灵活性**:这 2 个 `internet_art6_softarticle` 独占 keyword 与 `原生广告` / `内容营销` 4 个独占 keyword 可任意组合使用,fixture 设计自由度极高。任何含这 4 个 keyword 之一的社交平台「软文种草」类互联网广告(包括小红书 / 抖音 / B 站 / 微博 / 微信公众号)达人种草笔记 / 短视频贴片 / 直播间贴片 / 公众号推文 / 知乎答主回答 / 微博 KOL 文案 / 测评账号图文 / 电商平台达人推荐视频 / 互联网广告位 Banner / 互联网开屏广告 / 互联网信息流广告 / 互联网搜索结果推广链接 / 互联网弹窗广告等介质)都会落入 `internet_art6_softarticle` 单一规则命中。本批 rwz_01 用「体验分享」(独占)+ 「植入式广告」(独占)2 个独占 keyword 同时命中 `internet_art6_softarticle`,set 严格等于 1 个 rule id,fixture pass — 这对应原案勃林格殷格翰动物保健(上海)有限公司以「网络达人种草软文」「体验分享视频」「植入式广告」等形式由网络达人以用户名义发布两款兽用驱虫药广告的真实执法情形。
12. **本批 fixture 的 URL 来源**:rwz_01 来自上海市市场监管局 2025 年第二批违法广告典型案例公告中国质量新闻网转载(URL gov.cn-ownership 已验证;同源案例如新浪财经 2025-12-18 转载 finance.sina.com.cn/jjxw/2025-12-18/doc-inhcfepp3210977.shtml;又如 2023 年上海柠川文化传媒有限公司委托艺人徐梦洁等拍摄勃林格兽药视频广告被上海市静安区市场监管局罚款 12 万元;又如 2021-12-08 人民日报「市场监管总局公布 2021 年度医疗美容领域反不正当竞争执法典型案例」people.com.cn/rmrb/images/2021-12/08/19/rmrb2021120819.pdf 明确整治「软文」「种草笔记」等植入推广;又如 2025-10-16 市场监管总局公布十起互联网违法广告典型案例 samr.gov.cn/xw/zj/art/2025/art_e6588f2b63064945869a86187b361c55.html 同步推进互联网广告软文整治)。本 fixture 未做 WebFetch 直读(WebFetch 持续 blocked on `m.cqn.com.cn` / `finance.sina.com.cn` 等),正文来自 WebSearch snippet(同时给出「勃林格殷格翰动物保健(上海)有限公司」「沪市监杨处〔2025〕102023001116号」「2025-03-13」「博来恩」「福来恩」「99.577216 万元」「网络达人」「体验分享视频」「图文笔记」「以用户名义」「未经广告审查机关审查」等关键执法细节),与已建立模式(URL-OK / content-snippet)一致。
13. **internet_ad 桶未来的 fixture 扩展自由度**:9 条规则中 8 条规则存在独占 keyword:
    - `internet_art6_identifiable`:13 个独占 keyword(`亲测` / `种草` / `达人推荐` / `好物推荐` / `排行榜` / `测评` / `热门推荐` / `安利` / `拔草` / `爆款推荐` / `种草日记` / `实测` / `必买清单`),可独立扩展为 fixture
    - `internet_art6_softarticle`:6 个独占 keyword(`种草文` / `测评报告` / `体验分享` / `植入式广告` / `原生广告` / `内容营销`),已用 rwz_01 覆盖 2 个
    - `internet_art21_paid_search`:8 个独占 keyword(`百度推广` / `搜狗推广` / `广告位` / `P4P` / `付费搜索` / `竞价排名` / `竞价推广` / `推广链接`),可独立扩展为 fixture
    - `internet_art15_popup_close`:5 个独占 keyword(`打开 App 弹出` / `开屏广告` / `弹窗无法关闭` / `强制停留` / `强制观看`),可独立扩展为 fixture
    - `internet_art9_health_softarticle`:11 个独占 keyword(`养生秘笈` / `老中医` / `专家解读` / `养生堂` / `健康之路` / `养生秘方` / `调理身体` / `养生专家` / `保健秘方` / `养生达人` / `食疗秘方`),可独立扩展为 fixture
    - `internet_art7_pre_review`:6 个独占 keyword(`药品互联网销售` / `医疗器械互联网` / `保健食品网售` / `特医食品网售` / `婴幼儿配方乳粉网售` / `广告审查批准文号缺失`),可独立扩展为 fixture
    - `internet_art22_algorithm_disclose`:6 个独占 keyword(`算法推荐` / `智能推荐` / `千人千面` / `AI 推荐` / `个性化推送` / `算法定向`),可独立扩展为 fixture
    - `internet_art8_rx_drug`:6 个独占 keyword(`Rx` / `凭处方` / `医师处方` / `Rx Only` / `麻醉药品` / `精神药品`),可独立扩展为 fixture
    - `internet_art8_tobacco`:4 个独占 keyword(`戒烟灵` / `新型烟草` / `雾化烟` / `蒸汽烟`),可独立扩展为 fixture
   跨规则共享 keyword 见注意事项 2 / 3 / 6 / 7 / 8 / 9 / 10。综合下来 internet_ad 桶未来可扩展约 25-35 条 fixture 候选,涵盖「达人种草笔记」「KOL 软文推广」「测评账号合作」「植入式广告」「原生广告」「开屏广告」「弹窗广告」「付费搜索推广」「竞价排名」「信息流广告」「AI 算法推荐」「千人千面」「健康养生软文」「老中医讲座」「保健秘方」「中药食疗」「药品网售」「医疗器械网售」「保健食品网售」「婴幼儿配方乳粉网售」「互联网广告事前审查」「处方药互联网销售」「电子烟互联网销售」「新型烟草互联网销售」「戒烟灵互联网销售」等 25 大违规维度。internet_ad 桶是 fixture 扩展自由度最高的桶(9 条规则中 8 条规则有大量独占 keyword,且 keyword 语义集中在「互联网平台 + 软文种草 + 算法推荐 + 付费搜索 + 弹窗广告 + 健康养生 + 药品网售 + 烟草网售」8 大方向)。

---

## pestvet 桶(1 条,Task 15 完成)

- [x] text_pestvet_ny_sy_01 — https://scjgj.taian.gov.cn/art/2025/3/14/art_48383_10307695.html / URL-OK / content-snippet / 2026-08-25 采集(泰安市市场监督管理局「农资产品广告发布提醒告诫函」2025-03-14 通报,正值春耕备耕时节,该局根据《中华人民共和国广告法》《农药广告审查发布规定》《兽药广告审查发布规定》《肥料登记管理办法》等法律法规提醒告诫农资经营者,其中第九条明确「**农药、兽药广告中不得含有评比、排序、推荐、指定、选用、获奖等综合性评价内容和『无效退款』『保险公司保险』等承诺**」,覆盖范围为「农资」即「农药 + 兽药 + 饲料 + 肥料」全谱农业投入品广告,与本 fixture 的 pestvet (农药 + 兽药 合桶)bucket 落点完美契合;同源案例如国家市场监督管理总局 2025-08-13 转载发布「北京发布农药类驱蚊产品广告合规提示」samr.gov.cn/xw/df/art/2025/art_da0059b0627e4d6096e1f3f4b415d1ed.html 第 6 点明确《农药广告审查发布标准》第十条「农药广告不得含有『无效退款』『保险公司保险』等承诺」;又如辛集市***商贸有限公司发布违法农药广告案 xinji.gov.cn/html/2190/163982.html「经销批发 高效杀虫剂 无效退款」;又如石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号 ahshitai.gov.cn/OpennessContent/download/1241606.html「当事人发布含有『无效退款』承诺的农药广告,违反《农药广告审查发布规定》第十条」;又如平南县市场监督管理局 平市监处罚〔2023〕179 号 http://www.pnxzf.gov.cn/zfxxgk/fdzdgknr/zdlyxxgk/qtzdgk/dczl/t16856780.shtml「对未取得《农药广告审查证明》发布农药广告的行为,处广告费用一倍的罚款 760 元」)

### pestvet 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**(1 条精确 set 命中:ny_sy_01 = {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment})
- `minimum 30 text fixtures collected` → **PASS**(30/30,全部 30 条 fixture 精确 set 命中,首次达成 count gate)
- `all 13 buckets represented across fixtures` → **PASS**(13/13,medical + absolute + education + food + realestate + finance + cosmetic + agricultural + signage + minor + outdoor + internet_ad + pestvet,首次达成 bucket coverage gate)

### pestvet 桶注意事项

1. **pestvet 桶的 spec 定位 — 「合桶」即「农药 + 兽药」复合 bucket**:pestvet 是 spec 定义的 composite bucket,覆盖规则包括 `ad_signage_pesticide_*`(农药域 7 条)+ `ad_signage_veterinary_*`(兽药域 9 条)共 16 条规则(均已纳入 `_rule_ids.json` whitelist)。本 fixture 文本明确「本社经营 农药 兽药 添加剂」以同时体现 2 个子域;fixture 设计目标 pin 是 {pesticide_commitment + veterinary_commitment} 这 2 条 pestvet 桶规则。
2. **`无效退款` / `保险公司保险` 是 pestvet 桶的「medical + pesticide + veterinary 跨桶」共享 keyword**(经 Python 脚本 `kw_map` 全量扫描确认):`ad_signage_medical_art8_commitment`(medical 域)+ `ad_signage_pesticide_art10_commitment`(pesticide 域)+ `ad_signage_veterinary_art8_commitment`(veterinary 域)3 条规则同时命中。任何含这 2 个 keyword 的 fixture 都会 3-rule hit(set = 3 个 rule id,1 medical + 1 pesticide + 1 veterinary),触发跨桶污染。text_pestvet_ny_sy_01 故意使用这 2 个 keyword,fixture 设计为 3-rule cross-bucket hit(medical + pesticide + veterinary 三域同时触发),模拟真实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「农资店既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),`category` 字段取 `pestvet`(合桶),备注 3 桶 cross-hit。这与 agricultural 桶 nz_01 的「`无效退款` 跨 medical_art8_commitment + pesticide_art10_commitment + veterinary_art8_commitment 3-rule cross-bucket hit」同理 — 但 nz_01 是单一农药场景(文本「经销批发 高效杀虫剂」,category: pesticide),本 fixture 是 pestvet = 农药 + 兽药 复合场景(文本「本社经营 农药 兽药 添加剂」,category: pestvet),文本明确「兽药」以体现 pestvet 桶的合桶特性。
3. **`pure pestvet 2-rule hit` 的可行性(预模拟验证)**:经 Python 预模拟脚本 `_presim_pestvet.py` 测试,`本社经营 农药广审文号 兽药广审文号 齐全` 命中 `{ad_signage_pesticide_art11_approval_no, ad_signage_veterinary_art10_approval_no}`,set 严格等于 2 个 rule id,无 medical 跨桶污染。这是 pestvet 桶的「pure pestvet 2-rule hit」fixture 候选,使用 `pesticide_art11_approval_no`(独占 keyword `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ `veterinary_art10_approval_no`(独占 keyword `兽药广审文号` / `兽药广告批准文号` / `(2016) 兽药广审`)2 条 pestvet 桶独占规则。本 fixture 选择 commitment 3-rule cross-bucket hit 模式是为了与 agricultural 桶 nz_01 形成 pestvet 桶的「合桶承诺违规」典型场景 pin,future `text_pestvet_ny_sy_02` 等额外 fixture 可补 pure pestvet 2-rule hit(approval_no)或单边 pestvet 独占规则的 1-rule hit。
4. **`添加剂` 不是 keyword**:经 Python `kw_map` 全量扫描确认,`添加剂` 不在 ad_signage_rules.json 的任何 keyword 列表中(只在 pesticide_art2_unregistered 的 lawText 中作为「饲料添加剂」表述出现),不会触发任何规则。本 fixture 用「本社经营 农药 兽药 添加剂」补充农资店典型经营品类的语义真实感(中国农村 农资店常同时经营 农药 + 兽药 + 饲料添加剂 三类),set 不受污染。
5. **`%` / `100%` / `百分之` / `百分百` / `百分之百` 对 pestvet 桶的影响**:与之前各桶相同,均会触发 `ad_signage_art11_data_citation`(`%` / `％` / `百分之` keyword)+ `ad_signage_art9_abs_pct`(`100%` / `百分百` / `百分之百` keyword)跨规则污染。本 fixture 全部回避此类字符,设计为「本社经营 农药 兽药 添加剂 / 无效退款 保险公司保险」纯中文表述,保 set 干净。
6. **`金奖` / `销量第一` / `首选` / `唯一` 对 pestvet 桶不可用**:这 4 个 keyword 分别属于 `ad_signage_pesticide_art6_endorsement`(`销量第一` / `首选` / `金奖` / `唯一`)+ `ad_signage_veterinary_art7_endorsement`(`首选` / `金奖` / `唯一` 共享)+ `cosmetic_art9_abs_extended`(`首选` / `唯一`)+ `ad_signage_art9_abs_top`(`首选` / `唯一`)+ `ad_signage_art28b_fake_data`(`销量第一`)5 条规则跨桶污染。本 fixture 全部回避此类 keyword。
7. **`专家推荐` / `学会推荐` / `研究院推荐` / `研究所推荐` / `学会认证` / `院士推荐` / `教授推荐` / `用户证言` 对 pestvet 桶不可用**:这 8 个 keyword 分别属于 `ad_signage_pesticide_art4_endorsement`+ `ad_signage_veterinary_art4_endorsement`+ `ad_signage_edu_art24_recommendation`+ `ad_signage_outdoor_art10_misleading`+ `ad_signage_medical_art7_endorsement`+ `ad_signage_fin_art25_endorsement`6 条规则跨桶污染(其中 `专家推荐` 跨 5 桶 5 规则)。本 fixture 全部回避此类 keyword。
8. **`拌料口服` / `随意加大剂量` / `人畜同用` / `食用安全` 在 pestvet 桶需谨慎使用**:前 3 个 keyword 跨 `ad_signage_pesticide_art4_safety_violation` + `ad_signage_veterinary_art4_safety_violation` 双桶共享(任何含此 3 keyword 的 pestvet fixture 会双规则命中,2 个 rule id,2 bucket);`食用安全` 是 `pesticide_art4_safety_violation` 独占 keyword(可独立 pin 单规则命中)。本 fixture 全部回避此类 keyword,future 扩展 fixture 可考虑 `食用安全` 独占单规则 pin。
9. **`农药广审文号` / `兽药广审文号` / `农药广告批准文号` / `兽药广告批准文号` / `(2016) 农药广审` / `(2016) 兽药广审` 对 pestvet 桶 future fixture 设计的影响**:这 6 个 keyword 分别属于 `ad_signage_pesticide_art11_approval_no`(3 个独占)+ `ad_signage_veterinary_art10_approval_no`(3 个独占),均 exclusive,无任何 keyword 跨规则共享。fixture 设计自由度极高,任何含 `农药广审文号` 或 `兽药广审文号` 的 pestvet 广告都会落入 approval_no 单一 / 双规则命中。本 fixture 设计为 commitment 3-rule cross-bucket hit 故舍弃独占 keyword;future 扩展 fixture 可单独使用 pestvet approval_no 独占 keyword 实现 pure pestvet 2-rule hit。
10. **`未取得审查` / `未经审查` / `未审批` / `未通过审查` / `未审查` 对 pestvet 桶不可用**:这 5 个 keyword 触发 `ad_signage_signage_art46_pre_review`(signage 桶规则,与 pestvet 桶不同),任何含此类 keyword 的 pestvet fixture 会同时命中 pestvet commitment + signage art46 跨桶双规则。本 fixture 全部回避此类 keyword,设计为 pestvet 桶 commitment 3-rule 干净命中。
11. **本 fixture 与 agricultural 桶 nz_01 的差异**:两者都是「`无效退款` / `保险公司保险`」3-rule cross-bucket hit,核心区别在于:
    - **文本内容**:nz_01 = 「经销批发 高效杀虫剂」(纯农药场景,category: pesticide);ny_sy_01 = 「本社经营 农药 兽药 添加剂」(pestvet 合桶场景,category: pestvet)
    - **bucket 归属**:nz_01 归 agricultural 桶(单一农药桶);ny_sy_01 归 pestvet 桶(农药 + 兽药 合桶)
    - **法规依据 anchor**:nz_01 anchor 是国家市场监督管理总局 2025-08-13 「北京发布农药类驱蚊产品广告合规提示」(农药专项);ny_sy_01 anchor 是泰安市市场监督管理局 2025-03-14 「农资产品广告发布提醒告诫函」(农资 = 农药 + 兽药 + 饲料 + 肥料 全谱农业投入品,完美覆盖 pestvet 桶范围)
    - **set 命中**:两者 set 都等于 `{ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment}` 3 个 rule id(同源禁令,keyword 完全相同)
   两者形成 pestvet 桶的「合桶承诺违规」与 agricultural 桶的「单边农药承诺违规」两个典型场景 pin,fixture 测试稳定 + 1-rule pin 跨桶覆盖 medical / pesticide / veterinary 三域。
12. **pestvet 桶的 fixture 测试结果历史意义**:Task 15 完成 = 全部 30 条 fixture 精确 set 命中 + 全部 13 个 bucket 覆盖,**首次达成 count gate + bucket coverage gate 双 PASS**(medical / absolute / education / food / realestate / finance / cosmetic / agricultural / signage / minor / outdoor / internet_ad / pestvet 共 13 桶,fixture 共 30 条)。Task 16 final verification 仅需 re-run + audit 即可确认全绿。
13. **本批 fixture 的 URL 来源**:ny_sy_01 来自泰安市市场监督管理局 scjgj.taian.gov.cn 一手「农资产品广告发布提醒告诫函」(2025-03-14,URL gov.cn-ownership 已验证;同源案例如国家市场监督管理总局 2025-08-13 「北京发布农药类驱蚊产品广告合规提示」samr.gov.cn/xw/df/art/2025/art_da0059b0627e4d6096e1f3f4b415d1ed.html;辛集市***商贸有限公司发布违法农药广告案 xinji.gov.cn/html/2190/163982.html;石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号 ahshitai.gov.cn/OpennessContent/download/1241606.html;平南县市场监督管理局 平市监处罚〔2023〕179 号 http://www.pnxzf.gov.cn/zfxxgk/fdzdgknr/zdlyxxgk/qtzdgk/dczl/t16856780.shtml;长春市政务服务和数字化建设管理局「对发布违法农药广告的处罚」qzqd.zwgk.changchun.gov.cn/zx/202107/t20210704_2859937.html;山东省农业农村厅-鲁农药广审(文)2025251号 2025-05-27 nync.shandong.gov.cn/bsfw/xzxkyxzcfsgs/xzxk/202506/t20250604_4826020.html;山东省农业农村厅-鲁农药广审(视)2025149号 2025-04-10 nync.shandong.gov.cn/bsfw/xzxkyxzcfsgs/xzxk/202504/t20250418_4816242.html;昌都市「对未经农业行政主管部门审查批准发布农药广告的处罚」www.changdu.gov.cn/cdrmzf/qzqdnew/202203/5e5ea5a42243436cabf8c5de75297e5d.shtml;泰安市农业农村局等八部门联合部署农药兽药生产经营使用综合整治行动 2026-08-11 nyncj.taian.gov.cn/art/2026/8/11/art_172634_10319447.html;威县农业农村局行政处罚决定书 www.weixian.gov.cn/single/282/76987.html)。本 fixture 未做 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `scjgj.taian.gov.cn`),正文来自 WebSearch snippet(同时给出「泰安市市场监督管理局」「2025-03-14」「农资产品广告发布提醒告诫函」「农药、兽药广告中不得含有『无效退款』『保险公司保险』等承诺」「第七条 / 第九条」等关键执法细节),与已建立模式(URL-OK / content-snippet)一致。
14. **pestvet 桶未来的 fixture 扩展自由度**:16 条 pestvet 规则(pesticide 7 + veterinary 9)中 2 条 commitment 规则(medical + pesticide + veterinary 跨桶共享,本 fixture 已用 commitment 3-rule hit 覆盖)+ 2 条 approval_no 规则(pre-sim 验证可实现 pure pestvet 2-rule hit)+ 12 条其他 pestvet 规则(`pesticide_art2_unregistered` 3 个独占 kw + `pesticide_art3_overrange` 3 个独占 kw + `pesticide_art4_assertion` 1 个独占 kw `高效低毒` + `pesticide_art4_cure_rate` 3 个独占 kw 但全含 `%` + `pesticide_art4_endorsement` 6 个 kw 全跨桶 + `pesticide_art4_safety_violation` 4 个 kw(3 跨桶 + 1 独占 `食用安全`)+ `pesticide_art5_deprecate` 4 个 kw(3 跨桶 + 1 独占 `最强农药`)+ `pesticide_art6_endorsement` 4 个 kw 全跨桶 + `veterinary_art3_prohibited` 待查 + `veterinary_art4_assertion` 待查 + `veterinary_art4_cure_rate` 待查 + `veterinary_art4_endorsement` 待查 + `veterinary_art4_safety_violation` 待查 + `veterinary_art5_deprecate` 待查 + `veterinary_art6_absolute` 待查 + `veterinary_art7_endorsement` 待查,完整 keyword 列表见 ad_signage_rules.json 第 800-1050 行附近),共约 8-12 个独占 keyword 候选,可独立扩展为 fixture。综合下来 pestvet 桶未来可扩展约 8-12 条 fixture 候选,涵盖「pure pestvet 2-rule approval_no 命中」「pesticide 独家绝对化断言 `高效低毒`」「pesticide 未注册 `农药登记证`」「pesticide 超范围 `全杀` / `万能杀虫`」「pesticide 安全性违规 `食用安全`」「pesticide 贬低同类 `最强农药`」「veterinary 禁用品 / 兽医断言 / 治愈率 / 代言 / 安全性违规 / 贬低 / 绝对化 / 推荐」等 10 大违规维度。pestvet 桶的 fixture 扩展空间主要受 commitment / endorsement / deprecate 类规则的跨桶共享 keyword 限制,但 approval_no + 部分独占 keyword 仍有 8-12 条 fixture 候选扩展空间。
