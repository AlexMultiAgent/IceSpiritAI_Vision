package com.aiglass.zhangwen.bluetooth

/**
 * AI 识图照片 BLE 直传事件（FA10 / 0x51）。
 */
data class AiPhotoBleEvent(
    val status: AiPhotoBleStatus,
    val fileSize: Int = 0,
    val fileName: String = "",
    val progress: Float = 0f,
    val imageData: ByteArray? = null,
    val errorMessage: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    enum class AiPhotoBleStatus {
        START,
        SUCCESS,
        FAILED,
        TRANSFER_PROGRESS,
        COMPLETE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiPhotoBleEvent) return false
        return status == other.status && fileSize == other.fileSize &&
            fileName == other.fileName && progress == other.progress &&
            errorMessage == other.errorMessage && imageData.contentEquals(other.imageData)
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + fileSize
        result = 31 * result + fileName.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        result = 31 * result + errorMessage.hashCode()
        return result
    }
}

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    /** 经典蓝牙是否已配对（BOND_BONDED） */
    val classicBonded: Boolean = false
)
