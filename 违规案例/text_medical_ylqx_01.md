---
来源: https://www.mas.gov.cn/xxgk/openness/detail/content/69fbfe0e8866888e3e8b4576.html （马鞍山市市场监管局 行政处罚信息公开 马市监处罚〔2026〕193号 马鞍山恒硕商贸有限公司发布虚假医疗器械广告案 2026-05-07）
场景: 医疗器械店铺 / 网店商品标题与详情页
违规点: 家用医疗器械广告未显著标明"禁忌 / 注意事项"提示语,且含有"无效退款"承诺性内容,违反《医疗器械广告审查发布标准》第四条、第八条
法律依据: 《医疗器械广告审查发布标准》第四条 + 第八条 / 《广告法》第十六条 + 第五十八条
原始违法广告语: |
  本医疗器械 家用呼吸机
  禁忌 注意事项
  无效退款
预期命中规则:
  - id: ad_signage_medical_art4_selfuse_label
    severity: Warning
  - id: ad_signage_medical_art5_contraindication
    severity: Warning
  - id: ad_signage_medical_art6_producer
    severity: Info
  - id: ad_signage_medical_art8_commitment
    severity: Warning
  - id: ad_signage_pesticide_art10_commitment
    severity: Violation
  - id: ad_signage_veterinary_art8_commitment
    severity: Warning
处罚结果: 责令停止发布广告,罚款 100 元（广告费 100 元,从轻裁量）
备注: 2026-05-07 马鞍山市市场监管局公布。当事人在美团网店销售"家用呼吸机"商品,商品标题与详情页含"无效退款"承诺性内容(违反《医疗器械广告审查发布标准》第八条),且推荐给个人使用的医疗器械广告未显著标明"请仔细阅读产品说明书或者在医务人员的指导下购买和使用"(违反第四条 + 第九条)。文本故意同时包含「家用呼吸机」(art4_selfuse_label)+「禁忌 / 注意事项」文本(art5_contraindication)+「医疗器械」(art6_producer)+「无效退款」(art8_commitment)。注意:「无效退款」是 medical / pesticide / veterinary 三域共享关键词(《农药广告审查发布规定》第十条、《兽药广告审查发布规定》第八条均有相同禁令),因此 fixture 的 expected 同时包含 medical 域承诺规则 + pesticide_art10_commitment + veterinary_art8_commitment。
---

# 网店销售家用呼吸机"无效退款"承诺违法广告案（2026 年）

2026 年 5 月,马鞍山市市场监管局对辖区某医疗器械销售网店立案调查,查实该网店在美团平台销售"家用呼吸机"商品,在商品标题与详情页发布「本医疗器械 家用呼吸机」「禁忌 注意事项」「无效退款」等内容,违反《医疗器械广告审查发布标准》第四条（推荐给个人使用的医疗器械产品广告,必须在广告中标明"请仔细阅读产品说明书或者在医务人员的指导下购买和使用",未标明的依《广告法》第五十八条处罚）、第八条（医疗器械广告不得含有"无效退款""保险公司保险"等承诺）。

依据《广告法》第五十八条第一款第（一）项,鉴于广告费用仅 100 元且未实际成交,马鞍山市局责令当事人停止发布违法广告,处以罚款 100 元。

> **法条原文(医疗器械广告审查发布标准 §4)**:
> 推荐给个人使用的医疗器械产品广告,必须在广告中标明"请仔细阅读产品说明书或者在医务人员的指导下购买和使用"。

> **法条原文(医疗器械广告审查发布标准 §8)**:
> 医疗器械广告中不得含有"无效退款""保险公司保险"等承诺。

同类常见变体:「血压计 无效退款」「血糖仪 保险公司保险」「雾化器 保证有效」「按摩仪 100% 有效」均属家用医疗器械虚假 / 违规宣传,OCR 检出后应命中 `ad_signage_medical_art4_selfuse_label`(家用医疗器械未标明使用提示)+ `ad_signage_medical_art5_contraindication`(禁忌 / 注意事项声明相关)+ `ad_signage_medical_art6_producer`(医疗器械广告需标明生产企业信息)+ `ad_signage_medical_art8_commitment`(含无效退款承诺)+ `ad_signage_pesticide_art10_commitment`(共享关键词)+ `ad_signage_veterinary_art8_commitment`(共享关键词)。