---
来源: https://www.samr.gov.cn/xw/df/art/2025/art_da0059b0627e4d6096e1f3f4b415d1ed.html （国家市场监督管理总局 2025-08-13 转载发布「北京发布农药类驱蚊产品广告合规提示」,北京市市场监督管理局发布 6 点合规提示,其中第 6 点明确《农药广告审查发布标准》第十条「农药广告不得含有『无效退款』『保险公司保险』等承诺」;同源案例如辛集市***商贸有限公司发布违法农药广告案 xinji.gov.cn/html/2190/163982.html 「经销批发 高效杀虫剂 无效退款」;又如石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号 ahshitai.gov.cn/OpennessContent/download/1241606.html「当事人发布含有『无效退款』承诺的农药广告,违反《农药广告审查发布规定》第十条」;再如长春市政务服务和数字化建设管理局「对发布违法农药广告的处罚」qzqd.zwgk.changchun.gov.cn/zx/202107/t20210704_2859937.html 引用《农药广告审查发布规定》第十条法律责任）
场景: 农药经销门店招牌 / 农资宣传册 / 经销批发名片 / 农药广告宣传单 / 农业合作社推广海报(典型农药广告承诺类违规场景)
违规点: 农药广告含「无效退款」「保险公司保险」承诺性表述,违反《广告法》第二十一条 + 《农药广告审查发布规定》第十条
法律依据: 《广告法》第二十一条 + 第五十八条 / 《农药广告审查发布规定》第十条 + 第十三条
原始违法广告语: |
  经销批发 高效杀虫剂
  无效退款 保险公司保险
预期命中规则:
  - id: ad_signage_pesticide_art10_commitment
    severity: Violation
  - id: ad_signage_medical_insurance_commitment
    severity: Warning
  备注_anchor_gate_v0_1_54:
  - commit 3 起 veterinary 规则加 categoryAnchors(兽药/兽用/兽医),本 fixture 文本(经销批发 高效杀虫剂 + 无效退款 + 保险公司保险)不含兽药锚点,veterinary_art8_commitment 不再触发。
  - commit 4 起 medical_art8_commitment 加 categoryAnchors(医疗/器械/药品/医院),本 fixture 文本同上,不含医疗锚点,medical_art8_commitment 不再触发。
  本 fixture 设计目标为「农药承诺类」单桶违规 + medical_insurance_commitment(因含「保险公司保险」)。医疗承诺规则 + 兽医承诺规则的跨桶 hit 在 anchor gate 落地后正确抑制。
