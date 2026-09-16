package com.aiglass.zhangwen.ui.identify

object IdentifyResultHolder {
    @Volatile
    var photoPath: String = ""

    @Volatile
    var resultText: String = ""

    fun set(path: String, text: String) {
        photoPath = path
        resultText = text
    }

    fun clear() {
        photoPath = ""
        resultText = ""
    }
}
