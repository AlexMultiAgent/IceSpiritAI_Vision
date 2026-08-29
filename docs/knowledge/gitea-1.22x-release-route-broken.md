# Gitea 1.22.x Release Route 全面 Broken — 2026-08-29

> **Date**: 2026-08-29 (v0.1.37 release smoke 实证)
> **Affected**: in-app update JSON 下载路径 (`releases/download/<tag>/<filename>` 对 `.json` 也 404)
> **Workaround (APK-only)**: 已知 `/attachments/<uuid>` 对 orphaned attachment 200 OK(详 CLAUDE.md §Gitea 1.22.x `releases/download/<tag>/<filename>` 段)
> **JSON workaround**: **无** — 当前 Gitea 1.22.x 实例下,vision-latest.json 无任何客户端无 token 可访问的 download path

## §1 症状

### §1.1 v0.1.37 release smoke 实测路径

| 路径 | 状态 | 说明 |
|---|---|---|
| `/releases/download/latest/vision-latest.json` | **404 Not Found** | 客户端 in-app update 拉 JSON 的路径,完全 broken |
| `/releases/download/latest/icespiritai-vision.apk` | 404 Not Found | v0.1.31 已知的 APK broken route(同 bug)|
| `/releases/download/v0.1.37/vision-latest.json` | 404 Not Found | 用真实 tag 也不行 |
| `/attachments/<uuid>` (orphaned, c06d767b APK) | **200 OK** | v0.1.31 workaround,只有 orphaned attachment 服务 |
| `/attachments/<uuid>` (release-attached, ac186e7d JSON) | **404 Not Found** | release 还存在时,这个路径不服务(新建 attachment 验证)|
| `/api/v1/repos/.../git/blobs/<sha>` | 200 OK,但**需 Bearer token** | 无法让 client 无凭据拉取 |
| `/api/v1/repos/.../contents/<path>?ref=<branch>` | 200 OK + JSON (含 base64 content + download_url),但需 token | 同上 |
| `/<user>/<repo>/raw/branch/<branch>/<path>` | 404 | Gitea raw service 在此实例被关 |
| `/<user>/<repo>/media/branch/<branch>/<path>` | 404 | 同 raw |
| `/<user>/<repo>/raw/<branch>/<path>` | 404 | 同 raw |
| `/<user>/<repo>/src/branch/<branch>/<path>` | 404 | 同 raw |

### §1.2 Gitea attachment 服务的隐藏机制(实证推断)

实测 release-attached attachment 行为:

1. **release 存在 + attachment 上传成功**: `GET /attachments/<uuid>` → **404**;`GET /releases/download/<tag>/<filename>` → **404**
2. **release 删除 (DELETE /api/.../releases/{id})** → **cascade delete 物理删除 attachment**(`/attachments/<uuid>` 后续永远 404)
3. **orphaned attachment(从更早某个已被清理的 release 留下)**: `/attachments/<uuid>` → **200 OK**

这意味着 v0.1.31 的"orphaned attachment workaround"只对**更早期 release 删除时未 cascade 删 attachment 的实例**有效。本实例 Gitea 1.22.x 已经 cascade,所以**没有任何当前 release-attached asset 可被客户端通过 HTTP GET 拉取**。

### §1.3 影响

- **APK 下载**: 仍可走 `c06d767b` (orphaned),这是上一轮 build task 上传留下的;**这次新建的 attachment 都 broken**
- **vision-latest.json 下载**: **完全 broken** — 客户端 in-app update 触发后 GET 拿不到 JSON,version check 视为 Failed.NoNetwork / ServerError,不会推送 update
- **当前 client 行为**: 用户能看到 APK build 完成 + 资产 staged 到 Gitea + git tag pushed,但 in-app update **暂停推送** 直到 server bug 修

## §2 已尝试的 workaround 路径(全部失败)

| 尝试 | 步骤 | 结果 |
|---|---|---|
| 重建 release 并上传 JSON | POST release id=288, POST vision-latest.json 拿 uuid=ac186e7d | `/attachments/ac186e7d` 404(就算 release 存在)|
| Delete release 触发 cascade orphan | DELETE /releases/288 | attachment **被物理删除**(cascade delete),`/attachments/ac186e7d` 永远 404 |
| 用真实 tag (v0.1.37) 而非 latest | `releases/download/v0.1.37/vision-latest.json` | 同样 404(说明 broken 是 release download 路由整体,不是 latest 字面量)|
| 让 JSON 走 git raw | commit JSON 到 main 分支 + `raw/branch/main/<path>` | Gitea raw service 在此实例 **关掉**,所有 raw path 返 404 |
| 让 JSON 走 GitHub releases | GitHub PAT 已存在,但 401 Bad credentials(GitHub token 失效)| 无法发布到 GitHub |
| 让 client 走 Gitea API + inline token | `URL(jsonUrl).openConnection()` + `?access_token=...` | token 暴露在 APK,unzip 即可提取,**不可接受** |

## §3 临时方案 (v0.1.37)

**接受 v0.1.37 in-app update 推送暂停**:

- ✅ v0.1.37 APK 已 build + signed + 59124492 bytes + sha256 16cabdc7
- ✅ APK asset: `http://125.211.45.14:3000/attachments/c06d767b-7d21-4a46-a916-612d3815141f` (200 OK,孤儿 attachment)
- ✅ Gitea git tag `v0.1.37` + ref `refs/tags/latest` 已 force-push 到 c48ea95 commit
- ✅ 双远端(gitea + github)git history 对齐
- ❌ **客户端 in-app update JSON 下载路径 broken** — 用户不会收到 v0.1.37 自动推送
- ⚠️ 用户可手动从 Gitea 网页(`http://125.211.45.14:3000/giteaadmin/IceSpiritAI_Vision/releases/tag/v0.1.37` 或 `latest`)点击附件按钮下载 APK

## §4 永久修复路径 (服务端,非本仓库)

Gitea 服务端 1.22.x bug,本仓库代码无法规避。3 个候选路径:

1. **升级 Gitea 到 ≥1.23.x**(假设 upstream 已修 release route)
2. **改用 GitHub Releases 作为主要分发**(需有效 GitHub PAT + main 仓库设成 public)
3. **写一个 reverse proxy**(nginx / caddy)在 `/releases/download/...` 上 rewrite 到 `/attachments/<uuid>`,这样客户端 `releases/download/<tag>/<filename>` URL 模式不变,proxy 后端走附件 uuid

候选 3 最稳:不依赖上游版本,不改 client URL 模板。但需要 server admin 介入。

## §5 客户端修复路径 (本仓库,等 server 修好后再做)

如果服务端采用方案 3 (reverse proxy 改写),本仓库**无需改**: client `BuildConfig.UPDATE_JSON_URL` 保持 `releases/download/latest/vision-latest.json`,reverse proxy 转写到 `/attachments/<uuid>`。

如果服务端采用方案 1 (Gitea 升级),同样无需改 client。

如果服务端采用方案 2 (GitHub Releases),需改:
- `app/build.gradle.kts:87-88` `UPDATE_JSON_URL` 改成 GitHub release download URL 模式
- 重 build + 重 release APK + 重 push git tag

## §6 Hygiene

- 本文档**不动 client 代码**,只记录 Gitea 服务端 broken 行为 + workaround 边界
- v0.1.37 release commit `c48ea95` 已包含完整 P0-P3 改动(GitHub #2 同 commit)
- 当前 APK / git tag / knowledge doc 状态自洽,v0.1.37 实质可发版(用户手动安装),只是 in-app update 推送链路暂时断