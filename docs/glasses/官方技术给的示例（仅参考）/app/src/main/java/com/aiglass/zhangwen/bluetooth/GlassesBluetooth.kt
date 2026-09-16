package com.aiglass.zhangwen.bluetooth

import android.content.Context

object GlassesBluetooth {
    @Volatile
    private var controller: BluetoothController? = null

    fun init(context: Context) {
        if (controller == null) {
            synchronized(this) {
                if (controller == null) {
                    controller = BluetoothController(context.applicationContext)
                }
            }
        }
    }

    fun get(): BluetoothController {
        return controller
            ?: error("GlassesBluetooth 未初始化，请在 Application.onCreate 中调用 init")
    }
}
