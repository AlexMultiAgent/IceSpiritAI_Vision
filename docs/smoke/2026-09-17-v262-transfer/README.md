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

- App 侧无需改动：本仓早已实现「FA12 零丢失收块 + FA11 op2 批量补洞」，
  V2.6.2 上补洞逻辑只是不再被触发（`resends=0`），旧的补洞代码仍作为兜底保留
  （现场还有 V2.4.6/V2.5.8 的机器）。
- 本文只记录传图耗时的对比；升级过程本身（缺 `0x3E` 导致 PAN 不建立）见
  `docs/knowledge/official-glasses-ota-protocol.md` §8。