处罚结果: 责令停止发布违法广告;典型处罚区间:广告费用 1-3 倍罚款(《广告法》第五十八条);广告费用无法计算或明显偏低的,处 10 万元以上 20 万元以下罚款;情节较重的,处广告费用 3-5 倍罚款,可吊销营业执照等。同源案例如辛集市***商贸有限公司发布违法农药广告案被辛集市市场监督管理局行政处罚;石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号对当事人发布含有「无效退款」承诺的农药广告进行行政处罚;长春市政务服务和数字化建设管理局对违法发布农药广告的行为依据《农药广告审查发布规定》第十三条 + 《广告法》第五十八条进行行政处罚
备注: 2025-08-13 国家市场监督管理总局转载发布北京市市场监督管理局「农药类驱蚊产品广告合规提示」6 点,其中第 6 点明确《农药广告审查发布标准》第十条「**农药广告不得含有『无效退款』『保险公司保险』等承诺**」,与《医疗器械广告审查发布标准》第八条 + 《兽药广告审查发布规定》第八条三项广告审查标准的同源禁令并列存在。本 fixture 的 `originalAdText` 同时含「无效退款」「保险公司保险」2 个跨域共享 keyword,这两个 keyword 同时命中 `ad_signage_medical_art8_commitment`(《医疗器械广告审查发布标准》第八条)+ `ad_signage_pesticide_art10_commitment`(《农药广告审查发布规定》第十条,Violation)+ `ad_signage_veterinary_art8_commitment`(《兽药广告审查发布规定》第八条)3 条规则 — 这是 fixture 设计中**故意的多桶 cross-hit 模式**:模拟真实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「驱蚊产品既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),hits dedup by `(ruleId, matchedText)`,set 严格等于 3 个 rule id。本批 fixture 刻意回避 `100% 安全` / `绝对安全` / `零副作用` / `保证有效`(`ad_signage_pesticide_art4_assertion` 跨桶 — 这 4 个 keyword 同时命中 medical/pesticide/veterinary/cosmetic 多桶)+ `高效低毒`(pesticide_art4_assertion 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃独占 keyword)+ `专家推荐` / `院士推荐` / `教授推荐` / `研究所推荐` / `学会认证` / `用户证言`(`ad_signage_pesticide_art4_endorsement` 跨桶污染 — 多数跨 veterinary/edu/outdoor)+ `拌料口服` / `随意加大剂量` / `人畜同用`(pesticide/veterinary `art4_safety_violation` 双桶共享)+ `食用安全`(pesticide_art4_safety_violation 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃)+ `保证 100% 有效`(pesticide_art10_commitment 独占,但含 `100%` 触发 art9_abs_pct + art11_data_citation 双规则污染,且含 `100% 有效` 触发 medical/veterinary/cosmetic 多桶污染,跨规则风险太高)+ `100%`(art9_abs_pct 跨规则污染)+ `保本高收益` / `稳赚不赔` / `无风险`(finance 跨桶污染)+ `销量第一` / `首选` / `金奖` / `唯一`(pesticide/veterinary/cosmetic 跨桶污染)+ `%` / `百分之`(`ad_signage_art11_data_citation` 跨规则污染)等共享 keyword,确保 set 命中严格等于 {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment}。同类常见变体:任何含「无效退款」或「保险公司保险」的农药 / 医疗 / 兽药广告均会同时触发这 3 条规则(对应现实执法「同源禁令」);未来若补 `text_agricultural_nz_02` 等额外农药广告 fixture,`ad_signage_pesticide_art4_assertion`(独占 keyword `高效低毒`)+ `ad_signage_pesticide_art3_overrange`(独占 keyword `全杀` / `万能杀虫` / `对 X 病虫草均有效`,注意「彻底根除」含 med_art7_technicality 共享 keyword `根除` 不能用)+ `ad_signage_pesticide_art11_approval_no`(独占 keyword `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ `ad_signage_pesticide_art2_unregistered`(独占 keyword `农药登记证` / `农药登记号` / `PD` / `PDN`,但 `PD` 过短可能误命中)4 条规则仍有大量独占 keyword 候选,可继续扩展。内容来自 WebSearch snippet,非 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `samr.gov.cn`)。
---

# 农药经销门店「经销批发 高效杀虫剂 / 无效退款 / 保险公司保险」违法广告案(2025)

2025 年 8 月 13 日,国家市场监督管理总局转载发布北京市市场监督管理局「**农药类驱蚊产品广告合规提示**」,提示 6 点:

1. **未经审查不得发布** —— 《广告法》第四十六条规定,农药广告发布前应当由有关部门对广告内容进行审查,未经审查不得发布广告。
2. **必须明示农药广告批准文号** —— 《农药广告审查发布标准》第十一条规定,批准文号应当列为广告内容同时发布。
3. **禁止断言功效安全** —— 《广告法》第二十一条第（一）项规定,农药广告不得含有表示功效、安全性的断言或者保证。
4. **不得利用科研单位等推荐证明** —— 不得利用科研单位、学术机构、技术推广机构、行业协会或者专业人士、用户的名义或形象作推荐、证明。
5. **禁止说明有效率** —— 农药广告不得含有说明有效率的内容。
6. **不得作出无效退款承诺** —— 《农药广告审查发布标准》第十条规定,不得含有「**无效退款**」「**保险公司保险**」等承诺。

同源案例如:

- **辛集市***商贸有限公司发布违法农药广告案**:在经营场所发布含「经销批发 高效杀虫剂 无效退款」农药广告,违反《农药广告审查发布规定》第十条 + 《广告法》第五十八条,由辛集市市场监督管理局行政处罚。
- **石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号**:当事人发布含有「无效退款」承诺的农药广告,违反《农药广告审查发布规定》第十条 + 第十三条。
- **长春市政务服务和数字化建设管理局「对发布违法农药广告的处罚」**:引用《农药广告审查发布规定》第十条 + 第十三条 + 《广告法》第五十八条法律责任。

经查,当事人作为农药经营者在农药经销门店招牌、农资宣传册、经销批发名片、农药广告宣传单或农业合作社推广海报中发布「**经销批发 高效杀虫剂**」「**无效退款 保险公司保险**」等农药广告内容。「**无效退款**」「**保险公司保险**」属于《农药广告审查发布规定》第十条明确禁止的承诺性表述,直接违反《广告法》第二十一条「**农药、兽药、饲料和饲料添加剂广告的,应当遵守国家有关规定**」与《农药广告审查发布规定》第十条「**农药广告不得含有『无效退款』『保险公司保险』等承诺**」+ 第十三条法律责任条款。该禁令与《医疗器械广告审查发布标准》第八条 + 《兽药广告审查发布规定》第八条形成同源三项广告审查标准禁令,任一含此表述的特殊商品广告(医疗 / 农药 / 兽药)均同时落入相应 3 条规则触发范围。

> **法条原文(广告法 §21)**:
> 农药、兽药、饲料和饲料添加剂广告的,应当遵守国家有关规定。

> **法条原文(农药广告审查发布规定 §10)**:
> 农药广告不得含有「无效退款」「保险公司保险」等承诺。

> **法条原文(广告法 §58)**:
> 违反本法第二十一条规定的农药、兽药、饲料和饲料添加剂广告的,处广告费用一倍以上三倍以下的罚款;广告费用无法计算或者明显偏低的,处十万元以上二十万元以下的罚款;情节较重的,处广告费用三倍以上五倍以下的罚款,可以吊销营业执照等。

> **法条原文(农药广告审查发布规定 §13)**:
> 违反本规定发布广告,《广告法》及其他法律法规有规定的,依照有关法律法规规定予以处罚。法律法规没有规定的,对负有责任的广告主、广告经营者、广告发布者,处以违法所得三倍以下但不超过三万元的罚款;没有违法所得的,处以一万元以下的罚款。

关键词「无效退款」「保险公司保险」2 个 keyword 同时命中 3 条规则:`ad_signage_medical_art8_commitment`(《医疗器械广告审查发布标准》第八条,Warning)+ `ad_signage_pesticide_art10_commitment`(《农药广告审查发布规定》第十条,Violation,本 fixture 主规则,对应 `category: pesticide`)+ `ad_signage_veterinary_art8_commitment`(《兽药广告审查发布规定》第八条,Warning) — 这是 fixture 设计中的**故意的多桶 cross-hit 模式**:模拟真实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「驱蚊产品既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),hits dedup by `(ruleId, matchedText)`,set 严格等于 3 个 rule id。本批 fixture 刻意回避 `100% 安全` / `绝对安全` / `零副作用` / `保证有效`(`ad_signage_pesticide_art4_assertion` 跨桶污染)+ `高效低毒`(pesticide_art4_assertion 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃独占 keyword)+ `专家推荐` / `院士推荐` / `教授推荐` / `研究所推荐` / `学会认证` / `用户证言`(`ad_signage_pesticide_art4_endorsement` 跨桶污染)+ `拌料口服` / `随意加大剂量` / `人畜同用`(pesticide/veterinary `art4_safety_violation` 双桶共享)+ `食用安全`(pesticide_art4_safety_violation 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃)+ `保证 100% 有效`(pesticide_art10_commitment 独占,但含 `100%` 触发 art9_abs_pct + art11_data_citation 双规则污染,且含 `100% 有效` 触发 medical/veterinary/cosmetic 多桶污染,跨规则风险太高)+ `100%`(art9_abs_pct 跨规则污染)+ `保本高收益` / `稳赚不赔` / `无风险`(finance 跨桶污染)+ `销量第一` / `首选` / `金奖` / `唯一`(pesticide/veterinary/cosmetic 跨桶污染)+ `%` / `百分之`(`ad_signage_art11_data_citation` 跨规则污染)等共享 keyword,确保 set 命中严格等于 {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment}。同类常见变体:任何含「无效退款」或「保险公司保险」的农药 / 医疗 / 兽药广告均会同时触发这 3 条规则(对应现实执法「同源禁令」);未来若补 `text_agricultural_nz_02` 等额外农药广告 fixture,`ad_signage_pesticide_art4_assertion`(独占 keyword `高效低毒`)+ `ad_signage_pesticide_art3_overrange`(独占 keyword `全杀` / `万能杀虫` / `对 X 病虫草均有效`,注意「彻底根除」含 med_art7_technicality 共享 keyword `根除` 不能用)+ `ad_signage_pesticide_art11_approval_no`(独占 keyword `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ `ad_signage_pesticide_art2_unregistered`(独占 keyword `农药登记证` / `农药登记号` / `PD` / `PDN`,但 `PD` 过短可能误命中)4 条规则仍有大量独占 keyword 候选,可继续扩展。