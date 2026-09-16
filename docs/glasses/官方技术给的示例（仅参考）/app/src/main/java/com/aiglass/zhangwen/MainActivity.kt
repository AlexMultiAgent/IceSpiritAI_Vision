package com.aiglass.zhangwen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth
import com.aiglass.zhangwen.ui.bind.BindDeviceScreen
import com.aiglass.zhangwen.ui.home.HomeScreen
import com.aiglass.zhangwen.ui.identify.IdentifyResultHolder
import com.aiglass.zhangwen.ui.identify.IdentifyResultScreen
import com.aiglass.zhangwen.ui.navigation.Routes
import com.aiglass.zhangwen.ui.settings.AliyunSettingsScreen
import com.aiglass.zhangwen.ui.settings.DeviceInfoSettingsScreen
import com.aiglass.zhangwen.ui.settings.PromptSettingsScreen
import com.aiglass.zhangwen.ui.settings.SettingsScreen
import com.aiglass.zhangwen.ui.splash.SplashScreen
import com.aiglass.zhangwen.ui.theme.ZhangwenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlassesBluetooth.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            ZhangwenTheme {
                GovAppNav(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun GovAppNav(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onBindClick = { navController.navigate(Routes.BIND) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onIdentifyResult = { path, text ->
                    IdentifyResultHolder.set(path, text)
                    navController.navigate(Routes.IDENTIFY_RESULT)
                }
            )
        }
        composable(Routes.BIND) {
            BindDeviceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrompt = { navController.navigate(Routes.SETTINGS_PROMPT) },
                onAliyun = { navController.navigate(Routes.SETTINGS_ALIYUN) },
                onDeviceInfo = { navController.navigate(Routes.SETTINGS_DEVICE_INFO) }
            )
        }
        composable(Routes.SETTINGS_DEVICE_INFO) {
            DeviceInfoSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PROMPT) {
            PromptSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ALIYUN) {
            AliyunSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IDENTIFY_RESULT) {
            IdentifyResultScreen(
                photoPath = IdentifyResultHolder.photoPath,
                resultText = IdentifyResultHolder.resultText,
                onBack = {
                    IdentifyResultHolder.clear()
                    navController.popBackStack()
                }
            )
        }
    }
}
