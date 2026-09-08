# v0.1.X+4 TTS playback smoke — 待 operator 真机烟测填写

> Phase 4 of TTS playback feature (spec [`docs/superpowers/specs/2026-09-08-vision-tts-playback-design.md`](../superpowers/specs/2026-09-08-vision-tts-playback-design.md), §15)。
>
> 本 smoke doc 由 plan Task 17 创建,实际真机烟测 + 截图 + e2e 验证待 operator 跑完填写。

## 1. Validation target

冰灵锐目 vision APK 在 v0.1.X+4 路径下,真机端到端验证:
- HomeTopBar 朗读按钮 4 态视觉矩阵(Idle / Speaking / InitFailed / Disabled)
- TTS 朗读命中要点(audit71 fixture 67 蟹都汇)
- 0 命中朗读 fallback(spec §3.4 C)
- 冰灵 TTS 兜底引擎 APK 完整下载 + 安装流程(走 Gitea)
- 断点续传(杀进程后 resume)
- 首次启动免责声明对话框(主动接受)

## 2. Validation config

待 operator 填写真机型号 / versionCode / commit SHA / 规则库版本

## 3. Unit test(本 phase 增量)

待 operator 跑 `./gradlew.bat testDebugUnitTest -PmodelProfile=ice_ocr_rules` 后填:

| Test class | 新增测试 |
|---|---|
| ... | ... |

## 4. 真机 e2e 验证结果

待 operator 跑 Task 16 命令后填 logcat 摘要。

## 5. A/B 视觉对比(3 张 fixture)

待 visual-audit scaffold 截图后填。

## 6. 验收 checklist(对照 spec §15)

- [ ] HomeTopBar 朗读按钮 4 态视觉矩阵正确
- [ ] 朗读中文命中(HiVoice / Google TTS / 冰灵 TTS)
- [ ] 0 命中朗读 fallback 文案
- [ ] 冰灵 TTS 兜底 APK 下载 + 安装流程
- [ ] 断点续传
- [ ] 首次启动免责声明对话框

## 7. Plan ↔ reality drift(供 v0.1.X+5+ PR 范围参考)

待填

## 8. Phase 4 commit 累计清单

待 `git log --oneline` 后填

## 9. Phase 4 范围外 / 留给 icevision-release skill

- versionCode bump
- user-changelog.md 顶部新条目
- git tag v0.1.X+4 + push `latest` ref
- 4 步流水线 + Triple-SHA 对齐

— 由 `icevision-release` skill 触发时负责。
