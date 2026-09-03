---
来源: https://scjg.jiaozuo.gov.cn/2025/05-13/157778.html （焦作市市场监管局、市卫健委联合 打击虚假宣传诱导就医行为专项整治工作 2025-05-13 通报）
场景: 体检预约平台 App / 微信公众号推文 / 体检中心店堂展架
违规点: 体检预约服务以"三甲专家"身份推荐并以"无效退款"作承诺,违反《医疗广告管理办法》第七条与《广告法》第十六条
法律依据: 《医疗广告管理办法》第七条 / 《广告法》第十六条 + 第五十八条
原始违法广告语: |
  体检预约服务
  三甲专家亲诊
  无效退款
预期命中规则:
  - id: ad_signage_med_art11_qualifications
    severity: Info
  - id: ad_signage_medical_art8_commitment
    severity: Warning
  - id: ad_signage_veterinary_art8_commitment
    severity: Warning
  备注: v0.1.54 起 pesticide 规则加 categoryAnchors 后,本 fixture 文本(体检预约平台 三甲专家亲诊 无效退款)不含农药域锚点,pesticide_art10_commitment 不再触发 — anchor gate 的预期行为。后续 commit 3/4 将同步处理 medical_art8_commitment / veterinary_art8_commitment 的跨域 anchor gate(medical/vet 规则届时也会要求 anchor)。当前 commit 2 状态仅去掉 pesticide 跨域 hit。
处罚结果: 责令停止发布违法广告,罚款 8 万元
备注: 2025-05-13 焦作市市场监管局、卫健委联合通报。某体检预约平台在 App 与公众号推文中以"三甲专家亲诊"作推荐（违反《医疗广告管理办法》第七条"医疗广告内容仅限于医疗机构第一名称等 8 项"），并以"无效退款"作承诺性内容（违反《广告法》第十六条 + 第五十八条）。注意:「无效退款」是 medical / pesticide / veterinary 三域共享关键词,因此 fixture 的 expected 同时包含 medical 域承诺规则 + pesticide_art10_commitment + veterinary_art8_commitment。同类常见变体:「三甲专家亲诊」「三甲专家团队」「三甲医师」「体检预约 主任医师」「治不好不要钱」「签约体检 无效退款」「无效退款」。
---

# 体检预约平台"三甲专家亲诊 无效退款"违法广告案（2025 年）

2025 年 5 月,焦作市市场监管局、市卫健委联合开展「打击虚假宣传诱导就医行为」专项整治,通报某体检预约平台在 App 与公众号推文中发布「体检预约服务」「三甲专家亲诊」「无效退款」等内容。该平台未取得《医疗广告审查证明》,以"三甲专家"身份作推荐,违反《医疗广告管理办法》第七条（医疗广告内容仅限于医疗机构第一名称、地址、所有制形式等 8 项,不得利用专业人士名义作推荐）,并以"无效退款"作承诺性内容,违反《广告法》第十六条与第五十八条。

依据《广告法》第五十八条,焦作市市场监管局责令当事人停止发布违法广告,在相应范围内消除影响,并处 8 万元罚款。

> **法条原文(医疗广告管理办法 §7)**:
> 医疗广告的内容仅限于以下项目:(一)医疗机构第一名称;(二)医疗机构地址;(三)所有制形式;(四)医疗机构类别;(五)诊疗科目;(六)床位数;(七)接诊时间;(八)联系电话。

同类常见变体:「三甲专家亲诊」「三甲专家团队」「三甲医师推荐」「体检预约 主任医师」「治不好不要钱」「签约体检 无效退款」等,均属于以专家身份推荐 / 承诺性内容,OCR 检出后应命中 `ad_signage_med_art11_qualifications`(以专家身份推荐)+ `ad_signage_medical_art8_commitment`(含无效退款承诺)+ `ad_signage_pesticide_art10_commitment`(共享关键词)+ `ad_signage_veterinary_art8_commitment`(共享关键词)。