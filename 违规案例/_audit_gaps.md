# 违规案例审计清单

> 审计日期:2026-08-27
> 扫描图数:66(本文档含 01-66 共 66 节)
> 数据源:`违规案例/_违规档案总册.md`(922 行 / 14 桶)+ `app/src/main/assets/rules/ad_signage_rules.json`(v8 / 121 条)
> 桶归类口径:沿用总册(审计保守原则,不动总册)

## §桶汇总(本次审计,66 节明细)

| 桶 | 总图数 | 已覆盖 | 弱覆盖 | 未覆盖 | 建议动作 |
|---|---:|---:|---:|---:|---|
| `medical` | 8 | 7 | 1 | 0 | 强化 #55 关联规则 keywords |
| `absolute` | 8 | 7 | 1 | 0 | 强化 #42 关联规则 keywords |
| `food_function_claim` | 21 | 18 | 2 | 1 | 强化 #18/#28 关联规则 keywords;补新规则覆盖 #41(肺肽片) |
| `food_disease_target` | 4 | 4 | 0 | 0 | 保持 |
| `weight_loss` | 2 | 0 | 2 | 0 | 强化 #38/#54 关联规则 keywords |
| `education` | 7 | 2 | 3 | 2 | 强化 #10/#17/#51 关联规则 keywords;补新规则覆盖 #11/#32 |
| `agricultural` | 5 | 0 | 5 | 0 | 强化 #12/#13/#22/#23/#26 关联规则 keywords(沿用 Task 2 结论) |
| `pestvet` | 2 | 1 | 1 | 0 | 强化 #35 关联规则 keywords |
| `cosmetics` | 1 | 1 | 0 | 0 | 保持 |
| `signage` | 1 | 0 | 0 | 1 | 补新规则覆盖 #6(八一建军节) |
| `internet_ad` | (跨桶已计) | — | — | — | 规则已就位,待补示例图 |
| `realestate` | 5 | 1 | 2 | 2 | 强化 #1/#61 关联规则 keywords;补新规则覆盖 #3/#20 |
| `finance` | 0 | 0 | 0 | 0 | 规则已就位,待补示例图 |
| `minor` | 0 | 0 | 0 | 0 | 规则已就位,待补示例图 |
| `data_citation` | 2 | 2 | 0 | 0 | 保持 |
| `fake_data` | 0 | 0 | 0 | 0 | 规则已就位,待补示例图 |

> 说明:本表按「主桶计入」口径统计(每张图计入其 桶分类 字段中第一个桶;同一图多桶命中时只计 1 次),故各桶 总图数 之和 = 66(全部 66 张图都已归入某个主桶)。12 个主桶 + 4 个空桶 stub(internet_ad / finance / minor / fake_data)= 16 行;`internet_ad` 因场景以电商页为主,本审计中无图以该桶为主桶,标 `—`。
> 重点 gap:17 张弱覆盖中,4 张为本轮新发现关键词变体(#51 万通 PLC / #54 京东京造菊粉 / #55 京东京造番茄红素 / #61 哈尔滨信誉小区),其余为已知规则需批量扩展 keywords。

## 01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg` |
| 桶分类 | realestate × absolute × data_citation × fake_data |
| 违规描述 | 工地围挡「世界 500 强」「中国地产三强」属绝对化用语 + 数据引用无出处;「品牌地产 上市企业 购买居住享安心」属引人误解 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art11_data_citation |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art9_abs_top 加「三强/五百强」;ad_signage_art11_data_citation 加「500 强/世界 500 强」) |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十六条 + 第二十八条 + 《房地产广告发布规定》第三条 |

## 02_名师教育申论班_龙江第一_绝对化用语.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `02_名师教育申论班_龙江第一_绝对化用语.jpg` |
| 桶分类 | education × absolute × data_citation |
| 违规描述 | 「龙江第一」属绝对化最高级用语;「8 年以上教学经验 / 申论 80+」属数据无出处 + 身份背书无依据;「上岸为止」属诱导承诺 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art9_edu_abs, ad_signage_art11_data_citation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十四条 + 第二十八条 |

## 03_银泰集茶巷_品牌加冕财富启航_地产广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `03_银泰集茶巷_品牌加冕财富启航_地产广告.png` |
| 桶分类 | realestate × fake_data |
| 违规描述 | 「品牌加冕 财富实力启航」+「打造国潮茶文化主题商街」属对未来收益与规划的无依据承诺;「总价 68 万起」价格违规(未含均价/最高价) |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 新增 keywords 到(ad_signage_art26_re_prm 加「财富启航/财富实力/创富」;ad_signage_re_art26_planned_facility 加「主题商街/国潮茶文化」) |
| 关联法条 | 《广告法》第九条第(八)项 + 第二十六条 + 第二十八条 + 《房地产广告发布规定》第四条 |

## 04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg` |
| 桶分类 | medical × signage |
| 违规描述 | 户外招牌宣称「专业医疗机构」「全国连锁」属无依据表述;「口腔扫描/根管治疗」涉及医疗技术;「全瓷牙 当天戴」属治疗效果保证 |
| 现行覆盖规则 | ad_signage_signage_disease_prevention, ad_signage_med_art6_indications, ad_signage_med_art7_technicality |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十六条 + 第五十八条 + 《医疗广告管理办法》第三条 / 第七条 |

## 05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg` |
| 桶分类 | medical × absolute |
| 违规描述 | 「首选」属最高级绝对化用语;「心脑血管疾病 找庞洪飞」「糖尿病高血压 找庞洪飞」属疾病治疗承诺 + 医生代言;医保定点身份背书 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_signage_disease_prevention, ad_signage_med_art11_qualifications |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十六条 + 《医疗广告管理办法》第三条 / 第七条 / 第十六条 |

## 06_八一建军节宣传海报_商业广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `06_八一建军节宣传海报_商业广告.png` |
| 桶分类 | signage × fake_data |
| 违规描述 | 商业主体(哈尔滨胜利加油站)借「八一·建军节」「中国人民解放军建军97周年」「爱我中华 强我军威」「向伟大的军人们致敬」军政形象 + 国家庆典元素营销 |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 补新规则(建议 ruleId:`ad_signage_signage_military_political_marketing`,category=signage,severity=Violation,keywords 加「八一/建军节/中国人民解放军/军政形象/致敬军人/爱我中华/强我军威」) |
| 关联法条 | 《广告法》第九条第(七)项 + 第二十八条 + 《公益广告促进和管理暂行办法》 + 第五十七条 / 第五十五条 |

