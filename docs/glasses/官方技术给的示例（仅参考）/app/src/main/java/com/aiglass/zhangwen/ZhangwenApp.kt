package com.aiglass.zhangwen

import android.app.Application
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth

class ZhangwenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GlassesBluetooth.init(this)
    }
}
