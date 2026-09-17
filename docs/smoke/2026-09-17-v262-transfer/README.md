# V2.6.2 传图复测：厂商把丢块问题修掉了（2026-09-17）

## 结论

同一部手机（nova 6）、同一副眼镜（Glasses-A88）、同一份 App（本地 vc82 构建），
**固件从 V2.4.6 升到 V2.6.2 后，FA12 突发丢块消失**：

| 指标 | V2.4.6 | V2.5.8 | **V2.6.2** |
|---|---|---|---|
| 突发到达率 | 13–29 %（43 KB 只到 7 440 B） | 13–29 % | **100 %** |
| 补洞（FA11 op2） | 14–15 s、5–8 轮批量补洞 | 5–15 s | **0 次** |
| 按钮 → 收齐（含 CRC 确认） | ~14–15 s | ~7.5 s | **~1.5 s** |
| 块数 / 大小 | 179 块 / 43 KB | 142 块 / 34 KB | 96–98 块 / 22.8–23.3 KB |

参考：`docs/glasses/固件问题反馈-2026-09-17.md` 里我们请厂商调的就是这个块间节奏。

## 四轮原始日志（15:18–15:19，`adb logcat -s GlassesCapture:*`）

```
15:18:41.923 Stage 1: SendingCommand — writing 0x33 to FFF1
15:18:43.189 0x51 START received: 12 bytes; raw=55aa075103050001a3590000   (fileSize=0x59a3=22947)
15:18:43.406 collectChunks complete: 22947/22947 bytes covered by 96 blocks in 214ms (resends=0)
15:18:43.449 FA11 write ok=true                                            (CRC 确认)
15:18:44.116 glasses capture complete — auto-speaking report (hits=0)      (识别+播报)

15:19:31.234 writing 0x33 → 15:19:32.573 fileSize=23303 → 15:19:32.779 23303/23303 B, 98 blocks, 206ms, resends=0
15:19:43.406 writing 0x33 → 15:19:44.676 fileSize=23331 → 15:19:44.891 23331/23331 B, 98 blocks, 215ms, resends=0
15:19:56.497 writing 0x33 → 15:19:57.785 fileSize=23331 → 15:19:58.080 23331/23331 B, 98 blocks, 294ms, resends=0
```

两次 `0x51 START` 之间的约 1.2–1.3 s 是眼镜端拍照时间（第一次不带 `fileSize`，第二次带），
不在传图耗时里。

## 说明

## 眼镜拍照键链路（用户实际使用路径，15:26）

按一下眼镜上的拍照键，App 通过 `0x11` TLV `0x17` 监测到事件后自动拍照、传图、识别并播报：

```
15:26:00.062  glasses shutter button: taking an AI photo over BLE   ← 收到按键事件
15:26:00.071  Stage 1: writing 0x33                                ← App 自动发起拍照
15:26:01.401  chunk #1 …                                           ← 眼镜端拍照 ~1.3 s
15:26:01.990  collectChunks complete: 51015/51015 bytes, 213 blocks, 593ms (resends=0)
15:26:02.027  FA11 write ok=true                                   ← CRC 确认
15:26:02.343  TtsAutoSpeak: auto-speaking report (hits=0)           ← 语音开始播报
```

| 阶段 | 耗时 |
|---|---|
| 按键 → 第一个块（眼镜拍照+准备） | ~1.34 s |
| 传图（51 KB / 213 块 / 0 补洞） | **0.59 s** |
| 收齐 → CRC 确认 | 0.04 s |
| CRC → 语音开始（含 OCR 识别） | 0.32 s |
| **按键 → 语音出声** | **≈ 2.3 s** |

同一副眼镜在 V2.4.6/V2.5.8 上，仅传图一项就要 7.5–15 s，所以这条链路现在的瓶颈已经
回到「眼镜端拍照的 ~1.3 s」和「OCR 识别」，不再是蓝牙传图。

- App 侧无需改动：本仓早已实现「FA12 零丢失收块 + FA11 op2 批量补洞」，
  V2.6.2 上补洞逻辑只是不再被触发（`resends=0`），旧的补洞代码仍作为兜底保留
  （现场还有 V2.4.6/V2.5.8 的机器）。
- 本文只记录传图耗时的对比；升级过程本身（缺 `0x3E` 导致 PAN 不建立）见
  `docs/knowledge/official-glasses-ota-protocol.md` §8。
