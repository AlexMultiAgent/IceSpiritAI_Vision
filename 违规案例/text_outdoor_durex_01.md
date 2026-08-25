---
来源: 微信实拍截图 OCR（无 gov URL；图像素材来源：wechat_screenshot_20260819；OCR 识别自公交 388-12 路线车身广告 — SKYWORTH 创维汽车 / 悦椿足道店铺前）
场景: 公交车身广告 / 户外流动媒体广告 / 商业品牌公益装类广告
违规点: 商业品牌「杜蕾斯」在公交车身广告上宣称「首个公益装」，使用「首个」绝对化用语，违反《广告法》第九条第（三）项「使用『国家级』『最高级』『最佳』等用语」+ 化妆品类目专用「广告法 §9 化妆品变体」共同构成跨桶绝对化用语违规（绝对化桶 + 化妆品桶）
法律依据: 《中华人民共和国广告法》第九条第（三）项 + 第五十七条 / 《化妆品广告管理办法》（与「首个」绝对化用语相关条款）
原始违法广告语: |
  燃情公益红 守护爱她
  经典红 | 杜蕾斯首个公益装
  durex
  公交 388-12
  SKYWORTH 创维汽车
  悦椿足道
预期命中规则:
  - id: ad_signage_art9_abs_top
    severity: Warning
  - id: cosmetic_art9_abs_extended
    severity: Warning
处罚结果: 待定（图像素材，无原始处罚决定书可援引；以平台自查为准；若后续被监管部门立案查处，可参照「格勒博意利广州『全球首家』虚假宣传案」国家市场监督管理总局 2026-07-30 通报「六起市场化活动违法违规案件」同源量级 — 见 text_absolute_first_01 引用 samr.gov.cn 通报）
备注: OCR 识别自公交车身广告截图（杜蕾斯公交车身内容实际位于 inbox 文件 `xiaoyuan_yuliang_zytmhqs_douyin.jpg`，原 inbox 文件采集时 slug 命名与内容不符，按内容实际匹配「durex」场景名升档）。本 fixture 设计为 **2-rule 跨桶 hit（absolute + cosmetic）**：`首个` 同时命中 `ad_signage_art9_abs_top`（绝对化桶 §9 第（三）项）和 `cosmetic_art9_abs_extended`（化妆品桶对「首个」kw 的 §9 变体 — 因广告主为杜蕾斯属化妆品类目），hits dedup by `(ruleId, matchedText)`，set = 2 个 rule id，均 Warning 严重度。本 fixture 按用户原 plan 选用 `category=outdoor`（因户外属性是首要 — 公交车身属户外流动媒体广告场景），但**实际 scan 命中是 absolute + cosmetic 跨桶双规则**，未命中任何 outdoor-specific rule（art14_cert_no / art4_unaudited / art10_misleading / art32_government / art32_school_hospital / art32_traffic / art32_roof / art32_cultural_relic / art32_municipal / art32_heritage / art32_airport 11 条规则 keyword 全部不命中 — 图像无「楼顶广告」「户外广告登记证」「交通信号灯」「学校门口」等 art32_* keyword；亦无「权威推荐」「专家推荐」「国家免检」「质量免检」「驰名商标」等 art10_misleading keyword）。set pin 稳定 + 2-rule cross-bucket hit 反映真实执法中「**化妆品类绝对化用语变体**」的真实情形（化妆品广告 §9 变体 + 通用 §9 变体）。fixture 设计刻意回避 `专家推荐`（跨 outdoor + edu + medical + pesticide + veterinary 5 桶 5 规则共享，5-rule cross-bucket hit）+ `驰名商标`（outdoor + cosmetic 2 桶共享，2-rule cross-bucket hit）+ `%` / `百分百` / `百分之百`（art11_data_citation + art9_abs_pct 跨规则污染）+ `100% 安全` / `绝对安全` / `100% 有效`（medical + pesticide + veterinary + cosmetic 4 桶跨域污染）+ `治疗` / `治愈` / `疗效` / `消炎`（signage + cosmetic 跨桶共享）等跨桶跨规则污染 keyword，保 set 干净。同类常见变体：art9_abs_top 独占 keyword 集合 `最佳` / `最好` / `顶级` / `唯一` / `首家` / `首选` / `领军品牌` / `首屈一指` 均命中同一规则；cosmetic_art9_abs_extended 独占 keyword 集合 `顶级` / `首选` / `唯一` / `独家` / `最强` / `最佳` / `最好` / `第一名` / `全球第一` 均命中同一规则。本 fixture 是「**化妆品类商业品牌在户外流动媒体广告宣称绝对化用语**」典型违规场景 pin，对应真实世界中「化妆品 / 个护类商业品牌在户外广告中宣称『首家』/『首个』/『全球第一』」的执法情形。
---

