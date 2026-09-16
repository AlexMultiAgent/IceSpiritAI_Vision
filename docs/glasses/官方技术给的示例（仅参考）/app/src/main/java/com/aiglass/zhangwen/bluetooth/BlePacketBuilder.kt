package com.aiglass.zhangwen.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlePacketBuilder(private val c: BleCommandConfig = BleCommandConfig.default()) {
    val cfg: BleCommandConfig get() = c
    private val header: ByteArray = byteArrayOf(0x55, 0xAA.toByte())

    fun buildPacket(seq: Byte, cmd: Byte, type: Byte, payload: ByteArray): ByteArray {
        val packet = ByteBuffer.allocate(7 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        packet.put(header)
        packet.put(seq)
        packet.put(cmd)
        packet.put(type)
        packet.putShort(payload.size.toShort())
        packet.put(payload)
        return packet.array()
    }

    fun buildAiPhotoRequestPacket(seq: Byte, quality: Int = 80): ByteArray {
        val q = quality.coerceIn(0, 100).toByte()
        return buildPacket(seq, c.aiPhotoBleCmd, c.request, byteArrayOf(q))
    }

    fun buildTakePhotoPacket(seq: Byte): ByteArray {
        return buildPacket(seq, c.takePhotoCmd, c.request, byteArrayOf())
    }

    fun buildSubCommandPacket(seq: Byte, subCmd: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val subPayload = byteArrayOf(subCmd, payload.size.toByte()) + payload
        return buildPacket(seq, c.getDeviceInfoCmd, c.request, subPayload)
    }

    fun buildFirmwareVersionRequestPacket(seq: Byte): ByteArray {
        return buildSubCommandPacket(seq, c.subFirmwareInfo)
    }
}
