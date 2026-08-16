# 启动图标生成方式(冰灵锐目)

> 记录如何从冰灵吉祥物原图生成 launcher icon,以及调整构图的参数换算。
> 生成脚本:`tools/generate_launcher_icon.py`(Python 3 + numpy + Pillow)。

## 1. 来源素材

| 项 | 值 |
| --- | --- |
| 文件 | `D:\GitHub\冰灵图标\冰灵（男）.png` |
| 尺寸 | 1280 × 2074(竖版) |
| 背景 | 近白(#FEFEFE / #F4F4F4,不透明) |
| 构图 | 主体居中,头部在画面顶端(y=0),脚部在底端(y≈2073) |

素材**不直接**放进 `res/`,统一由脚本处理后输出,保证自适应图标/回退图标各密度一致。

## 2. 生成流程

脚本按以下步骤处理:

1. **读图**:原图转 RGB。
2. **去底**:从图像四边做"近白泛洪填充"(每通道容差默认 28)。只有与边框连通的近白区域才变透明,人物内部的浅色区域保留。
3. **裁切**:顶部对齐 `y=0`(头部位置),向下裁到指定像素高度。当前下沿 = **1550px**。
4. **前景层**:裁切结果**等比缩放**进 432×432 画布的 66dp 安全区(432 × 66/108 = 264px),居中,透明背景。
5. **回退图标**:前景层合成到背景色 `#FDFDFD` 上,输出 5 档密度。

## 3. 当前参数

| 项 | 值 |
| --- | --- |
| 裁切顶部 | `y=0`(保持不变,头部在顶端) |
| 裁切下沿 | `1550px` |
| 脚本参数 | `--crop-fraction 0.7474`(= 1550 / 2074) |
| 去底容差 | `--tolerance 28` |
| 背景色 | `#FDFDFD`(`@color/launcher_icon_bg`) |
| 安全区 | 66dp(432px 画布内 264px) |

**像素 ↔ fraction 换算**:`fraction = 下沿像素 / 2074`。

常见取值:

| 下沿(px) | --crop-fraction |
| --- | --- |
| 1280(初始方形) | 0.617 |
| 1493(1280 × 7/6) | 0.72 |
| **1550(当前)** | **0.7474** |
| 1626 | 0.784 |

## 4. 输出与接线

脚本输出(已提交进仓库):

| 文件 | 作用 |
| --- | --- |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | 432×432 RGBA 自适应前景 |
| `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | 48/72/96/144/192 回退图标 |
| 同目录 `ic_launcher_round.png` | 圆形回退图标 |

资源接线(已提交):

- `app/src/main/res/values/colors.xml`:`launcher_icon_bg = #FDFDFD`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`:
  background + foreground + monochrome(均用前景层)
- `app/src/main/AndroidManifest.xml`:`android:icon="@mipmap/ic_launcher"`、
  `android:roundIcon="@mipmap/ic_launcher_round"`

minSdk=26,因此 Android 8+ 全部走自适应图标;各密度 PNG 是 API<26 与部分预览工具的回退。

## 5. 重新生成 / 调整

```powershell
# 改下沿像素时先换算 fraction(= 下沿 / 2074)
python tools/generate_launcher_icon.py "D:\GitHub\冰灵图标\冰灵（男）.png" --crop-fraction 0.7474

# 打包(图标是资源,与 modelProfile 无关,任一 profile 都会带上)
.\gradlew.bat :app:assembleDebug -PmodelProfile=ice_ocr_rules

# 真机覆盖安装
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

换女性素材时把输入路径换成 `冰灵（女）.png`,按同样方法重新取构图参数。

## 6. 注意事项

- **顶部不能向下移**:吉祥物头部就在原图 y=0,裁切一律从 0 开始;调整构图只改下沿。
- 前景层必须是透明的;人物边缘若残留浅色描边,合成在 `#FDFDFD` 上几乎不可见。
- 改完图标直接 `assembleDebug` 即可,无需清缓存;图标内容不依赖 `modelProfile`。
