---
来源: https://www.samr.gov.cn/xw/zj/art/2025/art_bcea7ffb220c456dbbd0333fed5a651d.html （国家市场监督管理总局 公布六起通过保健品虚假宣传进行"内卷式"竞争典型案例 2025-07-22；案例 2「江苏省张家港市德积徳优迪斯超市」江苏省苏州市张家港保税区市场监督管理局 处罚通报）
场景: 保税区超市 / 中老年消费者健康讲座视频滚动播放
违规点: 普通食品宣称针对特定疾病患者人群具有疗效,违反《广告法》第十七条 + 《反不正当竞争法》第八条
法律依据: 《广告法》第十七条 + 第五十八条 / 《反不正当竞争法》第八条第一款 + 第二十条
原始违法广告语: |
  本蜂皇浆冻干粉胶囊
  专为糖尿病患者 高血压患者 冠心病患者
  关节炎患者 骨质疏松患者
预期命中规则:
  - id: ad_signage_signage_food_disease_target
    severity: Violation
  - id: ad_signage_non_medical_institution_disease_advertisement
    severity: Violation
  备注_anchor_gate_v0_1_54_commit_4: commit 4 起 med_art6_indications 加 categoryAnchors(医疗/医院/医师/诊所),本 fixture 文本(普通食品宣称疾病人群)无医疗锚点,med_art6_indications 不再触发 — 锚点门正确抑制跨域医疗广告规则误命中食品广告。
  备注_v0_1_55: v0.1.55 起新增 ad_signage_non_medical_institution_disease_advertisement 规则(《广告法》§17 + §58,signage / Violation,keywords 含「关节炎 / 肩周炎 / 风湿 / 类风湿 / 腱鞘炎 / 骨质增生」等),本 fixture 文本含「关节炎患者」「骨质疏松患者」,关节炎 keyword 命中;absent anchor(医院/医师/药品/OTC/国食健字/制药/处方/临床/保健食品 等)在本 fixture 文本均不出现,absent gate 放行。v0.1.57 之前 fixture 期望 set=[food_disease_target] 与实际 hit set={food_disease_target, non_medical_institution_disease_advertisement} 不等,v0.1.57 同步把 non_medical_institution_disease_advertisement 加入 fixture 预期 set 严格 pin(此 pin 是 v0.1.55 改动的遗漏,v0.1.57 才被发现并补齐)。
处罚结果: 罚款 20 万元
备注: 2025-07-22 市场监管总局公布「六起通过保健品虚假宣传进行'内卷式'竞争典型案例」之一。江苏省张家港市德积徳优迪斯超市在经营场所内通过播放视频向中老年人推介销售"蜂亿健蜂皇浆冻干粉胶囊",宣称该商品具有治疗糖尿病、高血压、冠心病、肝硬化、肾功能衰竭等多种疾病的功效;该商品仅为普通食品(非保健食品),不具有疾病治疗功能。本 fixture 的 `originalAdText` 严格使用「糖尿病患者/高血压患者/冠心病患者/关节炎患者/骨质疏松患者」5 个 food_disease_target 独占 keyword(均 exclusive,hits dedup by `(ruleId, matchedText)`,set 严格等于 1 个 rule id),原案「治疗」类表述在本 fixture 中刻意回避 — `治疗` / `治愈` / `疗效` / `消炎` 与 `cosmetic_art23_medical_claim` 共享,会跨规则污染 set 命中。同类常见变体「癌症病人」「肿瘤病人」「心脑血管病人」「便秘患者」「痔疮患者」「前列腺患者」「男性健康」「妇科疾病」「白癜风」「牛皮癣」「抗癌」「防癌」均命中同一规则。内容来自 WebSearch snippet,非 WebFetch 直读。

> **v0.1.50 set 调整**:`关节炎` 是 `ad_signage_med_art6_indications` 的 3 字 keyword,与 `ad_signage_signage_food_disease_target` 的 5 字 keyword「关节炎患者」共现时,两个 ruleId 都会命中。原 fixture set 期望 `[food_disease_target]`,实际命中 set 是 `{food_disease_target, med_art6_indications}`。这是真实案例文本的合法 overlap(关节炎既是疾病人群,又是医疗广告管理办法第六条规范的诊疗范围),非规则污染。本批同步把 med_art6_indications 加入 fixture 的预期命中规则,使 set 严格相等。
---

# 普通食品宣称针对糖尿病/高血压/冠心病/关节炎/骨质疏松患者违法广告案（2025）

2025 年 7 月 22 日,国家市场监督管理总局公布「六起通过保健品虚假宣传进行'内卷式'竞争典型案例」。案例 2「江苏省张家港市德积徳优迪斯超市」由江苏省苏州市张家港保税区市场监督管理局立案查处,罚款 20 万元。当事人在其经营场所内通过播放宣传视频向中老年消费者推介销售「蜂亿健蜂皇浆冻干粉胶囊」,宣称该商品具有治疗糖尿病、高血压、冠心病、肝硬化、肾功能衰竭等多种疾病的功效。经查,当事人所售「蜂亿健蜂皇浆冻干粉胶囊」仅为普通预包装食品(非保健食品、非药品),不具有任何疾病治疗功能。

当事人的行为违反《广告法》第十七条「除医疗、药品、医疗器械广告外,其他广告不得涉及疾病治疗功能,不得使用医疗用语或者易使推销的商品与药品、医疗器械相混淆的用语」,以及《反不正当竞争法》第八条第一款「经营者不得对商品的功能、性能、质量等作虚假或者引人误解的商业宣传,欺骗、误导消费者」。

> **法条原文(广告法 §17)**:
> 除医疗、药品、医疗器械广告外,其他广告不得涉及疾病治疗功能,不得使用医疗用语或者易使推销的商品与药品、医疗器械相混淆的用语。

> **法条原文(反不正当竞争法 §8)**:
> 经营者不得对商品的性能、功能、质量、产地、用途、有效期限、销售状况或者经营者在提供商品或者服务过程中作出的允诺等作虚假或者引人误解的商业宣传,欺骗、误导消费者。经营者不得通过组织虚假交易、虚假评价等方式,帮助其他经营者进行虚假或者引人误解的商业宣传。

关键词「糖尿病患者」「高血压患者」「冠心病患者」「关节炎患者」「骨质疏松患者」均命中 `ad_signage_signage_food_disease_target`(Violation,普通食品涉及疾病人群),OCR 检出后应判定为普通食品虚假宣传涉及特定疾病患者,适用《广告法》第十七条 + 第五十八条、《反不正当竞争法》第八条第一款 + 第二十条。同类常见变体「癌症病人」「肿瘤病人」「心脑血管病人」「便秘患者」「痔疮患者」「前列腺患者」「男性健康」「妇科疾病」「妇科炎症」「白癜风」「牛皮癣」「抗癌」「防癌」等均命中同一规则。本批 fixture 刻意回避「治疗」「治愈」「疗效」「消炎」等 keyword,因其与 `cosmetic_art23_medical_claim`(化妆品医疗用语)共享,会跨规则污染 set 命中。