## 07_保健食品8大优势_提高免疫力消炎止痛.png

| 字段 | 值 |
|---|---|
| 文件名 | `07_保健食品8大优势_提高免疫力消炎止痛.png` |
| 桶分类 | food_function_claim × food_disease_target |
| 违规描述 | 「8 大优势 NUTRIENTS」「提高人体免疫力」「改善营养 补充脑力」「消炎止痛」涉及保健食品功能宣称 + 疾病治疗用语 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 08_北大荒椴树蜜电商页_提高免疫力消炎止痛.png

| 字段 | 值 |
|---|---|
| 文件名 | `08_北大荒椴树蜜电商页_提高免疫力消炎止痛.png` |
| 桶分类 | food_function_claim × food_disease_target × internet_ad |
| 违规描述 | 普通食品(蜂蜜)在京东电商页宣称「提高人体免疫力」「消炎止痛」涉及疾病治疗 + 保健功能;「改善营养 补充脑力」「增强食欲」属保健功能宣称 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention, internet_art6_identifiable |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 + 第五十八条 |

## 09_俄罗斯椴树蜜电商页_蜂珍盛宴八大自然优势.png

| 字段 | 值 |
|---|---|
| 文件名 | `09_俄罗斯椴树蜜电商页_蜂珍盛宴八大自然优势.png` |
| 桶分类 | food_function_claim × food_disease_target × internet_ad |
| 违规描述 | 蜂王浆 + 蜂王胎 + 雄蜂蛹 + 蜂花粉 + 蜂蜜电商页宣称「改善营养 补充脑力」「增强食欲」「消炎止痛」+ 「无 0 添加」属保健功能 + 疾病治疗双重违规 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention, ad_signage_signage_food_safety_implication, internet_art6_identifiable |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 + 第五十八条 |

## 10_玉远龙公考_未进面0收费_教育承诺收益.png

| 字段 | 值 |
|---|---|
| 文件名 | `10_玉远龙公考_未进面0收费_教育承诺收益.png` |
| 桶分类 | education |
| 违规描述 | 「2025 省考笔试 未进面 0 收费」属教育广告明示保证性承诺;「快速提分」「巩固提升」属无依据培训效果承诺 |
| 现行覆盖规则 | ad_signage_art24_edu_guar |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art24_edu_guar 加「未进面 0 收费/上岸为止/快速提分/高效促学」) |
| 关联法条 | 《广告法》第二十四条 + 第二十八条 |

## 11_公安专项秋考刷题班_高效提分_教育承诺.png

| 字段 | 值 |
|---|---|
| 文件名 | `11_公安专项秋考刷题班_高效提分_教育承诺.png` |
| 桶分类 | education |
| 违规描述 | 「公安专项 秋考刷题班」公安类敏感考试培训;「高效提分」「冲刺模考」「抢靠前座位」属无依据培训效果 + 营销诱导 |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 新增 keywords 到(ad_signage_edu_art24_test_authority 加「警察老师主讲/公安专项/警考」;ad_signage_art24_edu_guar 加「高效提分/冲刺模考/封闭刷题班」) |
| 关联法条 | 《广告法》第二十四条 + 第二十八条 |

## 12_东北景椒辣妹子种子_早熟高产抗病_种子广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `12_东北景椒辣妹子种子_早熟高产抗病_种子广告.png` |
| 桶分类 | agricultural |
| 违规描述 | 「早熟 高产 抗病」「干鲜两用椒王」属种子产量 / 抗性 / 品质无依据承诺;「菜农种植基地专用」属推荐语无依据 |
| 现行覆盖规则 | ad_signage_art27_seed_yield_guarantee |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art27_seed_yield_guarantee 加「高产/抗病/早熟/干鲜两用/超高产/超高产王」) |
| 关联法条 | 《广告法》第二十七条 + 《种子法》第三十一条 + 《农作物种子标签和使用说明管理办法》 |

## 13_豌豆种子_多且饱满高产_种子广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `13_豌豆种子_多且饱满高产_种子广告.png` |
| 桶分类 | agricultural |
| 违规描述 | 「豌豆多且饱满 高产」「4 大豆种籽粒饱满」属种子产量 / 品质特征无依据承诺 |
| 现行覆盖规则 | ad_signage_art27_seed_yield_guarantee |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art27_seed_yield_guarantee 加「饱满/多且饱满/籽粒饱满/油亮饱满」单字粒径类) |
| 关联法条 | 《广告法》第二十七条 + 《种子法》第三十一条 |

## 14_红豆越桔_治疗泌尿系统感染_食品涉及疾病.png

| 字段 | 值 |
|---|---|
| 文件名 | `14_红豆越桔_治疗泌尿系统感染_食品涉及疾病.png` |
| 桶分类 | food_disease_target × food_function_claim |
| 违规描述 | 普通食品(红豆越桔)宣称「治疗一些疾病,如泌尿系统感染、消化不良」「抗炎、抗菌、利尿等作用」属疾病治疗 + 保健功能双重违规 |
| 现行覆盖规则 | ad_signage_signage_disease_prevention, ad_signage_signage_food_function_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 15_名师教育申论保分班_第一年未录取第二年半价.jpeg

| 字段 | 值 |
|---|---|
| 文件名 | `15_名师教育申论保分班_第一年未录取第二年半价.jpeg` |
| 桶分类 | education × absolute × data_citation |
| 违规描述 | 「龙江第一」绝对化用语 + 「第一年未录取,第二年半价」半价承诺违反教育广告禁止承诺考试结果 + 「申论 80+」数据无出处 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art9_edu_abs, ad_signage_art11_data_citation, ad_signage_art24_edu_guar |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十四条 + 第二十八条 |

## 16_蒙恩教育教师资格证_通过率85%_数据引用.png

| 字段 | 值 |
|---|---|
| 文件名 | `16_蒙恩教育教师资格证_通过率85%_数据引用.png` |
| 桶分类 | data_citation × education |
| 违规描述 | 「本次参考人数 164 人,两科通过 90 人,单科通过 50 人 通过率 85%」属数据引用不规范(无出处 / 统计口径 / 计算方法 / 有效期限) |
| 现行覆盖规则 | ad_signage_art11_data_citation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十一条第二款 + 第五十九条 |

## 17_智行教育25省考申论保分_快速提分_教育承诺.png

