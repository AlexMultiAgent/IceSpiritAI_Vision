package com.aiglass.zhangwen.service

import android.content.Context
import android.util.Log
import com.aiglass.zhangwen.bluetooth.AiPhotoBleEvent
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth
import com.aiglass.zhangwen.config.AppSettingsStore
import com.aiglass.zhangwen.vision.VisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

sealed class PhotoCaptureState {
    data object Idle : PhotoCaptureState()
    data object SendingCommand : PhotoCaptureState()
    data object WaitingForPhoto : PhotoCaptureState()
    data object Recognizing : PhotoCaptureState()
    data class Success(
        val photoFile: File? = null,
        val aiResult: String = "",
        val sessionId: String = "",
        val message: String = ""
    ) : PhotoCaptureState()

    data class Error(val message: String) : PhotoCaptureState()
}

class PhotoCaptureService(private val context: Context) {
    private val tag = "AiGlassPhoto"
    private val bluetooth get() = GlassesBluetooth.get()
    private val vision = VisionRepository(context)

    private val _state = MutableStateFlow<PhotoCaptureState>(PhotoCaptureState.Idle)
    val captureState: StateFlow<PhotoCaptureState> = _state.asStateFlow()

    fun resetState() {
        _state.value = PhotoCaptureState.Idle
    }

    /**
     * 普通拍照：仅向眼镜下发拍照指令（0x30），不回传图片、不识图。
     */
    suspend fun captureOnly(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!bluetooth.isCommandChannelReady()) {
                _state.value = PhotoCaptureState.Error("指令通道未就绪，请先连接眼镜")
                return@withContext false
            }
            _state.value = PhotoCaptureState.SendingCommand
            val ok = bluetooth.takePhoto()
            if (!ok) {
                _state.value = PhotoCaptureState.Error("拍照指令发送失败")
                return@withContext false
            }
            delay(200)
            _state.value = PhotoCaptureState.Success(
                message = "已控制眼镜拍照（不识图、不回传）"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "普通拍照异常", e)
            _state.value = PhotoCaptureState.Error(e.message ?: "拍照异常")
            false
        }
    }

    /**
     * 拍照识物：仅 BLE FA10 传图后调用视觉模型。
     */
    suspend fun captureAndRecognize(): Boolean = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        try {
            val prompt = AppSettingsStore.identifyPromptFlow(context).first()

            _state.value = PhotoCaptureState.SendingCommand
            val photoFile = waitForBleAiPhotoReturn()
            if (photoFile == null) {
                if (_state.value !is PhotoCaptureState.Error) {
                    _state.value = PhotoCaptureState.Error("等待眼镜回传图片超时或失败")
                }
                return@withContext false
            }

            _state.value = PhotoCaptureState.Recognizing
            val result = vision.recognizeImage(photoFile, prompt)
            if (!result.success) {
                _state.value = PhotoCaptureState.Error(result.errorMessage ?: "识图失败")
                return@withContext false
            }
            _state.value = PhotoCaptureState.Success(
                photoFile = photoFile,
                aiResult = result.text.orEmpty(),
                sessionId = sessionId
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "识物流程异常", e)
            _state.value = PhotoCaptureState.Error(e.message ?: "识物异常")
            false
        }
    }

    private suspend fun waitForBleAiPhotoReturn(timeoutMs: Long = 30_000): File? {
        bluetooth.prewarmAiPhotoBlePriority()
        val channelOk = withContext(Dispatchers.IO) {
            bluetooth.ensureAiPhotoChannelReady(3_500L)
        }
        if (!channelOk) {
            _state.value = PhotoCaptureState.Error(
                "识图通道未就绪(FA12 未订阅)，请断开眼镜后重连再试"
            )
            return null
        }
        delay(80)
        _state.value = PhotoCaptureState.WaitingForPhoto
        if (!bluetooth.requestAiPhotoBleCapture(quality = 80)) {
            val err = bluetooth.aiPhotoBleTerminal.value
                ?.takeIf { it.status == AiPhotoBleEvent.AiPhotoBleStatus.FAILED }
                ?.errorMessage
            _state.value = PhotoCaptureState.Error(err ?: "无法发起 BLE 传图")
            return null
        }
        val event = withTimeoutOrNull(timeoutMs) {
            bluetooth.aiPhotoBleTerminal.filterNotNull().first {
                it.status == AiPhotoBleEvent.AiPhotoBleStatus.COMPLETE ||
                    it.status == AiPhotoBleEvent.AiPhotoBleStatus.FAILED
            }
        }
        if (event == null) {
            bluetooth.cancelAiPhotoBleTransfer("timeout")
            _state.value = PhotoCaptureState.Error("BLE 传图超时")
            return null
        }
        if (event.status == AiPhotoBleEvent.AiPhotoBleStatus.FAILED) {
            _state.value = PhotoCaptureState.Error(event.errorMessage.ifBlank { "BLE 传图失败" })
            return null
        }
        val bytes = event.imageData ?: run {
            _state.value = PhotoCaptureState.Error("未收到图片数据")
            return null
        }
        val dir = File(context.filesDir, "Picture").apply { mkdirs() }
        val file = File(dir, "ble_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        Log.i(tag, "BLE 收图完成: ${file.absolutePath} size=${bytes.size}")
        return file
    }
}
