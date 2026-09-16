package com.aiglass.zhangwen.bluetooth

data class BleCommandConfig(
    val request: Byte = 0x01,
    val response: Byte = 0x02,
    val notify: Byte = 0x03,
    val takePhotoCmd: Byte = 0x30,
    val bluetoothNetworkCmd: Byte = 0x3E,
    val aiPhotoBleCmd: Byte = 0x33,
    val photoReadyCmd: Byte = 0x51,
    val aiPhotoStatusCmd: Byte = 0x51,
    val getDeviceInfoCmd: Byte = 0x10,
    val deviceStatusNotifyCmd: Byte = 0x11,
    val toggleSubCmd: Byte = 0x01,
    /** 固件版本子指令（TLV_FIRMWARE_INFO） */
    val subFirmwareInfo: Byte = 0x20
) {
    companion object {
        fun default(): BleCommandConfig = BleCommandConfig()
    }
}
