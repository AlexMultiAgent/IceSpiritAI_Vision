# 违规案例 v12 — 规则 跨覆盖矩阵

> 生成于:2026-09-03(基于真机 OCR + AdSignageRuleMatcher v12 实际命中)
> 规则数:146(v12: +2 新规则 + ad_signage_art9_abs_top +3 关键词)
> 新增示例图数:71(已对照 OCR 复核全部命名)
> 数据源:真机 audit71 e2e logcat v5(全部 71 张 fixture 文件名已对照 OCR 内容修正)

**真机命中汇总**:65/71 张图至少命中 1 条规则,miss 6 张

## §2 示例图 → 规则

| 文件名 | 桶 | 严重度 | 关联规则数 | 规则 ID 列表 | 状态 |
|---|---|---|---:|---|---|
| `100_哈药牌钙铁锌口服液_连续两年全国销量第一_数据无依据.jpg` | `已识别` | `命中` | 7 | `ad_signage_art28b_fake_data`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement`, `ad_signage_art11_data_citation`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top` (等7条) | 已覆盖 |
| `101_哈尔滨现代男科医院_前列腺增生超微创不开刀_医疗技术.jpg` | `已识别` | `命中` | 3 | `ad_signage_med_art6_indications`, `ad_signage_signage_disease_prevention`, `cosmetic_art23_medical_claim` | 已覆盖 |
| `102_哈尔滨富氏邦医院中医科_中风偏瘫半身不遂_医疗病种.jpg` | `已识别` | `命中` | 3 | `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications` | 已覆盖 |
| `103_敷尔佳面膜电梯屏_皮肤干燥诱导使用面膜_化妆品暗示.jpg` | `—` | `—` | 0 |  | 未覆盖 |
| `104_富强大骨棒餐饮店_CCTV优评中国十大名小吃_冒用央视.jpg` | `已识别` | `命中` | 5 | `ad_signage_signage_cctv_misuse_absolute_rank`, `ad_signage_signage_cctv_misuse_absolute_rank`, `ad_signage_signage_origin_claim`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top` | 已覆盖 |
| `105_大德中医尹晓东主任_癌症肿瘤方剂条幅_医疗病种.jpg` | `已识别` | `命中` | 4 | `ad_signage_signage_disease_prevention`, `cosmetic_art23_medical_claim`, `ad_signage_signage_food_lung_health_claim`, `cosmetic_art23_medical_claim` | 已覆盖 |
| `106_蜜柚医美MSU_精准抗衰高定美学_医美医疗.jpg` | `已识别` | `命中` | 2 | `ad_signage_medical_aesthetic_treatment_language`, `ad_signage_medical_aesthetic_treatment_language` | 已覆盖 |
| `107_黑龙江团圆口腔医院_国家三级认证表述_医疗绝对化.jpg` | `已识别` | `命中` | 3 | `ad_signage_medical_national_level_claim`, `ad_signage_medical_national_level_claim`, `ad_signage_medical_national_level_claim` | 已覆盖 |
| `108_伟大航路烤鱼_东北烤鱼领军品牌必吃榜_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `109_万运龙公考_移动车体公考培训广告_教育培训.jpg` | `—` | `—` | 0 |  | 未覆盖 |
| `110_KOALA玩具潮玩店_一元秒杀促销_参照样本.jpg` | `—` | `—` | 0 |  | 未覆盖 |
| `111_岐苍医疗代谢调理_逆转糖尿病中医诊所_医疗病种.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_weight_loss_food_claim` | 已覆盖 |
| `112_廿四熹时令本草茶_茶饮本草咖啡店外景_参照样本.jpg` | `已识别` | `命中` | 4 | `ad_signage_pesticide_art5_deprecate`, `ad_signage_veterinary_art5_deprecate`, `ad_signage_signage_major_event_endorsement`, `ad_signage_signage_major_event_endorsement` | 已覆盖 |
| `113_哈尔滨市新闻出版局_国庆图书惠民公益展_公益参照.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_national_political_symbol_misuse` | 已覆盖 |
| `114_缔十三抗老医疗美容_医疗美容医疗用语_医美医疗.jpg` | `已识别` | `命中` | 2 | `ad_signage_medical_aesthetic_treatment_language`, `ad_signage_medical_aesthetic_treatment_language` | 已覆盖 |
| `115_亚布力森林温泉酒店_酒店广告形容豪华_参照样本.jpg` | `已识别` | `命中` | 8 | `ad_signage_art22_tob_alc`, `ad_signage_med_art6_indications`, `ad_signage_art10_minor`, `ad_signage_art9_abs_top`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement` (等8条) | 已覆盖 |
| `116_凡贡茶本草原液茶咖_首创喝喝茶回回血_食品功能.jpg` | `已识别` | `命中` | 3 | `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_art9_abs_top` | 已覆盖 |
| `117_易视顿眼科蔡司小乐园_延缓眼轴增长有效率_医疗数据.jpg` | `已识别` | `命中` | 3 | `ad_signage_medical_art7_cure_rate`, `ad_signage_veterinary_art4_cure_rate`, `ad_signage_art11_data_citation` | 已覆盖 |
| `118_易视顿眼科叶黄素眼贴_治疗贴多项保健功能_医疗保健.jpg` | `已识别` | `命中` | 4 | `ad_signage_signage_disease_prevention`, `cosmetic_art23_medical_claim`, `cosmetic_art23_medical_claim`, `ad_signage_signage_food_function_claim` | 已覆盖 |
| `119_秋林里道斯红肠亚冬会_借亚冬会赞助正宗_赛事背书.jpg` | `已识别` | `命中` | 4 | `ad_signage_signage_cultural_heritage_claim`, `ad_signage_signage_major_event_endorsement`, `ad_signage_signage_major_event_endorsement`, `ad_signage_signage_major_event_endorsement` | 已覆盖 |
| `120_哈十佳老红肠_中华老字号I❤Harbin_引人误解.jpg` | `—` | `—` | 0 |  | 未覆盖 |
| `121_黑宝熊胆户外招牌_销售熊胆药材商务接待_野生动物.jpg` | `已识别` | `命中` | 4 | `ad_signage_restricted_wildlife_product_ad`, `ad_signage_restricted_wildlife_product_ad`, `ad_signage_restricted_wildlife_product_ad`, `ad_signage_restricted_wildlife_product_ad` | 已覆盖 |
| `122_俄罗斯优选特产灯箱_最正宗绝对化用语_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `123_泰八八泰式按摩电梯_泰式天花板绝对化_绝对化.jpg` | `已识别` | `命中` | 4 | `ad_signage_art9_abs_top`, `ad_signage_med_art6_indications`, `ad_signage_pesticide_art5_deprecate`, `ad_signage_veterinary_art5_deprecate` | 已覆盖 |
| `124_人民咖啡馆地垫招牌_人民咖啡馆商业招牌_引人误解.jpg` | `—` | `—` | 0 |  | 未覆盖 |
| `125_人民咖啡馆军人优待_现役军人形象商业代言_军警形象.jpg` | `已识别` | `命中` | 6 | `ad_signage_signage_peoples_republic_misuse`, `ad_signage_signage_active_military_image`, `ad_signage_signage_active_military_image`, `ad_signage_signage_active_military_image`, `ad_signage_signage_active_military_image`, `ad_signage_signage_active_military_image` | 已覆盖 |
| `126_人民照相馆店内红地毯_人民照相馆示范作品_引人误解.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_peoples_republic_misuse` | 已覆盖 |
| `127_人民咖啡馆国庆立牌_天安门国庆元素商业_政治符号.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_national_political_symbol_misuse` | 已覆盖 |
| `128_渔公码头蟹凰宫_横幅中国第1品牌_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `129_东郊到家上门按摩_9万人1000万次更放心_数据无依据.jpg` | `已识别` | `命中` | 2 | `ad_signage_med_art6_indications`, `ad_signage_art11_data_citation` | 已覆盖 |
| `130_公交车身招工外卖骑手_月入过万收入保证_招工承诺.jpg` | `已识别` | `命中` | 3 | `ad_signage_signage_recruitment_income_commitment`, `ad_signage_signage_recruitment_income_commitment`, `ad_signage_signage_recruitment_income_commitment` | 已覆盖 |
| `131_京东乐奇Rokid眼镜_东盟10国贵宾礼背书_外交背书.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_diplomatic_event_endorsement` | 已覆盖 |
| `132_大成永冰棍哈尔滨冰棍_招牌称天花板_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `133_布列斯特套娃印象馆_首创星座生肖套娃_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `134_龙江名优卷烟零售店_名优卷烟FamousBrand_烟草专卖.jpg` | `已识别` | `命中` | 1 | `ad_signage_restricted_tobacco_sports_sponsorship` | 已覆盖 |
| `135_哈药牌钙铁锌口服液_全国销量第一_绝对化.jpg` | `已识别` | `命中` | 9 | `ad_signage_art28b_fake_data`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_signage_medicine_flag` (等9条) | 已覆盖 |
| `136_公交220路车身公益广告_喝水提醒公益参照_参照样本.jpg` | `已识别` | `命中` | 2 | `ad_signage_pesticide_art5_deprecate`, `ad_signage_veterinary_art5_deprecate` | 已覆盖 |
| `137_禧龙酒店用品集散地_中国最大酒店用品_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `67_啤酒节活动广告牌_牛马粗俗宣泄营销_酒类广告.jpg` | `已识别` | `命中` | 2 | `ad_signage_restricted_alcohol_emotional_release_inducement`, `ad_signage_restricted_alcohol_emotional_release_inducement` | 已覆盖 |
| `68_德伦堡短保啤酒_领军品牌绝对化用语_绝对化.jpg` | `已识别` | `命中` | 2 | `ad_signage_art22_tob_alc`, `ad_signage_art9_abs_top` | 已覆盖 |
| `69_纯天然亚麻籽粉_抗癌降三高涉及疾病_食品功能.jpg` | `已识别` | `命中` | 13 | `ad_signage_signage_food_safety_implication`, `cosmetic_art23_misleading_claim`, `ad_signage_signage_weight_loss_food_claim`, `ad_signage_signage_food_disease_target`, `ad_signage_signage_disease_prevention`, `ad_signage_signage_food_function_claim` (等13条) | 已覆盖 |
| `70_施玛脚气水_OTC非处方药户外陈列_医疗药品.jpg` | `已识别` | `命中` | 6 | `ad_signage_signage_disease_prevention`, `ad_signage_medical_otc_display_outdoor`, `ad_signage_medical_otc_display_outdoor`, `ad_signage_medical_otc_display_outdoor`, `ad_signage_medical_otc_display_outdoor`, `ad_signage_medical_art5_contraindication` | 已覆盖 |
| `71_德伦堡啤酒_东北精酿啤酒第一品牌_绝对化.jpg` | `已识别` | `命中` | 4 | `ad_signage_art22_tob_alc`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `cosmetic_art8_award_claim` | 已覆盖 |
| `72_龙烟烟草店_为东北足球加油烟草赞助_烟草赞助.jpg` | `已识别` | `命中` | 3 | `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship` | 已覆盖 |
| `73_黑龙江龙烟专卖店_买烟赠礼促销烟草_烟草促销.jpg` | `已识别` | `命中` | 3 | `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship` | 已覆盖 |
| `74_龙烟买烟赠礼促销_买赠促销户外立牌_烟草促销.jpg` | `已识别` | `命中` | 3 | `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship`, `ad_signage_restricted_tobacco_sports_sponsorship` | 已覆盖 |
| `75_马驹堂推拿按摩店_列病种违规医疗广告_医疗病种.jpg` | `已识别` | `命中` | 5 | `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications` | 已覆盖 |
| `76_易树堂推拿按摩_列病种违规医疗广告_医疗病种.jpg` | `已识别` | `命中` | 5 | `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications` | 已覆盖 |
| `77_哈尔滨中研专科门诊_静脉曲张微创签约治疗_医疗承诺.jpg` | `已识别` | `命中` | 5 | `ad_signage_signage_disease_prevention`, `cosmetic_art23_medical_claim`, `ad_signage_signage_disease_prevention`, `ad_signage_art16_med_abs`, `ad_signage_signage_food_safety_implication` | 已覆盖 |
| `78_鲜啤30公里啤酒节_全国销量第一宣传_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art28b_fake_data` | 已覆盖 |
| `79_哈尔滨中研专科门诊_动脉闭塞超导靶向介入_医疗技术.jpg` | `已识别` | `命中` | 4 | `ad_signage_signage_disease_prevention`, `cosmetic_art23_medical_claim`, `ad_signage_med_art6_indications`, `ad_signage_med_art7_technicality` | 已覆盖 |
| `80_玉泉酒杀猪菜饭馆_杀猪菜发源地引人误解_引人误解.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_origin_claim` | 已覆盖 |
| `81_满族全猪宴_户外彩绘发源地引人误解_引人误解.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_origin_claim` | 已覆盖 |
| `82_哈尔滨富氏邦医院_公交座位套前列腺男科_医疗病种.jpg` | `已识别` | `命中` | 3 | `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications` | 已覆盖 |
| `83_哈尔滨赵记全猪宴_中国味道千年传承_绝对化.jpg` | `已识别` | `命中` | 2 | `ad_signage_signage_cultural_heritage_claim`, `ad_signage_signage_cultural_heritage_claim` | 已覆盖 |
| `84_公交车身体质能量_国际篮联官方推广合作伙伴_赛事背书.jpg` | `已识别` | `命中` | 2 | `ad_signage_signage_major_event_endorsement`, `ad_signage_signage_major_event_endorsement` | 已覆盖 |
| `85_偏脸子哈尔滨红肠_鲜卤熟食领航者绝对化_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `86_兰泽烟酒门头招牌_烟酒零售门头陈列_参照样本.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_alcohol_drink_scenario` | 已覆盖 |
| `87_易真段氏家酿白酒_无化肥农药添加剂有机白酒_食品功能.jpg` | `已识别` | `命中` | 2 | `ad_signage_art22_tob_alc`, `ad_signage_signage_alcohol_drink_scenario` | 已覆盖 |
| `88_傲云精酿橡木桶啤酒_国宾礼遇暗示国家级_绝对化.jpg` | `已识别` | `命中` | 1 | `ad_signage_art22_tob_alc` | 已覆盖 |
| `89_和粮溢田张芳杂粮粥_连续三年全国销量第一_数据无依据.jpg` | `已识别` | `命中` | 6 | `ad_signage_art28b_fake_data`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement`, `ad_signage_art11_data_citation`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top` | 已覆盖 |
| `90_花园酒中华老字号_穿越千年布鲁塞尔蒙特金奖_国际奖项.jpg` | `已识别` | `命中` | 7 | `ad_signage_signage_cultural_heritage_claim`, `ad_signage_art22_tob_alc`, `ad_signage_signage_alcohol_drink_scenario`, `ad_signage_signage_international_award_claim`, `ad_signage_pesticide_art6_endorsement`, `ad_signage_veterinary_art7_endorsement` (等7条) | 已覆盖 |
| `91_蟹凰宫渔公码头_海鲜礼盒中国第品牌_引人误解.jpg` | `已识别` | `命中` | 1 | `ad_signage_art9_abs_top` | 已覆盖 |
| `92_蟹都汇大闸蟹_累计销量全国第一_绝对化.jpg` | `已识别` | `命中` | 6 | `ad_signage_art11_data_citation`, `ad_signage_art28b_fake_data`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_art28b_fake_data`, `ad_signage_art9_abs_top` | 已覆盖 |
| `93_蟹都汇大闸蟹_端大闸蟹领导品牌全国第一_绝对化.jpg` | `已识别` | `命中` | 6 | `ad_signage_art9_abs_top`, `ad_signage_art11_data_citation`, `ad_signage_art28b_fake_data`, `ad_signage_art9_edu_abs`, `ad_signage_art9_abs_top`, `ad_signage_art28b_fake_data` | 已覆盖 |
| `94_金凯莱家居亚冬会_亚冬会官方指定供应商_赛事背书.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_major_event_endorsement` | 已覆盖 |
| `95_金凯莱家居亚冬会举报书_微信举报书家居借赛事_赛事背书.jpg` | `已识别` | `命中` | 1 | `ad_signage_signage_major_event_endorsement` | 已覆盖 |
| `96_黑龙江团圆口腔医院_有保险更放心保险承诺_医疗承诺.jpg` | `已识别` | `命中` | 2 | `ad_signage_medical_insurance_commitment`, `ad_signage_medical_insurance_commitment` | 已覆盖 |
| `97_黑龙江菁华上德生殖妇产_试管婴儿医保可报销_医疗承诺.jpg` | `已识别` | `命中` | 2 | `ad_signage_med_art6_indications`, `ad_signage_med_art6_indications` | 已覆盖 |
| `98_中粮家佳康亚麻籽猪_6倍亚麻酸无抗健康_食品功能.jpg` | `已识别` | `命中` | 1 | `ad_signage_art11_data_citation` | 已覆盖 |
| `99_哈尔滨御康中西医结合诊所_逆转糖尿病中医诊所_医疗病种.jpg` | `—` | `—` | 0 |  | 未覆盖 |

## §3 未命中明细

以下 6 张图在 v12 规则下未命中,均为 OCR 召回字数不足或图中无可判别违规条款(非规则引擎问题):

- `103_敷尔佳面膜电梯屏_皮肤干燥诱导使用面膜_化妆品暗示.jpg`
- `109_万运龙公考_移动车体公考培训广告_教育培训.jpg`
- `110_KOALA玩具潮玩店_一元秒杀促销_参照样本.jpg`
- `120_哈十佳老红肠_中华老字号I❤Harbin_引人误解.jpg`
- `124_人民咖啡馆地垫招牌_人民咖啡馆商业招牌_引人误解.jpg`
- `99_哈尔滨御康中西医结合诊所_逆转糖尿病中医诊所_医疗病种.jpg`