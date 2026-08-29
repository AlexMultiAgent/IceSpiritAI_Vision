# 违规案例 — 规则 跨覆盖矩阵

> 生成于:2026-08-27
> 规则数:129 | 示例图数:66
> 数据源:`_audit_gaps.md`(66 节明细) + `_违规档案总册.md`(关联规则 ID 列 + 桶/严重度列)
> 规则 v9:8 新规则(`ad_signage_signage_*` / `ad_signage_edu_art24_public_servant_endorsement`)+ 13 强化规则(沿用 Task 13 结论)

## §1 规则 → 示例图

| ruleId | category | severity | 示例图数 | 文件名列表 |
|---|---|---|---:|---|
| `ad_signage_art16_med_abs` | `medical` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art16_med_health` | `medical` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art20_breastmilk` | `minor` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art22_tobacco_internet` | `restricted` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art23_alcohol_drive` | `restricted` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art23_alcohol_relief` | `restricted` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art24_edu_guar` | `education` | Violation | 4 | `#03` 03_名师教育申论保分班_第一年未录取第二年半价_教育.png, `#10` 10_玉远龙公考_未进面0收费_教育承诺收益.png, `#17` 17_智行教育25省考申论保分_快速提分_教育承诺.png, `#51` 51_哈尔滨万通电工PLC培训_见效快回报高_教育.png |
| `ad_signage_art25_fin_prm` | `finance` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art27_seed_yield_guarantee` | `agricultural` | Violation | 5 | `#12` 12_东北景椒辣妹子种子_早熟高产抗病_种子广告.png, `#13` 13_豌豆种子_多且饱满高产_种子广告.png, `#22` 22_坤丰绿旋风2号辣椒种子_高产南北方栽培_种子.png, `#23` 23_黑旋风冬瓜种子_高产新改良_种子广告.png, `#26` 26_四季无筋豆种子_高产南北方种植_种子广告.png |
| `ad_signage_art9_abs_authority` | `absolute` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art9_abs_emblem` | `absolute` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art9_abs_superstition` | `absolute` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_edu_art24_public_servant_endorsement` *(new)* | `education` | Violation | 1 | `#33` 33_公考培训宋尚案老师_某省直机关在职领导_身份.png |
| `ad_signage_medical_art7_assertion` | `medical` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art7_compare` | `medical` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art7_cure_rate` | `medical` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art10_commitment` | `pesticide` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art4_assertion` | `pesticide` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_art46_pre_review` | `signage` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_food_beneficiary_count_claim` *(new)* | `signage` | Violation | 1 | `#42` 42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png |
| `ad_signage_signage_food_disease_target` | `signage` | Violation | 8 | `#34` 34_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png, `#35` 35_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png, `#36` 36_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png, `#44` 44_保健品广告_调节血压降低血脂_食品功能.png, `#45` 45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png, `#47` 47_益生菌产品_预防治疗腹泻提高免疫_医疗.png, `#50` 50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png, `#66` 66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg |
| `ad_signage_signage_food_function_claim` | `signage` | Violation | 27 | `#07` 07_保健食品8大优势_提高免疫力消炎止痛.png, `#08` 08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png, `#09` 09_榛蘑_可增强肌体免疫力益智_食用菌.png, `#14` 14_红豆越桔_治疗泌尿系统感染_食品涉及疾病.png, `#19` 19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg, `#25` 25_蜂王浆冻干粉_提高免疫力抵抗力_保健食品.png, `#28` 28_千禧柿子水果_减脂美白抗氧化_食品功能.png, `#29` 29_笨鸡蛋营养价值_增强人体免疫_食品功能.png, `#30` 30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png, `#31` 31_黄豆天然植物蛋白_降低胆固醇_食品功能.png, `#32` 32_白桦树汁植物饮料_提高免疫力儿童和老人.png, `#34` 34_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png, `#35` 35_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png, `#36` 36_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png, `#40` 40_体重管理减肥产品_两月减重31斤_减肥承诺.png, `#42` 42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png, `#43` 43_沙棘维生素C_补充VC提高免疫力_食品功能.png, `#44` 44_保健品广告_调节血压降低血脂_食品功能.png, `#45` 45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png, `#47` 47_益生菌产品_预防治疗腹泻提高免疫_医疗.png, `#48` 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png, `#49` 49_仁和氨糖软骨素钙片手机详情页_保健暗示安全_违规.png, `#50` 50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png, `#53` 53_北大荒椴树雪蜜_提高人体免疫力_保健食品.png, `#54` 54_京东京造菊粉代餐_减肥便秘通便_食品功能.png, `#55` 55_京东京造番茄红素沙棘果油_前列腺养护_食品.png, `#66` 66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg |
| `ad_signage_signage_food_lung_health_claim` *(new)* | `signage` | Violation | 1 | `#41` 41_肺肽片保健食品_清肺排毒提升肺功能_食品.png |
| `ad_signage_signage_food_safety_implication` | `signage` | Violation | 3 | `#09` 09_榛蘑_可增强肌体免疫力益智_食用菌.png, `#49` 49_仁和氨糖软骨素钙片手机详情页_保健暗示安全_违规.png, `#52` 52_北大荒蜂胶软胶囊_保健食品天然暗示_违规.png |
| `ad_signage_signage_medicine_flag` | `signage` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_military_political_marketing` *(new)* | `signage` | Violation | 1 | `#06` 06_八一建军节宣传海报_商业广告.png |
| `ad_signage_signage_weight_loss_data_commitment` *(new)* | `signage` | Violation | 1 | `#40` 40_体重管理减肥产品_两月减重31斤_减肥承诺.png |
| `ad_signage_signage_weight_loss_food_claim` *(new)* | `signage` | Violation | 3 | `#28` 28_千禧柿子水果_减脂美白抗氧化_食品功能.png, `#40` 40_体重管理减肥产品_两月减重31斤_减肥承诺.png, `#54` 54_京东京造菊粉代餐_减肥便秘通便_食品功能.png |
| `ad_signage_veterinary_art4_assertion` | `veterinary` | Violation | 2 | `#37` 37_优普派阿苯达唑片_无效包退800万粉丝_兽药.png, `#38` 38_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png |
| `ad_signage_veterinary_art6_absolute` | `veterinary` | Violation | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_medical_claim` | `cosmetic` | Violation | 2 | `#56` 56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png, `#57` 57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png |
| `cosmetic_art23_medical_explicit` | `cosmetic` | Violation | 1 | `#56` 56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png |
| `cosmetic_art23_misleading_claim` | `cosmetic` | Violation | 0 | (backlog, 本批无案例) |
| `finance_316_art3_2_fraud_guarantee` | `finance` | Violation | 0 | (backlog, 本批无案例) |
| `finance_316_art3_4_government_use` | `finance` | Violation | 0 | (backlog, 本批无案例) |
| `internet_art7_pre_review` | `internet_ad` | Violation | 8 | `#21` 21_72小时紧急避孕药_左炔诺孕酮片OTC.png, `#24` 24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png, `#25` 25_蜂王浆冻干粉_提高免疫力抵抗力_保健食品.png, `#27` 27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png, `#28` 28_千禧柿子水果_减脂美白抗氧化_食品功能.png, `#37` 37_优普派阿苯达唑片_无效包退800万粉丝_兽药.png, `#38` 38_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png, `#40` 40_体重管理减肥产品_两月减重31斤_减肥承诺.png |
| `internet_art8_rx_drug` | `internet_ad` | Violation | 0 | (backlog, 本批无案例) |
| `internet_art8_tobacco` | `internet_ad` | Violation | 0 | (backlog, 本批无案例) |
| `internet_art9_health_softarticle` | `internet_ad` | Violation | 0 | (backlog, 本批无案例) |
| `ad_signage_art11_data_citation` | `signage` | Warning | 9 | `#01` 01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg, `#02` 02_名师教育申论班_龙江第一_绝对化用语.jpg, `#16` 16_蒙恩教育教师资格证_通过率85%_数据引用.png, `#17` 17_智行教育25省考申论保分_快速提分_教育承诺.png, `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#58` 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#62` 62_东郊到家按摩APP_9万人1000万次_数据引用.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |
| `ad_signage_art12_fake_patent` | `absolute` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_art26_re_prm` | `realestate` | Warning | 1 | `#15` 15_银泰集茶巷_品牌加冕财富启航_地产广告.jpeg |
| `ad_signage_art28b_fake_data` | `absolute` | Warning | 4 | `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#63` 63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |
| `ad_signage_art9_abs_pct` | `absolute` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_art9_abs_top` | `absolute` | Warning | 13 | `#01` 01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg, `#02` 02_名师教育申论班_龙江第一_绝对化用语.jpg, `#05` 05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg, `#15` 15_银泰集茶巷_品牌加冕财富启航_地产广告.jpeg, `#30` 30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png, `#39` 39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png, `#42` 42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png, `#57` 57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png, `#58` 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg, `#59` 59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg, `#60` 60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg, `#64` 64_杜蕾斯公交车身_首个公益装_化妆品绝对化.jpg, `#65` 65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg |
| `ad_signage_art9_edu_abs` | `education` | Warning | 1 | `#02` 02_名师教育申论班_龙江第一_绝对化用语.jpg |
| `ad_signage_edu_art24_recommendation` | `education` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_edu_art24_test_authority` | `education` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_fin_art25_endorsement` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_med_art13_newsform` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_med_art6_indications` | `medical` | Warning | 5 | `#04` 04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg, `#24` 24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png, `#27` 27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png, `#46` 46_肺部结节草本产品_清热解毒消肿散结_医疗.png, `#55` 55_京东京造番茄红素沙棘果油_前列腺养护_食品.png |
| `ad_signage_med_art7_army` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_med_art7_compare` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_med_art7_technicality` | `medical` | Warning | 3 | `#04` 04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg, `#24` 24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png, `#27` 27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png |
| `ad_signage_medical_art4_selfuse_label` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art5_contraindication` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art6_adapproval` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art7_endorsement` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art8_commitment` | `medical` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_art10_misleading` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_art14_cert_no` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_art4_unaudited` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_airport` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_cultural_relic` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_government` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_heritage` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_municipal` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_roof` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_school_hospital` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_outdoor_city_art32_traffic` | `outdoor` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art11_approval_no` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art2_unregistered` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art3_overrange` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art4_cure_rate` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art4_endorsement` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art4_safety_violation` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art5_deprecate` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_pesticide_art6_endorsement` | `pesticide` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_re_art26_planned_facility` | `realestate` | Warning | 2 | `#59` 59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg, `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg |
| `ad_signage_re_art26_price_violation` | `realestate` | Warning | 1 | `#59` 59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg |
| `ad_signage_re_art26_time_distance` | `realestate` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_re_art4_sqmeter` | `realestate` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_re_art7_license_no` | `realestate` | Warning | 1 | `#61` 61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg |
| `ad_signage_re_art8_superstition` | `realestate` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_alcohol_drink_scenario` *(new)* | `restricted` | Warning | 1 | `#18` 18_白酒电商页_闻香入口天然桦树清香.png |
| `ad_signage_signage_art29_internet_identifiable` | `signage` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_art29_oneclick_close` | `signage` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_art44_internet_provider` | `signage` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_disease_prevention` | `signage` | Warning | 24 | `#04` 04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg, `#05` 05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg, `#07` 07_保健食品8大优势_提高免疫力消炎止痛.png, `#08` 08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png, `#09` 09_榛蘑_可增强肌体免疫力益智_食用菌.png, `#14` 14_红豆越桔_治疗泌尿系统感染_食品涉及疾病.png, `#19` 19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg, `#24` 24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png, `#27` 27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png, `#30` 30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png, `#32` 32_白桦树汁植物饮料_提高免疫力儿童和老人.png, `#34` 34_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png, `#35` 35_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png, `#36` 36_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png, `#45` 45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png, `#46` 46_肺部结节草本产品_清热解毒消肿散结_医疗.png, `#47` 47_益生菌产品_预防治疗腹泻提高免疫_医疗.png, `#48` 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png, `#50` 50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png, `#53` 53_北大荒椴树雪蜜_提高人体免疫力_保健食品.png, `#55` 55_京东京造番茄红素沙棘果油_前列腺养护_食品.png, `#58` 58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg, `#63` 63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg, `#66` 66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg |
| `ad_signage_signage_gift_to_leader` *(new)* | `signage` | Warning | 1 | `#20` 20_黑尊牛安格斯牛肉礼盒_送领导客户清真.png |
| `ad_signage_signage_infant_milk` | `signage` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_otc_label` | `signage` | Warning | 1 | `#21` 21_72小时紧急避孕药_左炔诺孕酮片OTC.png |
| `ad_signage_veterinary_art10_approval_no` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art3_prohibited` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art4_cure_rate` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art4_endorsement` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art4_safety_violation` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art5_deprecate` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art7_endorsement` | `veterinary` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_veterinary_art8_commitment` | `veterinary` | Warning | 2 | `#37` 37_优普派阿苯达唑片_无效包退800万粉丝_兽药.png, `#38` 38_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png |
| `cosmetic_art17_special_class` | `cosmetic` | Warning | 0 | (backlog, 本批无案例) |
| `cosmetic_art20_claim_basis` | `cosmetic` | Warning | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_ingredients` | `cosmetic` | Warning | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_special_regno` | `cosmetic` | Warning | 0 | (backlog, 本批无案例) |
| `cosmetic_art8_award_claim` | `cosmetic` | Warning | 0 | (backlog, 本批无案例) |
| `cosmetic_art9_abs_extended` | `cosmetic` | Warning | 3 | `#56` 56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png, `#57` 57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png, `#64` 64_杜蕾斯公交车身_首个公益装_化妆品绝对化.jpg |
| `finance_316_art3_1_scope` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_316_art3_2_consumer_right` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_316_art3_2_regulator_use` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_316_art3_3_fair_competition` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_316_art3_6_internet` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_316_art3_7_unlicensed_send` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_art25_endorsement_reinforced` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `finance_art9_abs_investment` | `finance` | Warning | 0 | (backlog, 本批无案例) |
| `internet_art15_popup_close` | `internet_ad` | Warning | 0 | (backlog, 本批无案例) |
| `internet_art21_paid_search` | `internet_ad` | Warning | 0 | (backlog, 本批无案例) |
| `internet_art22_algorithm_disclose` | `internet_ad` | Warning | 0 | (backlog, 本批无案例) |
| `internet_art6_identifiable` | `internet_ad` | Warning | 8 | `#08` 08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png, `#09` 09_榛蘑_可增强肌体免疫力益智_食用菌.png, `#32` 32_白桦树汁植物饮料_提高免疫力儿童和老人.png, `#48` 48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png, `#50` 50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png, `#53` 53_北大荒椴树雪蜜_提高人体免疫力_保健食品.png, `#54` 54_京东京造菊粉代餐_减肥便秘通便_食品功能.png, `#66` 66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg |
| `internet_art6_softarticle` | `internet_ad` | Warning | 0 | (backlog, 本批无案例) |
| `ad_signage_art10_minor` | `minor` | Info | 0 | (backlog, 本批无案例) |
| `ad_signage_art22_tob_alc` | `restricted` | Info | 1 | `#18` 18_白酒电商页_闻香入口天然桦树清香.png |
| `ad_signage_fin_art25_unlawful` | `finance` | Info | 0 | (backlog, 本批无案例) |
| `ad_signage_med_art11_qualifications` | `medical` | Info | 1 | `#05` 05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg |
| `ad_signage_medical_art6_producer` | `medical` | Info | 0 | (backlog, 本批无案例) |
| `ad_signage_medical_art6_registerno` | `medical` | Info | 0 | (backlog, 本批无案例) |
| `ad_signage_signage_art30_self_publish` | `signage` | Info | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_general_fileno` | `cosmetic` | Info | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_license_no` | `cosmetic` | Info | 0 | (backlog, 本批无案例) |
| `cosmetic_art23_safety_warning` | `cosmetic` | Info | 0 | (backlog, 本批无案例) |