| 字段 | 值 |
|---|---|
| 文件名 | `17_智行教育25省考申论保分_快速提分_教育承诺.png` |
| 桶分类 | education |
| 违规描述 | 「25 省考 申论保分班」「快速提分」「直击高分」「快速精准提升」「突破 70+ 分数学员」属无依据培训效果 + 提分幅度承诺 |
| 现行覆盖规则 | ad_signage_art11_data_citation, ad_signage_art24_edu_guar |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art24_edu_guar 加「快速提分/直击高分/精准提升/突破 70+/申论保分」;ad_signage_art11_data_citation 加「70+ 分数/高分/精准提升」) |
| 关联法条 | 《广告法》第二十四条 + 第二十八条 |

## 18_白酒电商页_闻香入口天然桦树清香.png

| 字段 | 值 |
|---|---|
| 文件名 | `18_白酒电商页_闻香入口天然桦树清香.png` |
| 桶分类 | `food_function_claim`(轻微) |
| 违规描述 | 「闻香 天然桦树清香 与酒香交织」「入口 层次丰富 清爽顺滑」白酒电商页;无明显诱导未成年人饮酒但属酒类电商广告场景 |
| 现行覆盖规则 | ad_signage_art22_tob_alc |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art22_tob_alc 加「白酒/啤酒/红酒/黄酒/洋酒/酒类/酒香/酒精度/纯粮」) + 补新规则(可选:`ad_signage_signage_alcohol_drink_scenario`,category=restricted,锚酒类电商页通用场景) |
| 关联法条 | 《广告法》第二十二条 + 第二十三条 + 《酒类广告管理办法》 |

## 19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg

| 字段 | 值 |
|---|---|
| 文件名 | `19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg` |
| 桶分类 | food_function_claim × food_disease_target |
| 违规描述 | 「蜂王浆冻干粉 + 蜂胶 + 蜂蜜」多产品整图宣称「改善营养 补充脑力」「提高人体免疫力」「增强食欲」「消炎止痛」「安神益智 提高记忆力」 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 20_黑尊牛安格斯牛肉礼盒_送领导客户清真.png

| 字段 | 值 |
|---|---|
| 文件名 | `20_黑尊牛安格斯牛肉礼盒_送领导客户清真.png` |
| 桶分类 | realestate(实为食品) × fake_data |
| 违规描述 | 「春节礼品 节日回送领导客户 清真」属公务送礼诱导;「中国安格斯肉牛之乡 - 孙吴」属地理标志无依据宣称 |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 补新规则(建议 ruleId:`ad_signage_signage_gift_to_leader`,category=minor 或 signage,severity=Warning,keywords 加「送领导/送上级/送老板/送客户/节日礼品/商务礼/送礼首选/公务送礼」) |
| 关联法条 | 《广告法》第九条第(八)项 + 引人误解的虚假宣传 |

## 21_72小时紧急避孕药_左炔诺孕酮片OTC.png

| 字段 | 值 |
|---|---|
| 文件名 | `21_72小时紧急避孕药_左炔诺孕酮片OTC.png` |
| 桶分类 | `medical`(药品) × `internet_ad` |
| 违规描述 | 「72 小时女性紧急避孕 左炔诺孕酮片 OTC」在天猫电商页发布 OTC 药品广告 + 「关爱女性 紧急避孕」类诱导性表述;互联网药品信息服务应备案 |
| 现行覆盖规则 | ad_signage_signage_otc_label, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《药品管理法》第六十一条 + 《互联网药品信息服务管理办法》 + 《广告法》第十五条 |

## 22_坤丰绿旋风2号辣椒种子_高产南北方栽培_种子.png

| 字段 | 值 |
|---|---|
| 文件名 | `22_坤丰绿旋风2号辣椒种子_高产南北方栽培_种子.png` |
| 桶分类 | agricultural |
| 违规描述 | 「高产 香辣螺丝椒」「适合南北方栽培」属种子产量 + 栽培区域无依据承诺;「绿旋风 2 号」包装无明显农药登记证号 / 品种审定编号 |
| 现行覆盖规则 | ad_signage_art27_seed_yield_guarantee |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art27_seed_yield_guarantee 加「高产/超高产/适合南北方栽培/南北方种植/全国适宜」) |
| 关联法条 | 《广告法》第二十七条 + 《种子法》第三十一条 |

## 23_黑旋风冬瓜种子_高产新改良_种子广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `23_黑旋风冬瓜种子_高产新改良_种子广告.png` |
| 桶分类 | `agricultural` |
| 违规描述 | 「瓜型好 心小肉厚 高产」「新改良」属种子产量 / 品种改良无依据承诺;包装无明显品种审定编号 + 农药登记证号 |
| 现行覆盖规则 | ad_signage_art27_seed_yield_guarantee |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art27_seed_yield_guarantee 加「高产/新改良/心小肉厚/瓜型好/科学上无法验证的断言」单字粒径类) |
| 关联法条 | 《广告法》第二十七条 + 《种子法》第三十一条 |

## 24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png` |
| 桶分类 | `medical` × `internet_ad` |
| 违规描述 | 「UBE 单侧双通道脊柱内镜技术」宣传具体医疗技术 + 治疗效果保证(「微创高效」「术后恢复快,疤痕小」「安全性高」「降低感染概率」)+ 适应症宣传(腰椎间盘突出 / 椎管狭窄 / 颈椎 / 胸椎);互联网医疗广告未取得审查证明 |
| 现行覆盖规则 | ad_signage_med_art6_indications, ad_signage_med_art7_technicality, ad_signage_signage_disease_prevention, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十六条 + 第五十八条 + 《医疗广告管理办法》第三条 / 第七条 + 《互联网广告管理办法》第七条 + 第十条 |

## 25_蜂王浆冻干粉_提高免疫力抵抗力_保健食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `25_蜂王浆冻干粉_提高免疫力抵抗力_保健食品.png` |
| 桶分类 | `food_function_claim` × `internet_ad` |
| 违规描述 | 「蜂王浆冻干粉养生提高免疫力抵抗」属普通食品(蜂王浆冻干粉)在电商页(小程序)宣称保健功能;无蓝帽子标识 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |

## 26_四季无筋豆种子_高产南北方种植_种子广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `26_四季无筋豆种子_高产南北方种植_种子广告.png` |
| 桶分类 | `agricultural` |
| 违规描述 | 「45 天早熟地豆」「高产」「抗病」「肉厚无筋无柴」「条细条绿」「适合南北方种植」属种子产量 / 品质 / 抗性 / 栽培区域无依据承诺 |
| 现行覆盖规则 | ad_signage_art27_seed_yield_guarantee |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art27_seed_yield_guarantee 加「高产/抗病/早熟/适合南北方种植/南北方种植/品质特征」) |
| 关联法条 | 《广告法》第二十七条 + 《种子法》第三十一条 |

## 27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png

| 字段 | 值 |
|---|---|
| 文件名 | `27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png` |
| 桶分类 | `medical` × `internet_ad` |
| 违规描述 | 「浮针疗法」「中医外治疗法」「皮下疏松结缔组织层进行扫散操作」属医疗技术宣传 + 「具有安全、无痛、见效快的特点」属治疗效果保证 |
| 现行覆盖规则 | ad_signage_med_art6_indications, ad_signage_med_art7_technicality, ad_signage_signage_disease_prevention, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十六条 + 《医疗广告管理办法》第三条 / 第七条 + 《互联网广告管理办法》第七条 + 第十条 |

## 28_千禧柿子水果_减脂美白抗氧化_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `28_千禧柿子水果_减脂美白抗氧化_食品功能.png` |
| 桶分类 | `food_function_claim` × `weight_loss` × `internet_ad` |
| 违规描述 | 「千禧小柿子 减脂 美白 补水 抗氧化 高 VC」属普通水果在电商页宣称保健功能 + 减肥功能;电商页无蓝帽子标识 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, internet_art7_pre_review |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_signage_food_function_claim 加「减脂/美白/补水/高 VC/减肥水果」);并补新规则(建议 ruleId:`ad_signage_signage_weight_loss_food_claim`,category=signage,severity=Violation,keywords 加「减脂/减肥/瘦身/瘦 X 斤/X 天瘦」专门锚普通食品减肥宣称) |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |

## 29_笨鸡蛋营养价值_增强人体免疫_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `29_笨鸡蛋营养价值_增强人体免疫_食品功能.png` |
| 桶分类 | `food_function_claim` |
| 违规描述 | 「蛋白质 补充能量」「钙 Ca 强身健骨」「磷 P 调节身体」「牛磺酸 增强人体免疫」「软磷脂 调节脂质」属普通食品(鸡蛋)成分描述外推保健功能;无蓝帽子标识 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 |

## 30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png` |
| 桶分类 | `absolute` × `food_function_claim` × `food_disease_target` |
| 违规描述 | 「油料之王」属最高级表述 + 「强心肺、降三高;抗肿瘤、通肠道;强体魄、抗衰老」「增强人体的免疫力」属食用油(普通食品)疾病治疗 + 保健功能双重违规 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十七条 + 第十八条 + 第五十七条 |

## 31_黄豆天然植物蛋白_降低胆固醇_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `31_黄豆天然植物蛋白_降低胆固醇_食品功能.png` |
| 桶分类 | `food_function_claim` |
| 违规描述 | 「黄豆有降低胆固醇的功效」属普通食品(黄豆)宣称保健功能;「绿色乳牛」「天然植物蛋白 满满的植物营养」属无依据原料 / 营养强化表述 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 |

## 32_公考培训宋尚案老师_某省直机关在职领导_身份.png

| 字段 | 值 |
|---|---|
| 文件名 | `32_公考培训宋尚案老师_某省直机关在职领导_身份.png` |
| 桶分类 | `education` × `absolute`(利用身份背书) |
| 违规描述 | 「某省直机关在职中层领导」属国家机关工作人员身份背书 + 「多年黑龙江省考、事业单位考试面试官」属考试机构人员参与培训代言;国家机关工作人员不得做商业广告代言人 |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 补新规则(建议 ruleId:`ad_signage_edu_art24_public_servant_endorsement`,category=education,severity=Violation,keywords 加「某省直机关在职/在职中层领导/在职机关人员/在职公务员代言/公务员兼职培训/机关事业单位在职人员推荐」) |
| 关联法条 | 《广告法》第九条第(三)项 + 第二十四条 + 《公务员法》第五十九条第(十六)项(违规兼职) |

## 33_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png

| 字段 | 值 |
|---|---|
| 文件名 | `33_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png` |
| 桶分类 | `food_disease_target` × `food_function_claim` |
| 违规描述 | 「降低胆固醇」「预防心血管疾病」「溶解血栓」「降低心脑血管疾病发生风险」属普通食品(纳豆红曲地龙蛋白片)疾病治疗 + 「增强免疫力」「调节血糖」「保护肝脏」「延缓衰老」属保健功能双重违规;涉 10 大功能宣称 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention, ad_signage_signage_food_disease_target |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 34_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png

| 字段 | 值 |
|---|---|
| 文件名 | `34_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png` |
| 桶分类 | `food_disease_target` × `medical`(含医疗用语) |
| 违规描述 | 「保肝护肝」「降血脂」「防止糖尿病」「保护心肌」「抗血小板聚集」「抗肿瘤」「清热解毒」「疏肝利胆」「治疗各种肝病」属普通食品(压片糖果)疾病治疗 + 医疗用语双重违规 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention, ad_signage_med_art6_indications |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 35_优普派阿苯达唑片_无效包退800万粉丝_兽药.png

| 字段 | 值 |
|---|---|
| 文件名 | `35_优普派阿苯达唑片_无效包退800万粉丝_兽药.png` |
| 桶分类 | `pestvet` × `fake_data` × `internet_ad` |
| 违规描述 | 「2+4+7 模式 精准驱杀」属治疗效果承诺 + 「无效包退 售后无忧」属兽药治疗效果保证;「全网 800 万粉丝的选择」属数据无依据;兽药广告不得含保证治愈效果 |
| 现行覆盖规则 | ad_signage_veterinary_art8_commitment, ad_signage_art11_data_citation |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_veterinary_art8_commitment 加「无效包退/无效全退/无效免单/售后无忧/驱杀/精准驱杀」;ad_signage_art11_data_citation 加「800 万粉丝/万粉丝选择/全网粉丝」) |
| 关联法条 | 《广告法》第二十一条第(一)项、第(三)项 + 第二十八条 + 第五十五条 + 《兽药管理条例》第三十一条 |

## 36_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png