# 公交车身广告「杜蕾斯首个公益装」化妆品类绝对化用语案（2026-08-19 OCR）

2026 年 8 月 19 日采集的公交 388-12 路线车身广告实拍截图，广告主体为杜蕾斯品牌，画面文字主体为「**燃情公益红 守护爱她**」情感诉求 +「**经典红 | 杜蕾斯首个公益装**」产品宣传语 + 「**durex**」品牌 logo，画面背景有 SKYWORTH 创维汽车 / 悦椿足道店铺招牌。

该广告在公交车身（户外流动媒体广告场景）使用「**首个**」绝对化用语：
- 杜蕾斯作为商业品牌，在商品包装 / 包装变体上使用「首个公益装」表述，没有可验证的事实依据 / 没有第三方权威认证；
- 该「首个」用语指向广告法 §9 第（三）项「使用『国家级』『最高级』『最佳』等用语」的禁止情形；
- 因广告主为杜蕾斯属化妆品类目，同时落入化妆品桶对「首个」kw 的 §9 变体规则（cosmetic_art9_abs_extended），构成绝对化桶 + 化妆品桶的跨桶双重违规情形。

按《广告法》第五十七条，对广告主处二十万元以上一百万元以下的罚款。公交车身广告属于户外广告，按《广告法》第三十二条 + 《户外广告登记管理规定》（已废止但部分内容并入《广告法》第二章广告内容准则性规定）+ 各地方户外广告管理条例执行。

> **法条原文（广告法 §9 第（三）项）**：
> 广告不得有下列情形：...（三）使用「国家级」「最高级」「最佳」等用语；...

> **法条原文（广告法 §57 第（一）项）**：
> 发布有本法第九条第一款规定的禁止情形之一的广告的，由市场监督管理部门责令改正，对广告主处二十万元以上一百万元以下的罚款。

OCR 检出后应判定为：
- 「**首个**」命中 `ad_signage_art9_abs_top`（广告法 §9 第（三）项 + §57，绝对化桶，Warning）
- 「**首个**」同时命中 `cosmetic_art9_abs_extended`（化妆品桶对「首个」kw 的 §9 变体，Warning）

set = 2 个 rule id，均 Warning 严重度，反映真实世界中「**化妆品类商业品牌在户外广告宣称绝对化用语**」的跨桶双重违法情形。本 fixture 是公交车身 / 户外流动媒体广告「**绝对化用语**」典型违规场景 pin，category 字段虽设为 outdoor（按用户原 plan「户外属性是首要」），但**实际 rule 命中是 absolute + cosmetic 跨桶双规则**，未命中任何 outdoor-specific rule（图像无 art14_cert_no / art4_unaudited / art10_misleading / art32_* 等 11 条 outdoor 规则 keyword）— 此 fixture 用作「户外场景下化妆品绝对化用语」违规场景的工程 pin，未来读者可在 fixture 列表中按 `category=outdoor` 检索到此案例以理解「户外场景 ≠ 户外桶专属 rule 命中」的边界情形。同类常见变体：art9_abs_top 独占 keyword 集合 `最佳` / `最好` / `顶级` / `唯一` / `首家` / `首选` / `领军品牌` / `首屈一指` 均命中同一规则；cosmetic_art9_abs_extended 独占 keyword 集合 `顶级` / `首选` / `唯一` / `独家` / `最强` / `最佳` / `最好` / `第一名` / `全球第一` 均命中同一规则。