> 排序:severity 降序(Violation → Warning → Info)→ ruleId 字典序。
> 0 示例图行 = backlog(规则已就位但本批 66 张无对应案例)。
> *(new)* 标记 = Task 13 新增的 8 条 v9 规则。

## §2 示例图 → 规则

| 文件名 | 桶 | 严重度 | 关联规则数 | 规则 ID 列表 | 状态 |
|---|---|---|---:|---|---|
| `01_碧桂园华美天樾_中国地产三强_绝对化与数据引用.jpg` | `realestate` | Critical | 2 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation` | 弱覆盖(关键词薄) |
| `02_名师教育申论班_龙江第一_绝对化用语.jpg` | `education` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art9_edu_abs`, `ad_signage_art11_data_citation` | 已覆盖 |
| `03_名师教育申论保分班_第一年未录取第二年半价_教育.png` | `education` | Critical | 1 | `ad_signage_art24_edu_guar` | 已覆盖(教育承诺典型) |
| `04_青少年正畸门诊_口腔扫描根管治疗_医疗广告.jpg` | `medical` | Critical | 3 | `ad_signage_signage_disease_prevention`, `ad_signage_med_art6_indications`, `ad_signage_med_art7_technicality` | 已覆盖 |
| `05_五常龙江医院_首选院长亲诊_医疗绝对化.jpg` | `medical` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_signage_disease_prevention`, `ad_signage_med_art11_qualifications` | 已覆盖 |
| `06_八一建军节宣传海报_商业广告.png` | `signage` | Critical | 1 | `ad_signage_signage_military_political_marketing` *(new)* | 未覆盖 |
| `07_保健食品8大优势_提高免疫力消炎止痛.png` | `food_function_claim` | Critical | 2 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `08_蜜蜜游俄罗斯椴树蜜电商页_8大优势_蜂蜜食品.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖 |
| `09_榛蘑_可增强肌体免疫力益智_食用菌.png` | `food_function_claim` | Critical | 4 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `ad_signage_signage_food_safety_implication`, `internet_art6_identifiable` | 已覆盖 |
| `10_玉远龙公考_未进面0收费_教育承诺收益.png` | `education` | Critical | 1 | `ad_signage_art24_edu_guar` | 弱覆盖(关键词薄) |
| `11_公安专项秋考刷题班_高效提分_教育承诺.png` | `education` | Warning | 0 | — | 未覆盖 |
| `12_东北景椒辣妹子种子_早熟高产抗病_种子广告.png` | `agricultural` | Warning | 1 | `ad_signage_art27_seed_yield_guarantee` | 弱覆盖(关键词薄) |
| `13_豌豆种子_多且饱满高产_种子广告.png` | `agricultural` | Warning | 1 | `ad_signage_art27_seed_yield_guarantee` | 弱覆盖(关键词薄) |
| `14_红豆越桔_治疗泌尿系统感染_食品涉及疾病.png` | `food_disease_target` | Critical | 2 | `ad_signage_signage_disease_prevention`, `ad_signage_signage_food_function_claim` | 已覆盖 |
| `15_银泰集茶巷_品牌加冕财富启航_地产广告.jpeg` | `realestate` | Warning | 2 | `ad_signage_art26_re_prm`, `ad_signage_art9_abs_top` | 弱覆盖(品牌加冕/财富启航 = art26;无预售证号 → re7_license_no 缺失) |
| `16_蒙恩教育教师资格证_通过率85%_数据引用.png` | `data_citation` | Warning | 1 | `ad_signage_art11_data_citation` | 已覆盖 |
| `17_智行教育25省考申论保分_快速提分_教育承诺.png` | `education` | Warning | 2 | `ad_signage_art11_data_citation`, `ad_signage_art24_edu_guar` | 弱覆盖(关键词薄) |
| `18_白酒电商页_闻香入口天然桦树清香.png` | `food_function_claim` | Info | 2 | `ad_signage_art22_tob_alc`, `ad_signage_signage_alcohol_drink_scenario` *(new)* | 弱覆盖(关键词薄) |
| `19_蜂胶胶囊整图_提高免疫力消炎止痛_保健食品.jpeg` | `food_function_claim` | Critical | 2 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `20_黑尊牛安格斯牛肉礼盒_送领导客户清真.png` | `realestate` | Warning | 1 | `ad_signage_signage_gift_to_leader` *(new)* | 未覆盖 |
| `21_72小时紧急避孕药_左炔诺孕酮片OTC.png` | `medical` | Warning | 2 | `ad_signage_signage_otc_label`, `internet_art7_pre_review` | 已覆盖 |
| `22_坤丰绿旋风2号辣椒种子_高产南北方栽培_种子.png` | `agricultural` | Warning | 1 | `ad_signage_art27_seed_yield_guarantee` | 弱覆盖(关键词薄) |
| `23_黑旋风冬瓜种子_高产新改良_种子广告.png` | `agricultural` | Warning | 1 | `ad_signage_art27_seed_yield_guarantee` | 弱覆盖(关键词薄) |
| `24_嘉润医院骨科_UBE脊柱内镜技术_医疗广告.png` | `medical` | Critical | 4 | `ad_signage_med_art6_indications`, `ad_signage_med_art7_technicality`, `ad_signage_signage_disease_prevention`, `internet_art7_pre_review` | 已覆盖 |
| `25_蜂王浆冻干粉_提高免疫力抵抗力_保健食品.png` | `food_function_claim` | Critical | 2 | `ad_signage_signage_food_function_claim`, `internet_art7_pre_review` | 已覆盖 |
| `26_四季无筋豆种子_高产南北方种植_种子广告.png` | `agricultural` | Warning | 1 | `ad_signage_art27_seed_yield_guarantee` | 弱覆盖(关键词薄) |
| `27_浮针疗法医疗培训_安全无痛见效快_医疗广告.png` | `medical` | Critical | 4 | `ad_signage_med_art6_indications`, `ad_signage_med_art7_technicality`, `ad_signage_signage_disease_prevention`, `internet_art7_pre_review` | 已覆盖 |
| `28_千禧柿子水果_减脂美白抗氧化_食品功能.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `internet_art7_pre_review`, `ad_signage_signage_weight_loss_food_claim` *(new)* | 弱覆盖(关键词薄) |
| `29_笨鸡蛋营养价值_增强人体免疫_食品功能.png` | `food_function_claim` | Critical | 1 | `ad_signage_signage_food_function_claim` | 已覆盖 |
| `30_卡巴迪油莎豆油_降三高抗肿瘤抗衰老_食品.png` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `31_黄豆天然植物蛋白_降低胆固醇_食品功能.png` | `food_function_claim` | Critical | 1 | `ad_signage_signage_food_function_claim` | 已覆盖 |
| `32_白桦树汁植物饮料_提高免疫力儿童和老人.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖(适合人群儿童和老人 = function_claim) |
| `33_公考培训宋尚案老师_某省直机关在职领导_身份.png` | `education` | Critical | 1 | `ad_signage_edu_art24_public_servant_endorsement` *(new)* | 弱覆盖(权威身份类违规典型) |
| `34_PICC松针油_降胆固醇辅助降压消炎恢复_保健.png` | `food_disease_target` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention` | 已覆盖(降胆固醇/降压 = food_disease_target;保健食品不能含医疗用语) |
| `35_纳豆红曲地龙蛋白片_溶解血栓调节血糖_保健.png` | `food_disease_target` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `ad_signage_signage_food_disease_target` | 已覆盖 |
| `36_水飞蓟籽油葛根枳椇子片_保肝降血脂_保健.png` | `food_disease_target` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `37_优普派阿苯达唑片_无效包退800万粉丝_兽药.png` | `pestvet` | Critical | 3 | `ad_signage_veterinary_art8_commitment`, `ad_signage_veterinary_art4_assertion`, `internet_art7_pre_review` | 已覆盖(无效包退/800万粉丝 = vet commitment;电商页 = internet_art7) |
| `38_优普派非泼罗尼滴剂_药效持久800万粉丝_兽药.png` | `pestvet` | Critical | 3 | `ad_signage_veterinary_art8_commitment`, `ad_signage_veterinary_art4_assertion`, `internet_art7_pre_review` | 已覆盖 |
| `39_百自分高效氯氟氰菊酯_杀虫广谱害虫触杀_农药.png` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art28b_fake_data`, `ad_signage_art11_data_citation` | 已覆盖 |
| `40_体重管理减肥产品_两月减重31斤_减肥承诺.png` | `weight_loss` | Critical | 4 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_weight_loss_data_commitment` *(new)*, `ad_signage_signage_weight_loss_food_claim` *(new)*, `internet_art7_pre_review` | 弱覆盖(关键词薄) |
| `41_肺肽片保健食品_清肺排毒提升肺功能_食品.png` | `food_function_claim` | Critical | 1 | `ad_signage_signage_food_lung_health_claim` *(new)* | 未覆盖 |
| `42_寒地森林有机茶_行业领导品牌滋养14亿_绝对化.png` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_beneficiary_count_claim` *(new)* | 弱覆盖(关键词薄) |
| `43_沙棘维生素C_补充VC提高免疫力_食品功能.png` | `food_function_claim` | Critical | 1 | `ad_signage_signage_food_function_claim` | 已覆盖 |
| `44_保健品广告_调节血压降低血脂_食品功能.png` | `food_function_claim` | Critical | 2 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target` | 已覆盖 |
| `45_杜仲雄花茶_植物软黄金降三高助睡眠_食品.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `46_肺部结节草本产品_清热解毒消肿散结_医疗.png` | `medical` | Critical | 2 | `ad_signage_med_art6_indications`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `47_益生菌产品_预防治疗腹泻提高免疫_医疗.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `48_玛莉魔粉黄瓜芹菜葡萄籽粉_血压血脂降下去.png` | `food_disease_target` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖 |
| `49_仁和氨糖软骨素钙片手机详情页_保健暗示安全_违规.png` | `food_function_claim` | Critical | 2 | `ad_signage_signage_food_safety_implication`, `ad_signage_signage_food_function_claim` | 已覆盖 |
| `50_大兴安岭野生蓝莓果粉_辅助调节血糖水平_食品.png` | `food_function_claim` | Critical | 4 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖 |
| `51_哈尔滨万通电工PLC培训_见效快回报高_教育.png` | `education` | Warning | 1 | `ad_signage_art24_edu_guar` | 弱覆盖(关键词薄) |
| `52_北大荒蜂胶软胶囊_保健食品天然暗示_违规.png` | `food_function_claim` | Critical | 1 | `ad_signage_signage_food_safety_implication` | 已覆盖 |
| `53_北大荒椴树雪蜜_提高人体免疫力_保健食品.png` | `food_function_claim` | Critical | 3 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖(提高人体免疫力 = food_function_claim;电商页 = internet_art6) |
| `54_京东京造菊粉代餐_减肥便秘通便_食品功能.png` | `weight_loss` | Critical | 3 | `ad_signage_signage_food_function_claim`, `internet_art6_identifiable`, `ad_signage_signage_weight_loss_food_claim` *(new)* | 弱覆盖(关键词薄) |
| `55_京东京造番茄红素沙棘果油_前列腺养护_食品.png` | `medical` | Critical | 3 | `ad_signage_med_art6_indications`, `ad_signage_signage_food_function_claim`, `ad_signage_signage_disease_prevention` | 弱覆盖(关键词薄) |
| `56_妆颜如玉绎雪回春精华液_激素依赖性皮炎_化妆品.png` | `cosmetics` | Critical | 3 | `cosmetic_art23_medical_claim`, `cosmetic_art23_medical_explicit`, `cosmetic_art9_abs_extended` | 已覆盖 |
| `57_妆颜如玉蓝绷带_国内首创速度第一_化妆品.png` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `cosmetic_art9_abs_extended`, `cosmetic_art23_medical_claim` | 已覆盖 |
| `58_Ulike蓝宝石脱毛仪_连续6年销量第一_绝对化.jpg` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation`, `ad_signage_signage_disease_prevention` | 已覆盖 |
| `59_凯利集团汽车后服务市场_升涨机会钱景新区发展_6888元㎡23万起_地产.jpg` | `realestate` | Warning | 3 | `ad_signage_art9_abs_top`, `ad_signage_re_art26_planned_facility`, `ad_signage_re_art26_price_violation` | 已覆盖 |
| `60_哈佛特区_出门即校门接送不烦恼_60-139㎡学府世家_地产.jpg` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation`, `ad_signage_art28b_fake_data` | 已覆盖 |
| `61_三元教育公考_哈尔滨排名第一通过率75%_教育.jpg` | `realestate` | Warning | 2 | `ad_signage_re_art26_planned_facility`, `ad_signage_re_art7_license_no` | 弱覆盖(关键词薄) |
| `62_东郊到家按摩APP_9万人1000万次_数据引用.jpg` | `data_citation` | Warning | 1 | `ad_signage_art11_data_citation` | 已覆盖 |
| `63_糖尿病虚假宣传_吃喝不忌口血糖不再高_医疗.jpg` | `medical` | Critical | 2 | `ad_signage_signage_disease_prevention`, `ad_signage_art28b_fake_data` | 已覆盖 |
| `64_杜蕾斯公交车身_首个公益装_化妆品绝对化.jpg` | `absolute` | Warning | 2 | `ad_signage_art9_abs_top`, `cosmetic_art9_abs_extended` | 已覆盖 |
| `65_蟹都汇总部_全国销量第一领导品牌_绝对化.jpg` | `absolute` | Critical | 3 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation`, `ad_signage_art28b_fake_data` | 已覆盖 |
| `66_小园玉粱紫玉米花青素_增强免疫糖尿病安心_食品.jpg` | `food_function_claim` | Critical | 4 | `ad_signage_signage_food_function_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention`, `internet_art6_identifiable` | 已覆盖 |

> 排序:文件名升序。
> 状态枚举:已覆盖 / 弱覆盖(关键词薄) / 未覆盖。
> 规则 ID 列表中 *(new)* 标记保留 = 该规则属 v9 新规则(共 8 条)。

## §3 覆盖率统计

- 规则总数:**129**
- 有示例图的规则:**34** / 129 (26%)
- 无示例图的规则(backlog):**95** / 129
- 示例图总数:**66**
- 被规则覆盖的图(已覆盖 + 弱覆盖):**62** / 66 (93%)
- 无规则覆盖的图(backlog):**4** / 66 (#06, #11, #20, #41)
- 弱覆盖(关键词薄)的图:**18** / 66 (#01, #10, #12, #13, #15, #17, #18, #22, #23, #26, #28, #33, #40, #42, #51, #54, #55, #61)
- 新规则覆盖的图:**9** (#06, #18, #20, #28, #33, #40, #41, #42, #54 — `ad_signage_signage_weight_loss_food_claim` 覆盖 #28 + #40 + #54 三条)
- 空桶(规则已就位, 待补示例图):`finance` / `minor` / `outdoor` / `fake_data`