| 字段 | 值 |
|---|---|
| 文件名 | `36_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` |
| 违规描述 | 「降胆固醇」「辅助降压」「提神醒脑」「预防心血管疾病」「消炎」「恢复体力」属普通食品(松针油凝胶糖果)保健功能 + 疾病治疗双重违规;「呵护家人健康」属诱导 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 37_白桦树汁植物饮料_提高免疫力儿童和老人.png

| 字段 | 值 |
|---|---|
| 文件名 | `37_白桦树汁植物饮料_提高免疫力儿童和老人.png` |
| 桶分类 | `food_function_claim` × `internet_ad` |
| 违规描述 | 「适合人群」「希望提高免疫力的儿童和老人」属普通植物饮料宣称保健功能 + 特定人群(儿童 / 老人)诱导;「大自然的搬运工 没有科技与狠活」类营销话术 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |

## 38_体重管理减肥产品_两月减重31斤_减肥承诺.png

| 字段 | 值 |
|---|---|
| 文件名 | `38_体重管理减肥产品_两月减重31斤_减肥承诺.png` |
| 桶分类 | `weight_loss` × `food_function_claim` × `internet_ad` |
| 违规描述 | 「2 个月减重 31 斤」「50 天减重 27.1 斤 腰围缩了 10CM」「14 天减重 8 斤」「21 天减重 10.2 斤」属减肥产品宣传具体减重数据 + 期限 + 部位围度变化;「调养脾胃·体重管理」属中医减肥暗示 |
| 现行覆盖规则 | internet_art7_pre_review, ad_signage_signage_food_function_claim |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(internet_art7_pre_review 加「体重管理/X 月减重/X 天减重/X 斤/腰围缩」);并补新规则(建议 ruleId:`ad_signage_signage_weight_loss_data_commitment`,category=signage,severity=Violation,keywords 加「减重 X 斤/X 月减重/X 天减重/X 天瘦 X 斤/腰围缩 X CM/腰围瘦 X」锚减肥数据承诺) |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |

## 39_蟹都汇总部商城_全国第一领导品牌_绝对化.png

| 字段 | 值 |
|---|---|
| 文件名 | `39_蟹都汇总部商城_全国第一领导品牌_绝对化.png` |
| 桶分类 | `absolute` × `fake_data` × `data_citation` × `internet_ad` |
| 违规描述 | 「大闸蟹十年累计销量全国第一」「大闸蟹连锁门店数量全国第一」属绝对化用语双连发 + 数据无依据;「高端大闸蟹领导品牌」属最高级表述;小程序商城电商场景 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art28b_fake_data, ad_signage_art11_data_citation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十八条 + 第五十七条 / 第五十五条 |

## 40_北大荒椴树雪蜜_提高人体免疫力_保健食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `40_北大荒椴树雪蜜_提高人体免疫力_保健食品.png` |
| 桶分类 | `food_function_claim` × `internet_ad` |
| 违规描述 | 「8 大优势」「改善营养 补充脑力」「提高人体免疫力」「增强食欲」属普通食品(蜂蜜)电商页宣称保健功能;「京东大促」电商场景需平台同步下架 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, internet_art7_pre_review |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |

## 41_肺肽片保健食品_清肺排毒提升肺功能_食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `41_肺肽片保健食品_清肺排毒提升肺功能_食品.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` |
| 违规描述 | 「清肺排毒」「提升肺功能」「2 瓶拥有 1 健康好肺」属运动营养食品(耐力类)宣称肺部保健 + 治疗效果保证;产品分类与「肺功能」宣称不匹配 |
| 现行覆盖规则 | — |
| 覆盖状态 | 未覆盖 |
| 建议动作 | 补新规则(建议 ruleId:`ad_signage_signage_food_lung_health_claim`,category=signage,severity=Violation,keywords 加「清肺/清肺排毒/提升肺功能/养肺/润肺/清肺解毒」锚食品类肺部宣称) |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png

| 字段 | 值 |
|---|---|
| 文件名 | `42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png` |
| 桶分类 | `absolute` × `food_function_claim` |
| 违规描述 | 「行业领导品牌」属绝对化用语 + 「滋养 14 亿国人」属无依据保健功能 + 引人误解;「送礼送贵人 送客户 送朋友 送领导 送长辈」含「送领导」公务送礼诱导 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_signage_food_function_claim |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art9_abs_top 加「行业领导品牌」);并新增 keywords 到(ad_signage_signage_food_function_claim 加「滋养 X 亿国人/滋养 X 亿/14 亿国人」);或补新规则(建议 ruleId:`ad_signage_signage_food_beneficiary_count_claim`,category=signage,severity=Violation,keywords 加「滋养 X 亿国人/X 亿国人选择/覆盖 X 亿/中国 X 亿人」锚受益人群夸大) |
| 关联法条 | 《广告法》第九条第(三)项 + 第十七条 + 第十八条 + 第五十七条 |

## 43_沙棘维生素C_补充VC提高免疫力_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `43_沙棘维生素C_补充VC提高免疫力_食品功能.png` |
| 桶分类 | `food_function_claim` |
| 违规描述 | 「沙棘被誉为维生素 C 宝库」「补充 VC 提高免疫力」属普通食品(沙棘饮料)宣称保健功能;「绿色营养 健康之选」属无依据整体保健宣称 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 |

