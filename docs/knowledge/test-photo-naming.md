# 测试照片命名规则

`违规案例/` 下图片素材的两档命名约定 — 区分「已 OCR 分类的 fixture 图」与「未分类的原始素材」,避免微信截屏默认名(`微信图片_20260819100008_5_2011.jpg`)污染 fixture 列表。

## 规则

| 档 | 模式 | 例子 | 配套要求 |
|---|---|---|---|
| **Fixture 图**(已 OCR + 分类) | `<category>_<scene>_<seq>.<ext>` | `medical_store_01.jpg` | 必须有同名 `.md` sidecar;`.md` frontmatter 显式引用该图 |
| **Inbox / 原始素材**(采集后未分类) | `inbox_<source>_<yyyymmdd>_<seq>.<ext>` | `inbox_wechat_20260819_01.jpg` | 不入 fixture 测试,**不入 git**;等 OCR 分类后升档 |

## 字段说明

- `<category>` — `medical` / `absolute` / `education` / `food` / `realestate` / `finance` / `cosmetic` / `agricultural` / `signage` / `minor` / `outdoor` / `internet_ad` / `pestvet` 共 13 类,与 `text_*.md` 的 13 桶同构
- `<scene>` — 简短中文/英文 slug,描述违规场景(如 `store` 药店 / `ykzp` 药店招牌 / `tssj` 特殊膳食);同一 bucket 内 scene 唯一
- `<seq>` — 2 位零填充(`01` 到 `99`),同 `<category>_<scene>` 下累加
- `<source>` — 采集来源类别(不是文件名):
  - `wechat` — 微信截图
  - `samr` — 国家市场监督管理总局站
  - `province.gov` — 各省/直辖市政府站
  - `commercial` — 商业站点(广告主/品牌站)
  - `camera` — 自拍/现场拍摄
- `<yyyymmdd>` — 采集日期(8 位无连字符),用于 inbox 排序
- `<seq>` (inbox) — 2 位零填充,**同日同源累加**;不同日/不同源独立计数

## 升档路径(inbox → fixture)

```
inbox_wechat_20260819_03.jpg
   │
   ├─→ OCR 识别文字 → 关键词命中规则查询
   ├─→ 人工判定 category(13 桶之一)
   ├─→ 创建 sidecar .md:
   │     违规案例/<category>_<scene>_<seq>.md
   │     frontmatter 含「参考图片: <category>_<scene>_<seq>.jpg」字段
   ├─→ 删除原 inbox 文件(不留双份)
   └─→ 新文件入 git
```

**严禁**:fixture 图 与 inbox 图 共存同一 category 的同 seq(避免 fixture 列表里出现一图两源)。

## 反模式(不要做的事)

| 反模式 | 为什么错 |
|---|---|
| 直接 commit `微信图片_xxx_xxx.jpg` 默认名 | 默认名带时间戳+索引,无语义;git log 里看不出是谁、何场景 |
| 把 `inbox_*.jpg` 留在 `违规案例/` 不处理 | 会污染 fixture 列表;grep `违规案例/*.jpg` 时误以为是有意义的 fixture |
| inbox 文件 `git add` 进版本库 | 是临时素材,不该跟代码同寿命 |
| `category_<seq>.jpg` 不带 scene 字段 | 同 bucket 多场景时无法区分(如 medical 桶药店 vs 诊所) |
| 升档后保留 inbox 原文件 + 新 fixture 图 | 升档是「迁移」语义,不是「复制」 |

## 当前 `违规案例/` 状态(2026-08-25 内容查清后改名)

| 文件 | 档 | 状态 / 内容 |
|---|---|---|
| `medical_store_01.jpg` | Fixture | 入 git;sidecar `medical_store_01.md` 引用;medical 桶(药店糖尿病根治) |
| `inbox_wechat_20260819_dongjiao_daojia_recruit.jpg` | Inbox | 不入 git;东郊到家按摩 APP 招揽海报(全国技师 9 万 + 协会背书) |
| `inbox_wechat_20260819_xieduhui_xcx_quanguo_first.jpg` | Inbox ⭐ | 不入 git;蟹都汇小程序 VIP 页(大闸蟹**领导品牌** / **门店数量全国第一** / **累计销量全国第一**)— **正是 `docs/knowledge/ppocrv6_vs_v5_a_b_test.md` 里 v6 vs v5 A/B 经典案例**,升档价值最高 |
| `inbox_wechat_20260819_durex_bus_shouge_gongyi.jpg` | Inbox | 不入 git;杜蕾斯公交车身广告(**杜蕾斯首个公益装**)— outdoor + absolute 跨桶 |
| `inbox_wechat_20260819_xiaoyuan_yuliang_zytmhqs_douyin.jpg` | Inbox | 不入 git;小园玉粱紫玉米花青素抖音短视频(**控糖稳血糖** / **糖尿病患者的安心选择**)— food + internet_ad 跨桶 |

⭐ = 推荐优先升档(内容明确 + 项目曾用其做 OCR 评测基准 + 命中 13 桶 + 跨桶价值高)。

inbox 文件是 2026-08-19 暂停的 Task B(微信图采集)残留,2026-08-25 由 Claude Read tool 直接读图查清内容。**改名规范**:`inbox_<source>_<yyyymmdd>_<content_slug>.<ext>`,`<content_slug>` 用拼音 + 关键词短串(如 `durex_bus_shouge_gongyi` = 杜蕾斯 + 公交 + 首个 + 公益装)。若后续判定不构成 fixture 案例,直接 `rm` 即可(本就 untracked)。

## 与 text fixture 的对偶

text fixture 命名规则 (`docs/superpowers/specs/2026-08-25-text-violation-cases-design.md` §4):

```
text_<category>_<scene>_<seq>.md
```

image fixture 命名去 `text_` 前缀,与 text fixture 一一对应(同一 bucket 同 seq,共享 `.md` sidecar 时即升级成「图文并茂 fixture」)。未来若做混合 fixture(`text_medical_ykzp_01.md` + `medical_ykzp_01.jpg`),frontmatter 字段需扩展以引用图片。
