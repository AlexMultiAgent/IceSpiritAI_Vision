package com.icespiritai.offline.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportedChineseEnginesTest {

    @Test fun `filters out engines that don't support zh-CN`() {
        val raw = listOf(
            EngineInfo("com.huawei.hivoice", "荣耀 AI 语音引擎", supportsChinese = true),
            EngineInfo("com.google.android.tts", "Google TTS", supportsChinese = false),
            EngineInfo("com.samsung.tts.engine", "Samsung TTS", supportsChinese = true),
        )
        val result = SupportedChineseEngines.filter(raw)
        assertEquals(2, result.size)
        assertEquals(setOf("com.huawei.hivoice", "com.samsung.tts.engine"), result.map { it.packageName }.toSet())
    }

    @Test fun `empty input returns empty list`() {
        assertEquals(emptyList<EngineInfo>(), SupportedChineseEngines.filter(emptyList()))
    }

    @Test fun `all engines supporting zh-CN passes through unchanged`() {
        val raw = listOf(
            EngineInfo("a", "A", supportsChinese = true),
            EngineInfo("b", "B", supportsChinese = true),
        )
        assertEquals(raw, SupportedChineseEngines.filter(raw))
    }
}