## 44_保健品广告_调节血压降低血脂_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `44_保健品广告_调节血压降低血脂_食品功能.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` |
| 违规描述 | 「过度劳累 感觉身体被掏空?」「久坐办公族 久坐缺乏运动」「年纪不大 免疫力低下」「中老年人 调节血压降低血脂」属场景化营销保健食品 + 「调节血压」「降低血脂」疾病治疗 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` |
| 违规描述 | 「补肝肾、强筋骨、降三高、助睡眠」属普通食品(杜仲雄花茶)保健食品功能 + 疾病治疗双重违规;「药用价值」属医疗用语 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 46_肺部结节草本产品_清热解毒消肿散结_医疗.png

| 字段 | 值 |
|---|---|
| 文件名 | `46_肺部结节草本产品_清热解毒消肿散结_医疗.png` |
| 桶分类 | `medical` × `food_disease_target` |
| 违规描述 | 「清热解毒」「消肿散结」「抑制结节增生」「软化硬结」「利水消肿」「防止结节再生」属草本食品使用医疗术语 + 疾病治疗;针对肺结节人群的医疗暗示 |
| 现行覆盖规则 | ad_signage_med_art6_indications, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 47_益生菌产品_预防治疗腹泻提高免疫_医疗.png

| 字段 | 值 |
|---|---|
| 文件名 | `47_益生菌产品_预防治疗腹泻提高免疫_医疗.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` |
| 违规描述 | 「预防和治疗腹泻」「提高机体免疫力」「可改善过敏体质」属普通食品(益生菌)保健功能 + 疾病治疗双重违规 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 |

## 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png

| 字段 | 值 |
|---|---|
| 文件名 | `48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png` |
| 桶分类 | `food_disease_target` × `food_function_claim` × `internet_ad` |
| 违规描述 | 产品标题「血压 血糖 血脂降下去」属普通食品(玛莉魔粉)保健功能 + 疾病治疗双重违规;电商页场景需互联网广告规范 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention, internet_art6_identifiable |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |

## 49_仁和氨糖软骨素钙片手机详情页_保健暗示安全_违规.png

| 字段 | 值 |
|---|---|
| 文件名 | `49_仁和氨糖软骨素钙片手机详情页_保健暗示安全_违规.png` |
| 桶分类 | `food_function_claim`(保健食品演绎化 / 暗示安全性) |
| 违规描述 | 「优质配方 安全放心」属保健食品暗示安全性保证 + 「补软骨 护关节」属演绎化保健功能(蓝帽子批准功能仅为「增加骨密度」);强制提示语 + 蓝帽子 + 国家查询 URL 部分合规 |
| 现行覆盖规则 | ad_signage_signage_food_safety_implication, ad_signage_signage_food_function_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十八条第(一)项 + 《药品、医疗器械、保健食品、特殊医学用途配方食品广告审查管理暂行办法》第十一条第(五)项 + 第五十八条 |

## 50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png` |
| 桶分类 | `food_function_claim` × `food_disease_target` × `internet_ad` |
| 违规描述 | 「呵护泌尿系统健康」「抑制有害细菌」「辅助调节血糖水平」属普通食品(蓝莓果粉)保健功能 + 疾病治疗双重违规;电商页场景 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention, internet_art6_identifiable |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |

## 51_哈尔滨万通电工PLC培训_见效快回报高_教育.png

| 字段 | 值 |
|---|---|
| 文件名 | `51_哈尔滨万通电工PLC培训_见效快回报高_教育.png` |
| 桶分类 | `education` |
| 违规描述 | 「投入少、见效快、回报高」属培训效果 + 投资回报承诺;「毕业就能凭技术拿高薪」「高薪、有前途」属就业 / 收入承诺 |
| 现行覆盖规则 | ad_signage_art24_edu_guar |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_art24_edu_guar 加「见效快/回报高/投入少/拿高薪/高薪/有前途/稳定/硬核」) |
| 关联法条 | 《广告法》第二十四条 + 第二十八条 + 第五十八条 |

## 52_北大荒蜂胶软胶囊_保健食品天然暗示_违规.png

| 字段 | 值 |
|---|---|
| 文件名 | `52_北大荒蜂胶软胶囊_保健食品天然暗示_违规.png` |
| 桶分类 | `food_function_claim`(保健食品暗示安全性) |
| 违规描述 | 「✅纯天然」勾选框 + 「天然珍藏 生态臻品」主标题属保健食品暗示天然 → 安全保证;蓝帽子 + 强制提示语部分合规 |
| 现行覆盖规则 | ad_signage_signage_food_safety_implication |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十八条第(一)项 + 《药品、医疗器械、保健食品、特殊医学用途配方食品广告审查管理暂行办法》第十一条第(五)项 + 第五十八条 |

## 53_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png

| 字段 | 值 |
|---|---|
| 文件名 | `53_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png` |
| 桶分类 | `pestvet` × `fake_data` × `internet_ad` |
| 违规描述 | 「药效持久」「干扰虫体神经细胞正常功能致其消亡」属兽药治疗效果保证;「800 万粉丝的选择」数据无依据;兽药广告不得含保证治愈效果 |
| 现行覆盖规则 | ad_signage_veterinary_art8_commitment, ad_signage_veterinary_art4_assertion, ad_signage_art11_data_citation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第二十一条第(一)项、第(三)项 + 第二十八条 + 第五十五条 + 《兽药管理条例》第三十一条 |

## 54_京东京造菊粉代餐_减肥便秘通便_食品功能.png

| 字段 | 值 |
|---|---|
| 文件名 | `54_京东京造菊粉代餐_减肥便秘通便_食品功能.png` |
| 桶分类 | `weight_loss` × `food_function_claim` × `internet_ad` |
| 违规描述 | 「减肥」「便秘通便」属普通食品(菊粉代餐)减肥功能 + 保健功能宣称;电商页需互联网广告规范 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, internet_art6_identifiable |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_signage_food_function_claim 加「减肥/通便/便秘通便/代餐减肥」);并补新规则(建议 ruleId:`ad_signage_signage_weight_loss_food_claim`,category=signage,severity=Violation,keywords 加「代餐减肥/菊粉代餐/减肥代餐」) |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |

## 55_京东京造番茄红素沙棘果油_前列腺养护_食品.png

| 字段 | 值 |
|---|---|
| 文件名 | `55_京东京造番茄红素沙棘果油_前列腺养护_食品.png` |
| 桶分类 | `medical` × `food_disease_target` × `internet_ad` |
| 违规描述 | 「前列腺养护」「护前列腺炎」「尿频尿急」属普通食品(番茄红素沙棘果油)疾病治疗 + 涉及男性生殖健康;「增强免疫力」保健功能 |
| 现行覆盖规则 | ad_signage_med_art6_indications, ad_signage_signage_food_function_claim, ad_signage_signage_disease_prevention |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_signage_food_disease_target 加「前列腺养护/护前列腺炎/尿频尿急/男性生活伴侣」);并新增 keywords 到(ad_signage_med_art6_indications 加「前列腺养护/尿频尿急」) |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |

## 56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png

| 字段 | 值 |
|---|---|
| 文件名 | `56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png` |
| 桶分类 | `cosmetics` × `medical` × `absolute` |
| 违规描述 | 「激素依赖性皮炎」「干性湿疹+角质受损」「医美后脆弱肌」属化妆品宣称疾病治疗 + 医疗用语;「专利抗炎 强韧屏障」「激活细胞根源 重建屏障」涉及医疗暗示 |
| 现行覆盖规则 | cosmetic_art23_medical_claim, cosmetic_art23_medical_explicit, cosmetic_art9_abs_extended |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十七条 + 《化妆品监督管理条例》第二十二条 / 第二十三条 |

