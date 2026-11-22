package com.superxiao.budgettracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superxiao.budgettracker.data.BudgetDatabase
import com.superxiao.budgettracker.data.BudgetRepository
import com.superxiao.budgettracker.notification.NotificationHelper
import com.superxiao.budgettracker.security.BiometricLockManager
import com.superxiao.budgettracker.ui.AboutMeScreen
import com.superxiao.budgettracker.ui.AppIconManager
import com.superxiao.budgettracker.ui.BudgetViewModel
import com.superxiao.budgettracker.ui.BudgetViewModelFactory
import com.superxiao.budgettracker.ui.HistoryScreen
import com.superxiao.budgettracker.ui.HomeScreen
import com.superxiao.budgettracker.ui.SettingsScreen
import com.superxiao.budgettracker.ui.theme.BudgetTrackerTheme

class MainActivity : FragmentActivity() {

    companion object {
        private var sessionUnlocked = false
    }

    private var isUnlocked by mutableStateOf(true)
    private var authError by mutableStateOf<String?>(null)
    private var resumeAuthTick by mutableStateOf(0)
    private var isAuthenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val lockEnabled = BiometricLockManager.isLockEnabled(this)
        // 若是应用内切换深浅色模式或旋转屏幕导致的配置重建，保持已解锁状态；冷启动或切后台返回则重新锁定
        isUnlocked = !lockEnabled || (savedInstanceState != null && sessionUnlocked)
        if (isUnlocked) {
            sessionUnlocked = true
        }

        val database = BudgetDatabase.getInstance(applicationContext)
        val repository = BudgetRepository(database)
        val factory = BudgetViewModelFactory(repository)

        setContent {
            BudgetTrackerTheme {
                val isDark = isSystemInDarkTheme()
                val navController = rememberNavController()
                val budgetViewModel: BudgetViewModel = viewModel(factory = factory)

                val onboardingPrefs = remember {
                    getSharedPreferences("budget_onboarding_prefs", Context.MODE_PRIVATE)
                }
                var showFirstLaunchPermDialog by remember {
                    val alreadyPrompted = onboardingPrefs.getBoolean("first_launch_perm_prompted", false)
                    mutableStateOf(!alreadyPrompted && !NotificationHelper.hasNotificationPermission(this))
                }
                val firstLaunchPermLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    onboardingPrefs.edit().putBoolean("first_launch_perm_prompted", true).apply()
                    showFirstLaunchPermDialog = false
                }

                val triggerAuth = {
                    authError = null
                    if (!BiometricLockManager.canAuthenticate(this)) {
                        sessionUnlocked = true
                        isUnlocked = true
                    } else if (!isAuthenticating) {
                        isAuthenticating = true
                        BiometricLockManager.authenticate(
                            activity = this,
                            onSuccess = {
                                isAuthenticating = false
                                sessionUnlocked = true
                                isUnlocked = true
                            },
                            onError = { msg ->
                                isAuthenticating = false
                                authError = msg
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (!isDark) {
                        Image(
                            painter = painterResource(id = R.drawable.home_bg_light),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            alpha = 0.16f
                        )
                    }

                    // 保持 NavHost 常驻，锁定/解锁不销毁页面栈
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = budgetViewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToHistory = { navController.navigate("history") }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                viewModel = budgetViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = budgetViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToAbout = { navController.navigate("about") }
                            )
                        }
                        composable("about") {
                            AboutMeScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // 首次进入引导授予通知权限（拒绝或取消也绝不闪退）
                    if (isUnlocked && showFirstLaunchPermDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                onboardingPrefs.edit().putBoolean("first_launch_perm_prompted", true).apply()
                                showFirstLaunchPermDialog = false
                            },
                            title = { Text("欢迎使用超级魈的记账工具") },
                            text = {
                                Text("为了支持「定时记账提醒」与「通知栏原生快捷回复记账」，建议授予通知权限。本应用纯本地运行，不会联网上传任何数据。")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onboardingPrefs.edit().putBoolean("first_launch_perm_prompted", true).apply()
                                        showFirstLaunchPermDialog = false
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            try {
                                                firstLaunchPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } catch (_: Exception) {
                                                NotificationHelper.openNotificationSettings(this@MainActivity)
                                            }
                                        } else {
                                            NotificationHelper.openNotificationSettings(this@MainActivity)
                                        }
                                    }
                                ) {
                                    Text("立即授权")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        onboardingPrefs.edit().putBoolean("first_launch_perm_prompted", true).apply()
                                        showFirstLaunchPermDialog = false
                                    }
                                ) {
                                    Text("暂不开启")
                                }
                            }
                        )
                    }

                    // 全屏不透明生物识别锁遮罩层
                    if (!isUnlocked) {
                        LaunchedEffect(resumeAuthTick) {
                            if (resumeAuthTick > 0) {
                                triggerAuth()
                            }
                        }
                        BiometricLockOverlay(
                            isDark = isDark,
                            iconRes = remember { AppIconManager.getCurrentIcon(this@MainActivity).previewRes },
                            errorMessage = authError,
                            onUnlockClick = triggerAuth
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isUnlocked && !isAuthenticating && BiometricLockManager.isLockEnabled(this)) {
            resumeAuthTick++
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // 退回桌面/后台时安全应用已选择的桌面图标，避免前台被系统杀进程
            AppIconManager.applyPendingIconIfNeeded(applicationContext)
            // 切到后台或退出 APP 时立即上锁（排除深浅色切换/横竖屏旋转及系统生物识别弹窗本身）
            if (!isAuthenticating && BiometricLockManager.isLockEnabled(this)) {
                sessionUnlocked = false
                isUnlocked = false
                authError = null
            }
        }
    }
}

@Composable
private fun BiometricLockOverlay(
    isDark: Boolean,
    iconRes: Int,
    errorMessage: String?,
    onUnlockClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = interactionSource, indication = null) { }
    ) {
        if (!isDark) {
            Image(
                painter = painterResource(id = R.drawable.home_bg_light),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = 0.16f
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = "应用图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "超级魈的记账工具",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已开启生物识别保护，请验证身份",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("指纹 / 人脸解锁")
            }
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
