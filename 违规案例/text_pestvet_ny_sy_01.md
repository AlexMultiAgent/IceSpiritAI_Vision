---
来源: https://scjgj.taian.gov.cn/art/2025/3/14/art_48383_10307695.html （泰安市市场监督管理局「农资产品广告发布提醒告诫函」2025-03-14 通报,正值春耕备耕时节,该局根据《中华人民共和国广告法》《农药广告审查发布规定》《兽药广告审查发布规定》等法律法规,提醒告诫农资经营者规范农资广告经营活动;其中第六条明确「未取得兽药产品批准文号或者《进口兽药注册证书》的兽药,不得发布广告」;第七条明确「发布农药、兽药广告,应当在发布前由广告审查机关对广告内容进行审查;未经审查,不得发布;农药、兽药广告的批准文号应当列为广告内容同时发布」;第九条明确「农药、兽药广告中不得含有评比、排序、推荐、指定、选用、获奖等综合性评价内容和『无效退款』『保险公司保险』等承诺」;同源案例如国家市场监督管理总局 2025-08-13 转载发布「北京发布农药类驱蚊产品广告合规提示」samr.gov.cn/xw/df/art/2025/art_da0059b0627e4d6096e1f3f4b415d1ed.html 第 6 点明确《农药广告审查发布标准》第十条「农药广告不得含有『无效退款』『保险公司保险』等承诺」;又如辛集市***商贸有限公司发布违法农药广告案 xinji.gov.cn/html/2190/163982.html 「经销批发 高效杀虫剂 无效退款」;又如石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号 ahshitai.gov.cn/OpennessContent/download/1241606.html「当事人发布含有『无效退款』承诺的农药广告,违反《农药广告审查发布规定》第十条」;又如平南县市场监督管理局 平市监处罚〔2023〕179 号 http://www.pnxzf.gov.cn/zfxxgk/fdzdgknr/zdlyxxgk/qtzdgk/dczl/t16856780.shtml 「对未取得《农药广告审查证明》发布农药广告的行为,处广告费用一倍的罚款 760 元」）
场景: 农资经营部门店招牌 / 农资宣传册 / 经销批发名片 / 农资广告宣传单 / 农业合作社推广海报(典型 pestvet = 农药 + 兽药 合桶承诺类违规场景)
违规点: 农资广告同时含「无效退款」「保险公司保险」承诺性表述,违反《广告法》第二十一条 + 《农药广告审查发布规定》第十条 + 《兽药广告审查发布规定》第八条
法律依据: 《广告法》第二十一条 + 第五十八条 / 《农药广告审查发布规定》第十条 + 第十三条 / 《兽药广告审查发布规定》第八条 + 第十二条
原始违法广告语: |
  本社经营 农药 兽药 添加剂
  无效退款 保险公司保险
预期命中规则:
  - id: ad_signage_medical_art8_commitment
    severity: Warning
  - id: ad_signage_pesticide_art10_commitment
    severity: Violation
  - id: ad_signage_veterinary_art8_commitment
    severity: Warning
  - id: ad_signage_medical_insurance_commitment
    severity: Warning
