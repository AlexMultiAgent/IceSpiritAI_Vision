# Gitea 1.22.x Release Route Broken — 2026-08-29 (v0.1.37)

> **Date**: 2026-08-29 (v0.1.37 release smoke 实证)
> **Affected repo**: `giteaadmin/IceSpiritAI_Vision`(代码仓库) — `/releases/download/<tag>/<filename>` 对 .apk + .json 都 404
> **Client 发布目标(repo)**: `giteaadmin/vision-app` — **完全健康**,200 OK,client in-app update 链路 **正常工作**
> **结论**: client in-app update 链路不受影响,这次误诊的根本原因是把代码仓库当成发布仓库了
> **状态**: v0.1.37 release **完全 ready 推送**

## §1 真相:两个 Gitea repo,功能分离

| Repo | 用途 | URL | 状态 |
|---|---|---|---|
| `giteaadmin/IceSpiritAI_Vision` | **代码仓库**(git remote gitea) | `http://125.211.45.14:3000/giteaadmin/IceSpiritAI_Vision` | `releases/download/<tag>/<filename>` 路由 404 |
| `giteaadmin/vision-app` | **发布仓库**(client in-app update 终点,build.gradle.kts `giteaRepo` hardcoded) | `http://125.211.45.14:3000/giteaadmin/vision-app` | 完全健康,release 187 + 3 assets 全 200 |

`app/build.gradle.kts:459-460`:
```kotlin
val giteaBaseUrl = "http://125.211.45.14:3000"
val giteaRepo = "giteaadmin/vision-app"   // ← 发布仓库,不是 IceSpiritAI_Vision
```

`app/build.gradle.kts:87-88` (BuildConfig,client hardcoded):
```kotlin
buildConfigField("String", "UPDATE_JSON_URL",
    "\"http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json\"")
```

**所以 client in-app update 走的是 vision-app**,不走 IceSpiritAI_Vision。IceSpiritAI_Vision broken 不影响客户端。

## §2 v0.1.37 release 端到端 smoke (2026-08-29)

### §2.1 客户端 UPDATE_JSON_URL — 200 OK ✓

```bash
$ curl -s http://125.211.45.14:3000/giteaadmin/vision-app/releases/download/latest/vision-latest.json
{
  "versionCode": 37,
  "versionName": "0.1.37",
  "apkUrl": "http://125.211.45.14:3000/attachments/c06d767b-7d21-4a46-a916-612d3815141f",
  "apkSize": 59124492,
  "apkSha256": "16cabdc7de69e8b3...",
  "signerCertSha256": "4a21f417782d561dccd31ff0a10e4d64...",
  ...
}
```

### §2.2 APK 下载 — 200 OK,Content-Length 匹配 ✓

```bash
$ curl -sI http://125.211.45.14:3000/attachments/c06d767b-7d21-4a46-a916-612d3815141f
HTTP/1.1 200 OK
Content-Length: 59124492
Content-Disposition: inline; filename="icespiritai-vision.apk"
```

local APK size = 59124492 bytes,remote Content-Length = 59124492 bytes,**完全一致**。

### §2.3 Gitea release 187 assets 列表(vision-app repo)

| Asset | Size | UUID | Browser URL | 状态 |
|---|---:|---|---|---|
| `icespiritai-vision.apk` | 59124492 | c06d767b-... | `releases/download/latest/icespiritai-vision.apk` (或 `/attachments/<uuid>`) | 200 OK ✓ |
| `vision-latest.json` | 4086 | e7d69bec-... | `releases/download/latest/vision-latest.json` | 200 OK ✓ |
| `icespiritai-vision-update.apk` | 16919936 | 23a672c7-... | (旧版本残留) | (历史) |

## §3 之前误诊的原因

之前的诊断(commit `8920b4a` 包含的 `docs/knowledge/gitea-1.22x-release-route-broken.md` 初版)错误地把代码仓库 `IceSpiritAI_Vision` 的 broken 状态当作发布仓库的 broken:

- curl `GET /api/v1/repos/giteaadmin/IceSpiritAI_Vision/releases/187` → 404(因为 vision-app repo 的 release id=187 不在 IceSpiritAI_Vision repo)
- curl `GET /releases/download/latest/vision-latest.json` 用 IceSpiritAI_Vision repo path → 404
- DELETE release 288(在 IceSpiritAI_Vision repo 创建的临时 release) → cascade delete attachment

**正确做法**:发布仓库是 vision-app,所有 client-facing URL 都用 `/giteaadmin/vision-app/...` 路径。

## §4 修复

### §4.1 代码仓库 broken(可忽略)

IceSpiritAI_Vision repo release download 路由 broken 是 Gitea server bug,**不影响 client in-app update**。不修。

如果未来需要修复 IceSpiritAI_Vision repo 路由(比如做代码 release 用),候选路径:
- nginx reverse proxy rewrite(详 §5)
- 或等服务端 Gitea 升级

### §4.2 已修的诊断错误

- `docs/knowledge/gitea-1.22x-release-route-broken.md`(本文) — 改写,说明 vision-app repo 健康
- `CLAUDE.md §发布流水线踩坑` — caveat 段重写,说明 broken repo 是 IceSpiritAI_Vision(代码),不影响 client(vision-app)
- `memory/followup-gitea-1.22x-route-broken.md` — 同步更新

## §5 nginx reverse proxy 候选(给未来)

如果想修复 IceSpiritAI_Vision repo(代码仓库)的 release route(可选,例如未来需要从代码仓库直接 serve release):

```nginx
# /etc/nginx/conf.d/gitea-icevision-reverse-proxy.conf
map $request_uri $attachment_uuid {
    default "";
    include /etc/nginx/icevision-release-map.conf;
}

server {
    listen 80;
    server_name 125.211.45.14;

    location ~ ^/giteaadmin/IceSpiritAI_Vision/releases/download/(?<tag>[^/]+)/(?<filename>[^/]+)$ {
        set $uuid $attachment_uuid;
        if ($uuid = "") {
            return 502 "release map not configured for $filename";
        }
        proxy_pass http://125.211.45.14:3000/attachments/$uuid;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        proxy_pass http://125.211.45.14:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

**当前不需要部署此 proxy**,因为 client in-app update 完全工作(vision-app repo 健康)。

## §6 Hygiene

- v0.1.37 release **完全 ready 推送**
- in-app update 链路完全工作
- 误诊修正文档已 commit(后续 commit)
- 无 nginx 反代部署需求
- 双远端(gitea + github)git history 对齐,v0.1.37 tag 在 vision-app repo release assets 上对应 c48ea95 commit 的 APK + JSON