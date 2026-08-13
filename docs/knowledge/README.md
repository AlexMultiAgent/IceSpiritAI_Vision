# IceSpirit Family — Knowledge Base (2026-08)

本目录是冰灵家族三个项目(冰灵慧语 / 智译 / 锐目)共享的技术研判依据库。
首版建于 **冰灵锐目** 项目内(`D:\github\IceSpiritAI_Vision\docs\knowledge\`),作为权威参考;其余项目可通过 git 仓库结构或显式复制引用。

## 文件索引

| 文件 | 内容 |
|---|---|
| [`build-stack-2026-08.md`](./build-stack-2026-08.md) | AGP / Kotlin / Gradle / JDK / NDK / AndroidX / 主流三方库的 2026-08 实际版本与硬约束矩阵。Phase 1 baseline 研判的主文档。 |
| [`cross-project-implications.md`](./cross-project-implications.md) | 上述 baseline 对冰灵智译 / 冰灵慧语两个项目的迁移含义与建议顺序。 |

## 调研时间与维护

- 首次建立: **2026-08-13**(基于外部 WebFetch / WebSearch 的实证查询)
- 知识截止对比: 本文档中的部分细节由开发者官网发布说明 + GitHub Release notes 双向核对
- **再校准周期**: 每 6 个月重新验证一次外部版本(开发者生态月度级变化,新版固定时间窗短)
- 单项字段若有"约 / 待核"标注,代表未拿到一手 release notes,需后续追查

## 与项目文档的关系

- 项目根 `CLAUDE.md` 记录三项目**统一字段**(命名空间 / Gradle root / 主题 / minSdk 等)
- 本目录记录的是 **研判过程、版本依据、兼容性矩阵**,不是硬约束
- 任何 baseline 决策须在文档里登记 **决策日期 + 依据 + 替代选项**,便于后续审计

## 已知缺口

- ONNX Runtime Android artifact 的具体 AAR 体积与最新版本兼容(API 36 / minSdk 26)未逐项验证,需要 Phase 1 plan 中加入"启动期烟测"
- Kotlin 2.4.10 的 stdlib 大小变化对 APK 体积的实证未做
- AGP 9.x 强制 `uniquePackageNames=true` 对既有 AndroidManifest 包名分布的影响未实测