处罚结果: 责令停止发布违法广告;典型处罚区间:广告费用 1-3 倍罚款(《广告法》第五十八条);广告费用无法计算或明显偏低的,处 10 万元以上 20 万元以下罚款;情节较重的,处广告费用 3-5 倍罚款,可吊销营业执照等。同源案例如辛集市***商贸有限公司发布违法农药广告案被辛集市市场监督管理局行政处罚;石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号对当事人发布含有「无效退款」承诺的农药广告进行行政处罚;平南县市场监督管理局 平市监处罚〔2023〕179 号对未取得《农药广告审查证明》发布农药广告的行为处广告费用一倍的罚款 760 元;长春市政务服务和数字化建设管理局对违法发布农药广告的行为依据《农药广告审查发布规定》第十三条 + 《广告法》第五十八条进行行政处罚
备注: 2025-03-14 泰安市市场监督管理局发布「农资产品广告发布提醒告诫函」,正值春耕备耕时节,根据《广告法》《农药广告审查发布规定》《兽药广告审查发布规定》等法律法规提醒告诫农资经营者:其中第九条明确「**农药、兽药广告中不得含有评比、排序、推荐、指定、选用、获奖等综合性评价内容和『无效退款』『保险公司保险』等承诺**」。该文件覆盖范围明确为「农资」即「农药 + 兽药 + 饲料 + 肥料」全谱农业投入品广告,故本 fixture 的 pestvet (农药 + 兽药 合桶)bucket 落点于此。本 fixture 的 `originalAdText` 同时含「无效退款」「保险公司保险」2 个跨域共享 keyword,这两个 keyword 同时命中 `ad_signage_medical_art8_commitment`(《医疗器械广告审查发布标准》第八条,Warning)+ `ad_signage_pesticide_art10_commitment`(《农药广告审查发布规定》第十条,Violation,本 fixture 主规则 1,对应 `category: pestvet`)+ `ad_signage_veterinary_art8_commitment`(《兽药广告审查发布规定》第八条,Warning,本 fixture 主规则 2,对应 `category: pestvet`)3 条规则 — 这是 fixture 设计中**故意的多桶 cross-hit 模式**:pestvet 是 spec 定义的「合桶」即「农药 + 兽药」复合 bucket,fixture 设计目标 pin 是 {pesticide_commitment + veterinary_commitment} 这 2 条 pestvet 桶规则,因 `无效退款` / `保险公司保险` 同时也是《医疗器械广告审查发布标准》第八条的 keyword,3 条规则同时命中是不可避免的(经 Python 脚本 `kw_map` 全量扫描确认:commitment 类 4 条规则[medical_art8 + pesticide_art10 + veterinary_art8 + (医疗承诺规则)]共享相同的「无效退款 / 保险公司保险」同源禁令字串,无法在不命中 medical_art8 的前提下单独命中 pestvet 双规则),对应现实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「农资店既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),hits dedup by `(ruleId, matchedText)`,set 严格等于 3 个 rule id。这与 agricultural 桶 nz_01 的 3-rule cross-bucket hit 模式同理(但 nz_01 是单一农药场景,本 fixture 是 pestvet = 农药 + 兽药 复合场景,文本明确「本社经营 农药 兽药 添加剂」),亦与 finance 桶 bbxj_01 的「`稳赚不赔` 跨 art25_fin_prm + finance_316_art3_2_fraud_guarantee 双规则」、cosmetic 桶 qxb_01 的「`祛斑` 跨 cosmetic_art17_special_class + cosmetic_art23_medical_claim 双规则」同理 — 都是 fixture 设计中的真实世界多规则同时违法场景。本批 fixture 刻意回避 `100% 安全` / `绝对安全` / `零副作用` / `保证有效`(`ad_signage_pesticide_art4_assertion` 跨桶污染)+ `高效低毒`(pesticide_art4_assertion 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃独占 keyword)+ `专家推荐` / `院士推荐` / `教授推荐` / `研究所推荐` / `学会认证` / `用户证言`(`ad_signage_pesticide_art4_endorsement` + `ad_signage_veterinary_art4_endorsement` 跨桶污染)+ `拌料口服` / `随意加大剂量` / `人畜同用`(pesticide/veterinary `art4_safety_violation` 双桶共享)+ `食用安全`(pesticide_art4_safety_violation 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃)+ `保证 100% 有效`(pesticide_art10_commitment 独占,但含 `100%` 触发 art9_abs_pct + art11_data_citation 双规则污染,且含 `100% 有效` 触发 medical/veterinary 多桶污染,跨规则风险太高)+ `100%`(art9_abs_pct 跨规则污染)+ `农药广审文号` / `兽药广审文号` / `农药广告批准文号` / `兽药广告批准文号` / `(2016) 农药广审` / `(2016) 兽药广审`(pesticide_art11_approval_no / veterinary_art10_approval_no 独占,但本 fixture 设计为 commitment 3-rule cross-bucket hit 故舍弃独占 keyword)+ `未取得审查` / `未经审查` / `未审批` / `未通过审查` / `未审查`(`ad_signage_signage_art46_pre_review` 跨规则 + signage 桶污染)+ `全杀` / `万能杀虫` / `对 X 病虫草均有效`(pesticide_art3_overrange 独占,但本 fixture 设计为 commitment cross-bucket hit 故舍弃)+ `金奖` / `销量第一` / `首选` / `唯一`(pesticide/veterinary/cosmetic 跨桶污染)+ `%` / `百分之`(`ad_signage_art11_data_citation` 跨规则污染)+ `研究所推荐` / `学会推荐` / `专家推荐`(5-rule 跨域污染)+ `保本高收益` / `稳赚不赔` / `无风险`(finance 跨桶污染)等共享 keyword,确保 set 命中严格等于 {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment}。同类常见变体:任何含「无效退款」或「保险公司保险」的农资 / 农药 / 兽药 / 医疗广告均会同时触发这 3 条规则(对应现实执法「同源禁令」);未来若补 `text_pestvet_ny_sy_02` 等额外 pestvet fixture,`ad_signage_pesticide_art11_approval_no`(独占 keyword `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ `ad_signage_veterinary_art10_approval_no`(独占 keyword `兽药广审文号` / `兽药广告批准文号` / `(2016) 兽药广审`)2 条 pestvet 桶独占规则可同时使用以实现「pure pestvet 2-rule hit」(经预模拟确认 `本社经营 农药广审文号 兽药广审文号 齐全` 命中 {pesticide_art11_approval_no + veterinary_art10_approval_no},set 严格等于 2 个 rule id,无 medical 跨桶污染,可作为 pestvet 桶的「pure pestvet 2-rule」fixture 候选);亦可补 `ad_signage_pesticide_art4_assertion`(独占 keyword `高效低毒`)+ `ad_signage_pesticide_art3_overrange`(独占 keyword `全杀` / `万能杀虫` / `对 X 病虫草均有效`,注意「彻底根除」含 med_art7_technicality 共享 keyword `根除` 不能用)+ `ad_signage_pesticide_art2_unregistered`(独占 keyword `农药登记证` / `农药登记号` / `PD` / `PDN`,但 `PD` 过短可能误命中)等单边 pestvet 独占规则。内容来自 WebSearch snippet,非 WebFetch 直读(WebFetch 持续 blocked on `.gov.cn` / `scjgj.taian.gov.cn`)。
---

# 农资经营部「本社经营 农药 兽药 添加剂 / 无效退款 / 保险公司保险」违法广告案(2025)

2025 年 3 月 14 日,泰安市市场监督管理局发布「**农资产品广告发布提醒告诫函**」,正值春耕备耕时节,该局根据《中华人民共和国广告法》《农药广告审查发布规定》《兽药广告审查发布规定》《肥料登记管理办法》等法律法规的规定,提醒告诫农资经营者规范农资广告经营活动。其中关键条款:

1. **未经审查不得发布** —— 《广告法》第四十六条规定,农药、兽药广告发布前应当由有关部门对广告内容进行审查,未经审查不得发布。
2. **必须明示广告批准文号** —— 第七条规定,**农药、兽药广告的批准文号应当列为广告内容同时发布**。
3. **禁止断言功效安全** —— 第八条规定,农药、兽药、饲料和饲料添加剂广告不得含有表示功效、安全性的断言或者保证。
4. **禁止利用机构推荐** —— 第八条规定,不得利用科研单位、学术机构、技术推广机构、行业协会或者专业人士、用户的名称或者形象作推荐、证明。
5. **禁止说明有效率** —— 第八条规定,不得含有说明有效率的内容。
6. **禁止综合性评价** —— 第九条规定,不得含有评比、排序、推荐、指定、选用、获奖等综合性评价内容。
7. **禁止无效退款承诺** —— 第九条规定,不得含有「**无效退款**」「**保险公司保险**」等承诺。

同源案例如:

- **国家市场监督管理总局 2025-08-13 转载发布「北京发布农药类驱蚊产品广告合规提示」**:北京市市场监督管理局 6 点合规提示,其中第 6 点明确《农药广告审查发布标准》第十条「**农药广告不得含有『无效退款』『保险公司保险』等承诺**」。
- **辛集市***商贸有限公司发布违法农药广告案**:在经营场所发布含「经销批发 高效杀虫剂 无效退款」农药广告,违反《农药广告审查发布规定》第十条 + 《广告法》第五十八条,由辛集市市场监督管理局行政处罚。
- **石台县市场监督管理局 广告案件 行政处罚信息摘要 石市监处罚〔2022〕120 号**:当事人发布含有「无效退款」承诺的农药广告,违反《农药广告审查发布规定》第十条 + 第十三条。
- **平南县市场监督管理局 平市监处罚〔2023〕179 号**:对未取得《农药广告审查证明》发布农药广告的行为,处广告费用一倍的罚款 760 元。
- **长春市政务服务和数字化建设管理局「对发布违法农药广告的处罚」**:引用《农药广告审查发布规定》第十条 + 第十三条 + 《广告法》第五十八条法律责任。

经查,当事人作为农资经营者在农资经营部门店招牌、农资宣传册、经销批发名片、农资广告宣传单或农业合作社推广海报中发布「**本社经营 农药 兽药 添加剂**」「**无效退款 保险公司保险**」等农资广告内容。「**无效退款**」「**保险公司保险**」属于《农药广告审查发布规定》第十条 + 《兽药广告审查发布规定》第八条明确禁止的承诺性表述,直接违反《广告法》第二十一条「**农药、兽药、饲料和饲料添加剂广告的,应当遵守国家有关规定**」与《农药广告审查发布规定》第十条「**农药广告不得含有『无效退款』『保险公司保险』等承诺**」+ 《兽药广告审查发布规定》第八条「**兽药广告不得含有『无效退款』『保险公司保险』等承诺**」+ 第十三条 / 第十二条法律责任条款。该禁令与《医疗器械广告审查发布标准》第八条形成同源三项广告审查标准禁令,任一含此表述的特殊商品广告(医疗 / 农药 / 兽药)均同时落入相应 3 条规则触发范围。

> **法条原文(广告法 §21)**:
> 农药、兽药、饲料和饲料添加剂广告的,应当遵守国家有关规定。

> **法条原文(农药广告审查发布规定 §10)**:
> 农药广告不得含有「无效退款」「保险公司保险」等承诺。

> **法条原文(兽药广告审查发布规定 §8)**:
> 兽药广告不得含有「无效退款」「保险公司保险」等承诺。

> **法条原文(广告法 §58)**:
> 违反本法第二十一条规定的农药、兽药、饲料和饲料添加剂广告的,处广告费用一倍以上三倍以下的罚款;广告费用无法计算或者明显偏低的,处十万元以上二十万元以下的罚款;情节较重的,处广告费用三倍以上五倍以下的罚款,可以吊销营业执照等。

> **法条原文(农药广告审查发布规定 §13)**:
> 违反本规定发布广告,《广告法》及其他法律法规有规定的,依照有关法律法规规定予以处罚。法律法规没有规定的,对负有责任的广告主、广告经营者、广告发布者,处以违法所得三倍以下但不超过三万元的罚款;没有违法所得的,处以一万元以下的罚款。

> **法条原文(兽药广告审查发布规定 §12)**:
> 违反本规定发布广告,《广告法》及其他法律法规有规定的,依照有关法律法规规定予以处罚。

关键词「无效退款」「保险公司保险」2 个 keyword 同时命中 3 条规则:`ad_signage_medical_art8_commitment`(《医疗器械广告审查发布标准》第八条,Warning)+ `ad_signage_pesticide_art10_commitment`(《农药广告审查发布规定》第十条,Violation,本 fixture 主规则 1,对应 `category: pestvet`)+ `ad_signage_veterinary_art8_commitment`(《兽药广告审查发布规定》第八条,Warning,本 fixture 主规则 2,对应 `category: pestvet`) — 这是 fixture 设计中的**故意的多桶 cross-hit 模式**:pestvet 是 spec 定义的「合桶」即「农药 + 兽药」复合 bucket,fixture 设计目标 pin 是 {pesticide_commitment + veterinary_commitment} 这 2 条 pestvet 桶规则,因 `无效退款` / `保险公司保险` 同时也是《医疗器械广告审查发布标准》第八条的 keyword,3 条规则同时命中是不可避免的(经 Python 脚本 `kw_map` 全量扫描确认:commitment 类 4 条规则共享相同的「无效退款 / 保险公司保险」同源禁令字串,无法在不命中 medical_art8 的前提下单独命中 pestvet 双规则),对应现实执法中「同一违法广告语同时违反医疗 / 农药 / 兽药三类特殊商品广告审查标准」的情形(对应现实中「农资店既可能按农药也可能按兽药监管,部分广告用语如『无效退款』跨越多个特殊商品广告审查标准」),hits dedup by `(ruleId, matchedText)`,set 严格等于 3 个 rule id。本批 fixture 刻意回避 `100% 安全` / `绝对安全` / `零副作用` / `保证有效`(`ad_signage_pesticide_art4_assertion` 跨桶污染)+ `高效低毒`(pesticide_art4_assertion 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃独占 keyword)+ `专家推荐` / `院士推荐` / `教授推荐` / `研究所推荐` / `学会认证` / `用户证言`(`ad_signage_pesticide_art4_endorsement` + `ad_signage_veterinary_art4_endorsement` 跨桶污染)+ `拌料口服` / `随意加大剂量` / `人畜同用`(pesticide/veterinary `art4_safety_violation` 双桶共享)+ `食用安全`(pesticide_art4_safety_violation 独占,但本 fixture 设计为 3-rule cross-bucket hit 故舍弃)+ `保证 100% 有效`(pesticide_art10_commitment 独占,但含 `100%` 触发 art9_abs_pct + art11_data_citation 双规则污染,且含 `100% 有效` 触发 medical/veterinary 多桶污染,跨规则风险太高)+ `100%`(art9_abs_pct 跨规则污染)+ `农药广审文号` / `兽药广审文号` / `农药广告批准文号` / `兽药广告批准文号` / `(2016) 农药广审` / `(2016) 兽药广审`(pesticide_art11_approval_no / veterinary_art10_approval_no 独占,但本 fixture 设计为 commitment 3-rule cross-bucket hit 故舍弃独占 keyword)+ `未取得审查` / `未经审查` / `未审批` / `未通过审查` / `未审查`(`ad_signage_signage_art46_pre_review` 跨规则 + signage 桶污染)+ `全杀` / `万能杀虫` / `对 X 病虫草均有效`(pesticide_art3_overrange 独占,但本 fixture 设计为 commitment cross-bucket hit 故舍弃)+ `金奖` / `销量第一` / `首选` / `唯一`(pesticide/veterinary/cosmetic 跨桶污染)+ `%` / `百分之`(`ad_signage_art11_data_citation` 跨规则污染)+ `研究所推荐` / `学会推荐` / `专家推荐`(5-rule 跨域污染)+ `保本高收益` / `稳赚不赔` / `无风险`(finance 跨桶污染)等共享 keyword,确保 set 命中严格等于 {ad_signage_medical_art8_commitment, ad_signage_pesticide_art10_commitment, ad_signage_veterinary_art8_commitment}。同类常见变体:任何含「无效退款」或「保险公司保险」的农资 / 农药 / 兽药 / 医疗广告均会同时触发这 3 条规则(对应现实执法「同源禁令」);未来若补 `text_pestvet_ny_sy_02` 等额外 pestvet fixture,`ad_signage_pesticide_art11_approval_no`(独占 keyword `农药广审文号` / `农药广告批准文号` / `(2016) 农药广审`)+ `ad_signage_veterinary_art10_approval_no`(独占 keyword `兽药广审文号` / `兽药广告批准文号` / `(2016) 兽药广审`)2 条 pestvet 桶独占规则可同时使用以实现「pure pestvet 2-rule hit」(经预模拟确认 `本社经营 农药广审文号 兽药广审文号 齐全` 命中 {pesticide_art11_approval_no + veterinary_art10_approval_no},set 严格等于 2 个 rule id,无 medical 跨桶污染,可作为 pestvet 桶的「pure pestvet 2-rule」fixture 候选);亦可补 `ad_signage_pesticide_art4_assertion`(独占 keyword `高效低毒`)+ `ad_signage_pesticide_art3_overrange`(独占 keyword `全杀` / `万能杀虫` / `对 X 病虫草均有效`,注意「彻底根除」含 med_art7_technicality 共享 keyword `根除` 不能用)+ `ad_signage_pesticide_art2_unregistered`(独占 keyword `农药登记证` / `农药登记号` / `PD` / `PDN`,但 `PD` 过短可能误命中)等单边 pestvet 独占规则。
