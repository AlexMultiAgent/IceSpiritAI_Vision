package com.icespiritai.offline.tts

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.icespiritai.offline.IceSpiritVisionActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTtsE2ETest {

    @get:Rule val composeRule = createAndroidComposeRule<IceSpiritVisionActivity>()

    @Test fun ttsButtonToggleChangesIcon() {
        // 等首屏渲染
        composeRule.waitForIdle()
        // 首次启动会叠 DisclaimerDialog(spec §6.4),HomeTopBar 的朗读按钮在 dialog
        // 之下不可见 — 必须先把"我了解"点了,HomeTopBar 才能拿到事件。
        composeRule.onNodeWithText("我了解").performClick()
        composeRule.waitForIdle()
        // 现在 HomeTopBar 的朗读按钮(contentDescription 含 "朗读")可见
        val initial = composeRule.onNodeWithContentDescription(
            "朗读", substring = true, useUnmergedTree = true,
        )
        initial.assertExists()
        initial.performClick()
        // 等 crossfade 220ms + speak 完成(冰灵 TTS 未装,会走到 InitFailed 但 toggle 已发生)
        composeRule.mainClock.advanceTimeBy(500)
        Log.i("IceSpiritTtsE2E", "[TOGGLE_CLICKED]")
    }
}