## 57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png

| 字段 | 值 |
|---|---|
| 文件名 | `57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png` |
| 桶分类 | `absolute` × `cosmetics` × `medical` |
| 违规描述 | 「国内首创」+「首个」+「速度第一」属绝对化用语三重连发;「敏感肌/激素脸 → 28 天科学修复」涉及医疗宣称;「100% 纯原料玻色因」无依据绝对化 |
| 现行覆盖规则 | ad_signage_art9_abs_top, cosmetic_art9_abs_extended, cosmetic_art23_medical_claim |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十七条 + 《化妆品监督管理条例》第二十二条 / 第二十三条 + 第五十七条 |

## 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg` |
| 桶分类 | `absolute` × `fake_data` × `medical`(美容器械) |
| 违规描述 | 「连续 6 年销量第 1」属绝对化用语 + 数据无出处;「四周见效」「冰点无痛」属治疗效果保证(医疗美容器械);电梯液晶屏广告 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art11_data_citation, ad_signage_signage_disease_prevention |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十八条 + 第五十七条 / 第五十五条 |

## 59_凯利集团金街商铺_1万抵3万起_地产广告.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `59_凯利集团金街商铺_1万抵3万起_地产广告.jpg` |
| 桶分类 | `realestate` × `fake_data` |
| 违规描述 | 「1 万抵 3 万起」「地铁旁」属房地产广告诱导性承诺;「首席企业官」「唯一汽车后服务专业市场」属绝对化用语;「智慧健康体检区域」属对未来配套设施的无依据承诺 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_re_art26_planned_facility, ad_signage_re_art26_price_violation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第二十六条 + 第二十八条 + 《房地产广告发布规定》第四条 / 第六条 |

## 60_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `60_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` |
| 桶分类 | `absolute` × `data_citation` × `education` × `fake_data` |
| 违规描述 | 「哈尔滨地区排名第一」属绝对化用语;「实际通过率高达 75%」数据无出处 + 无统计口径;「不二之选」属无依据最高级表述 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art11_data_citation, ad_signage_art28b_fake_data |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十四条 + 第二十八条 + 第五十七条 / 第五十九条 |

## 61_哈尔滨信誉小区_智慧健康体检60_139㎡_地产.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `61_哈尔滨信誉小区_智慧健康体检60_139㎡_地产.jpg` |
| 桶分类 | `realestate` |
| 违规描述 | 「60-139㎡ 智慧健康体检区」属对未来配套设施的无依据承诺;无项目地址 / 预售证号 / 开发商明示 |
| 现行覆盖规则 | ad_signage_re_art26_planned_facility, ad_signage_re_art7_license_no |
| 覆盖状态 | 弱覆盖(关键词薄) |
| 建议动作 | 强化现规则 keywords(ad_signage_re_art26_planned_facility 加「智慧健康/体检区/健康体检区/锦绣全景/核心商圈」) |
| 关联法条 | 《广告法》第二十六条 + 《房地产广告发布规定》第四条 / 第六条 |

## 62_东郊到家按摩APP_9万人1000万次_数据引用.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `62_东郊到家按摩APP_9万人1000万次_数据引用.jpg` |
| 桶分类 | `data_citation` |
| 违规描述 | 「全国技师超 9 万人」「累计服务超 1000 万次」数据未标明出处 / 适用范围 / 有效期限;「致力于公益事业」属公益营销无具体项目;电梯液晶屏广告 |
| 现行覆盖规则 | ad_signage_art11_data_citation |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十一条第二款 + 第五十九条 + 《互联网广告管理办法》(APP 类广告适用) |

## 63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg` |
| 桶分类 | `medical` × `fake_data` |
| 违规描述 | 公开宣称「吃喝不忌口」「血糖不再高」「摆脱降糖药」「并发症状消」属典型医疗虚假宣传 + 重大虚假医疗宣传情形;高发重大违法 |
| 现行覆盖规则 | ad_signage_signage_disease_prevention, ad_signage_art28b_fake_data |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十六条 + 第二十八条 + 第五十五条 + 《医疗广告管理办法》第三条 |

## 64_杜蕾斯公交车身_首个公益装_化妆品绝对化.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `64_杜蕾斯公交车身_首个公益装_化妆品绝对化.jpg` |
| 桶分类 | `absolute` × `cosmetics` × `signage` |
| 违规描述 | 商业品牌「杜蕾斯」在公交车身广告上宣称「首个公益装」使用「首个」绝对化用语 |
| 现行覆盖规则 | ad_signage_art9_abs_top, cosmetic_art9_abs_extended |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第五十七条 + 《化妆品监督管理条例》第二十二条 / 第二十三条 |

## 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg` |
| 桶分类 | `absolute` × `fake_data` × `data_citation` |
| 违规描述 | 「大闸蟹十年累计销量全国第一」+ 「高端大闸蟹领导品牌」属绝对化用语双连发 + 数据无依据 |
| 现行覆盖规则 | ad_signage_art9_abs_top, ad_signage_art11_data_citation, ad_signage_art28b_fake_data |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第九条第(三)项 + 第十一条第二款 + 第二十八条 + 第五十七条 / 第五十五条 |

## 66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg

| 字段 | 值 |
|---|---|
| 文件名 | `66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg` |
| 桶分类 | `food_function_claim` × `food_disease_target` × `internet_ad` |
| 违规描述 | 「抗氧化」「保护心血管」「增强免疫力」属普通食品(紫玉米花青素)保健食品功能宣称;「控糖稳血糖」「糖尿病患者的安心选择」属疾病治疗 + 糖尿病人群诱导 |
| 现行覆盖规则 | ad_signage_signage_food_function_claim, ad_signage_signage_food_disease_target, ad_signage_signage_disease_prevention, internet_art6_identifiable |
| 覆盖状态 | 已覆盖 |
| 建议动作 | 保持 |
| 关联法条 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |

## §新规则候选清单

