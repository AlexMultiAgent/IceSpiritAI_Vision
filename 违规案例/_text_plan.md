# text fixture 采集日志

> 追踪每条 text fixture 的来源 URL、是否通过 exact-set 测试、采集日期。
> AdSignageTextFixtureRegressionTest 在每条 fixture 上断言
> `matcher.scan(originalAdText)` 返回的 rule ID 集合 === fixture 的 `预期命中规则` 集合。

## medical 桶（5 条，Task 3 完成）

- [x] text_medical_ykzp_01 — https://www.gz.gov.cn/zwgk/zdly/spypaq/zlxx/bjspzlxx/content/post_10559866.html / OK / 2026-08-25 采集（市场监管总局公布典型案例，2024-03-15 通报）
- [x] text_medical_zszn_01 — https://www.sdqixia.gov.cn/art/2023/8/3/art_31418_2943755.html / OK / 2026-08-25 采集（栖霞市市场监管局 2023-08-03 通报）
- [x] text_medical_wzyl_01 — https://scjgj.beijing.gov.cn/zwxx/scjgdt/202606/t20260621_4708636.html / OK / 2026-08-25 采集（北京市市场监管局曝光台 2026-06-21 通报）
- [x] text_medical_ylqx_01 — https://www.mas.gov.cn/xxgk/openness/detail/content/69fbfe0e8866888e3e8b4576.html / OK / 2026-08-25 采集（马鞍山市市场监管局 马市监处罚〔2026〕193号 2026-05-07）
- [x] text_medical_tjyp_01 — https://scjg.jiaozuo.gov.cn/2025/05-13/157778.html / OK / 2026-08-25 采集（焦作市市场监管局、卫健委联合 2025-05-13 通报）

### medical 桶测试结果

- `every text fixture has matching rule hits (exact set)` → **PASS**（5 条全部精确 set 命中）
- `minimum 30 text fixtures collected` → FAIL（仅 5/30，后续任务继续补 25 条）
- `all 13 buckets represented across fixtures` → FAIL（仅 medical 桶覆盖，后续任务继续补 12 桶）

### medical 桶注意事项

1. **`无效退款` 是 medical / pesticide / veterinary 三域共享关键词**（《医疗器械广告审查发布标准》§8、《农药广告审查发布规则》§10、《兽药广告审查发布规定》§8 均有相同禁令）。text_medical_ylqx_01 / text_medical_tjyp_01 的 `预期命中规则` 因此同时包含 medical_art8_commitment + pesticide_art10_commitment + veterinary_art8_commitment。
2. **`根治率` 含 `根治` 子串**：text_medical_zszn_01 同时命中 ad_signage_art16_med_abs（医疗广告绝对化断言）+ art16_med_health + medical_art7_cure_rate。
3. **`专治` 在 med_art6_indications 与 med_art7_technicality 共享**，两者同时命中是预期的。