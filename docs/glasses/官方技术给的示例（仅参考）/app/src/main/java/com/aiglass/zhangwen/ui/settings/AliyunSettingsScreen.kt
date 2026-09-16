package com.aiglass.zhangwen.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aiglass.zhangwen.config.AliyunVisionConfig
import com.aiglass.zhangwen.config.AliyunVisionConfigStore
import com.aiglass.zhangwen.ui.theme.GovBlue
import com.aiglass.zhangwen.ui.theme.GovTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliyunSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var timeoutSec by remember { mutableStateOf("60") }
    var maxTokens by remember { mutableStateOf("256") }
    var showKey by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    fun applyToUi(cfg: AliyunVisionConfig) {
        baseUrl = cfg.baseUrl
        apiKey = cfg.apiKey
        model = cfg.model
        timeoutSec = cfg.timeoutSec.toString()
        maxTokens = cfg.maxTokens.toString()
    }

    fun currentConfig(): AliyunVisionConfig = AliyunVisionConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim().lowercase(),
        timeoutSec = timeoutSec.toIntOrNull() ?: 60,
        maxTokens = maxTokens.toIntOrNull() ?: 256
    )

    LaunchedEffect(Unit) {
        applyToUi(AliyunVisionConfigStore.load(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阿里云视觉配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GovBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "配置阿里云百炼 OpenAI 兼容视觉接口。模型名请使用小写（如 qwen3-vl-flash）。密钥仅保存在本机。",
                color = GovTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(AliyunVisionConfigStore.BAILIAN_CONSOLE_URL)
                            )
                        )
                    }
                }
            ) {
                Text("打开百炼控制台（北京）", color = GovBlue)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址 baseUrl") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    OutlinedButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏" else "显示")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.lowercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名（小写）") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = timeoutSec,
                onValueChange = { timeoutSec = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("超时（秒）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("max_tokens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        "限制视觉模型单次回复可生成的最大 token 数（约等于输出长度上限）。" +
                            "数值越大，回答可以更长，但耗时与费用通常更高；识物场景一般 128～512 即可。"
                    )
                }
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val cfg = currentConfig()
                        val ok = withContext(Dispatchers.IO) {
                            AliyunVisionConfigStore.save(context, cfg)
                        }
                        applyToUi(AliyunVisionConfigStore.load(context))
                        hint = if (ok && cfg.isReady()) {
                            "已保存，识物将使用当前配置"
                        } else if (ok) {
                            "已保存，但地址 / Key / 模型仍有空项"
                        } else {
                            "保存失败，请重试"
                        }
                    }
                }
            ) { Text("保存") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val example = AliyunVisionConfigStore.exampleBailianConfig()
                        applyToUi(example)
                        val ok = withContext(Dispatchers.IO) {
                            AliyunVisionConfigStore.save(context, example)
                        }
                        applyToUi(AliyunVisionConfigStore.load(context))
                        hint = if (ok) {
                            "已填入并保存百炼示例（qwen3-vl-flash），可直接识物"
                        } else {
                            "已填入界面，但保存失败，请再点「保存」"
                        }
                    }
                }
            ) { Text("填入百炼视觉示例配置") }
            hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = GovBlue)
            }
        }
    }
}