(根据 66 节审计结果汇总,以下为建议新增规则,Phase 2 阶段实施。审计中若仅在 1 节出现 补新规则 候选(可选)也列入,但实施优先级排在 #18/#20/#32 之后。)

| 候选 ruleId | category | severity | 关联图 | 法条依据 |
|---|---|---|---|---|
| ad_signage_signage_military_political_marketing | signage | Violation | #06 | 《广告法》第九条第(七)项 + 第二十八条 + 《公益广告促进和管理暂行办法》 + 第五十七条 / 第五十五条 |
| ad_signage_signage_alcohol_drink_scenario | restricted | (— 可选,审计未指定 severity) | #18 | 《广告法》第二十二条 + 第二十三条 + 《酒类广告管理办法》 |
| ad_signage_signage_gift_to_leader | signage | Warning | #20 | 《广告法》第九条第(八)项 + 引人误解的虚假宣传 |
| ad_signage_signage_weight_loss_food_claim | signage | Violation | #28, #54 | 《广告法》第十七条 + 第十八条 + 第五十八条 + 《互联网广告管理办法》 |
| ad_signage_edu_art24_public_servant_endorsement | education | Violation | #32 | 《广告法》第九条第(三)项 + 第二十四条 + 《公务员法》第五十九条第(十六)项(违规兼职) |
| ad_signage_signage_weight_loss_data_commitment | signage | Violation | #38 | 《广告法》第十七条 + 第十八条 + 《互联网广告管理办法》 |
| ad_signage_signage_food_lung_health_claim | signage | Violation | #41 | 《广告法》第十七条 + 第十八条 + 第五十八条 |
| ad_signage_signage_food_beneficiary_count_claim | signage | Violation | #42 | 《广告法》第九条第(三)项 + 第十七条 + 第十八条 + 第五十七条 |

> 备注:
> - 上述 8 条候选中,#06 / #20 / #32 / #41 / #42 在审计中明确标记为「未覆盖」(覆盖状态 = 未覆盖),需新规则落地;#18 / #28 / #38 / #54 现有规则虽存在但需补强(同 #18 同时含「补新规则(可选)」候选,优先级次之)。
> - 实施顺序(与 rules-coverage-audit 后续 Tasks 对齐):Task 7 处理 #18(酒类场景);Task 8 处理 #20(送礼诱导);Task 9 处理 #32(公务员代言);Task 10 处理其余 5 条(#06 / #28+#54 / #38 / #41 / #42)。
> - 同一 ruleId(`ad_signage_signage_weight_loss_food_claim`)在 #28 / #54 两节都标了「补新规则」,合并为 1 条。
> - `ad_signage_signage_alcohol_drink_scenario` 审计标为「可选」(`+ 补新规则(可选:...)`),实施顺序排最末。

## §强化规则清单

(根据 66 节审计结果汇总,以下为建议 keywords 扩展,Phase 2 阶段实施。n = 当前关键词数 / severity 取自 `app/src/main/assets/rules/ad_signage_rules.json` v8 / 121 条。)

| ruleId | 现状(n=关键词数, severity) | 强化方向 | 关联图 |
|---|---|---|---|
| ad_signage_art9_abs_top | n=11, Warning | 加「三强/五百强」(#01);加「行业领导品牌」(#42) | #01, #42 |
| ad_signage_art11_data_citation | n=38, Warning | 加「500 强/世界 500 强」(#01);加「70+ 分数/高分/精准提升」(#17);加「800 万粉丝/万粉丝选择/全网粉丝」(#35) | #01, #17, #35 |
| ad_signage_art22_tob_alc | n=2, Info | 加「白酒/啤酒/红酒/黄酒/洋酒/酒类/酒香/酒精度/纯粮」 | #18 |
| ad_signage_art24_edu_guar | n=4, Violation | 加「未进面 0 收费/上岸为止/快速提分/高效促学」(#10);加「高效提分/冲刺模考/封闭刷题班」(#11);加「快速提分/直击高分/精准提升/突破 70+/申论保分」(#17);加「见效快/回报高/投入少/拿高薪/高薪/有前途/稳定/硬核」(#51) | #10, #11, #17, #51 |
| ad_signage_edu_art24_test_authority | n=4, Warning | 加「警察老师主讲/公安专项/警考」 | #11 |
| ad_signage_art27_seed_yield_guarantee | n=14, Violation | 加「高产/抗病/早熟/干鲜两用/超高产/超高产王」(#12);加「饱满/多且饱满/籽粒饱满/油亮饱满」(#13);加「高产/超高产/适合南北方栽培/南北方种植/全国适宜」(#22);加「高产/新改良/心小肉厚/瓜型好」(#23);加「高产/抗病/早熟/适合南北方种植/品质特征」(#26) | #12, #13, #22, #23, #26 |
| ad_signage_art26_re_prm | n=3, Warning | 加「财富启航/财富实力/创富」 | #03 |
| ad_signage_re_art26_planned_facility | n=5, Warning | 加「主题商街/国潮茶文化」(#03);加「智慧健康/体检区/健康体检区/锦绣全景/核心商圈」(#61) | #03, #61 |
| ad_signage_signage_food_function_claim | n=39, Violation | 加「减脂/美白/补水/高 VC/减肥水果」(#28);加「滋养 X 亿国人/滋养 X 亿/14 亿国人」(#42);加「减肥/通便/便秘通便/代餐减肥」(#54) | #28, #42, #54 |
| ad_signage_signage_food_disease_target | n=20, Violation | 加「前列腺养护/护前列腺炎/尿频尿急/男性生活伴侣」 | #55 |
| ad_signage_veterinary_art8_commitment | n=3, Warning | 加「无效包退/无效全退/无效免单/售后无忧/驱杀/精准驱杀」 | #35 |
| ad_signage_med_art6_indications | n=5, Warning | 加「前列腺养护/尿频尿急」 | #55 |
| internet_art7_pre_review | n=6, Violation | 加「体重管理/X 月减重/X 天减重/X 斤/腰围缩」 | #38 |

> 备注:
> - 上述 13 条强化候选中,#03 / #55 / #61 / #35 涉及「新增 keywords 到」措辞(审计中与「强化现规则 keywords」混用),但语义等价,合并入本表。
> - `ad_signage_signage_food_function_claim` 现 n=39 是 v8 阶段最大的规则之一,再加 #28 / #42 / #54 关键词可能触发关键词重叠(已有「排毒 / 抗氧化」等) — 落地时需去重。
> - `ad_signage_art11_data_citation` 现 n=38,继续加「500 强」家族变体(世界 500 强 / 500 强企业 等)需警惕与「全国第一 / 销量第一」已存词的边界。
> - `ad_signage_re_art26_planned_facility` 现 n=5,#03 + #61 关键词合计 ~10 个,落地时优先合并到现有「规划学校 / 规划医院 / 国潮 / 主题商街」附近桶。
