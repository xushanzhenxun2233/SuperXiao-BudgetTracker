package com.superxiao.budgettracker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superxiao.budgettracker.data.MoneyUtils
import com.superxiao.budgettracker.data.QuickButtonEntity
import com.superxiao.budgettracker.notification.NotificationHelper
import com.superxiao.budgettracker.notification.ReminderPrefs
import com.superxiao.budgettracker.notification.ReminderScheduler
import com.superxiao.budgettracker.notification.ReminderSlot
import com.superxiao.budgettracker.security.BiometricLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BudgetViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var budgetInput by remember(state.totalBudgetCents) {
        mutableStateOf(MoneyUtils.formatCentsForInput(state.totalBudgetCents))
    }
    var budgetSavedBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(budgetSavedBanner) {
        if (budgetSavedBanner != null) {
            delay(3_500L)
            budgetSavedBanner = null
        }
    }

    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    var reminderSlots by remember { mutableStateOf(ReminderPrefs.loadSlots(context)) }
    var editingReminderSlot by remember { mutableStateOf<ReminderSlot?>(null) }
    var hasNotificationPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var canUseBiometric by remember { mutableStateOf(BiometricLockManager.canAuthenticate(context)) }
    var selectedIcon by remember { mutableStateOf(AppIconManager.getCurrentIcon(context)) }
    var biometricLockEnabled by remember { mutableStateOf(BiometricLockManager.isLockEnabled(context)) }

    var showAddButtonDialog by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<QuickButtonEntity?>(null) }
    var showNewPeriodDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从系统设置页或权限弹窗返回时，实时刷新权限与生物识别可用状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
                canUseBiometric = BiometricLockManager.canAuthenticate(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppNotificationSettings() {
        val opened = NotificationHelper.openNotificationSettings(context)
        if (!opened) {
            scope.launch {
                snackbarHostState.showSnackbar("请在手机系统设置中手动开启本应用的通知权限")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted && NotificationHelper.hasNotificationPermission(context)
        if (!hasNotificationPermission) {
            scope.launch {
                snackbarHostState.showSnackbar("未授予通知权限，定时提醒功能保持禁用，可点「去设置」开启")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("已获得通知权限，定时记账提醒功能已启用")
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportBackupJson(ReminderPrefs.loadSlots(context))
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("无法写入目标文件")
                    }
                    snackbarHostState.showSnackbar("备份已成功导出")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导出失败：${e.message}")
                }
            }
        }
    }

    fun shareBackupFile() {
        scope.launch {
            try {
                val json = viewModel.exportBackupJson(ReminderPrefs.loadSlots(context))
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "backups").apply { mkdirs() }
                    val target = File(dir, "superxiao-budget-backup-${LocalDate.now()}.json")
                    target.writeText(json, Charsets.UTF_8)
                    target
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "超级魈的记账工具备份")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "分享备份文件到"))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("分享失败：${e.message}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: error("无法读取该文件")
                    }
                    pendingImportJson = jsonText
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("读取备份文件失败：${e.message}")
                }
            }
        }
    }

    fun requestNotificationPermissionOrOpenSettings() {
        // 直接跳转到本应用的系统通知/应用详情页，避免被系统重复拒绝策略拦截导致无反应
        openAppNotificationSettings()
    }

    fun updateSlot(updated: ReminderSlot) {
        ReminderPrefs.saveSlot(context, updated)
        ReminderScheduler.scheduleSlot(context, updated)
        reminderSlots = ReminderPrefs.loadSlots(context)
    }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("预算设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 当前周期总预算
            item {
                val saveBudgetAction = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    val parsedCents = MoneyUtils.parseYuanToCents(budgetInput, allowZero = true)
                    viewModel.updateTotalBudgetFromText(budgetInput)
                    if (parsedCents != null) {
                        budgetSavedBanner = "已保存！当前周期总预算已更新为 ¥${MoneyUtils.formatCents(parsedCents)}"
                    }
                }
                SettingsCard(title = "当前周期总预算") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("总预算金额 (元，最多两位小数)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { saveBudgetAction() }),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(onClick = saveBudgetAction) {
                                Text("保存")
                            }
                        }
                        AnimatedVisibility(visible = budgetSavedBanner != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = budgetSavedBanner.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. 起止日期设置 + 新建周期
            item {
                SettingsCard(title = "当前预算周期（剩余 ${state.remainingDays} 天）") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickingStartDate = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("起始日期", style = MaterialTheme.typography.labelSmall)
                                    Text(state.startDate.format(dateFormatter), fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { pickingEndDate = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("结束日期", style = MaterialTheme.typography.labelSmall)
                                    Text(state.endDate.format(dateFormatter), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showNewPeriodDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("新建预算周期（归档当前周期至历史）")
                        }
                    }
                }
            }

            // 3. 定时记账提醒
            item {
                SettingsCard(title = "定时记账提醒（支持通知栏快捷回复）") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasNotificationPermission) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "未授予通知权限，以下功能已禁用",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { requestNotificationPermissionOrOpenSettings() }) {
                                        Text("去开启")
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "收到提醒后，可直接下拉通知点击「快捷记账」回复金额与备注（如：25 午餐）自动扣款。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (hasNotificationPermission) 1f else 0.45f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reminderSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = slot.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        TextButton(
                                            enabled = hasNotificationPermission,
                                            onClick = { editingReminderSlot = slot },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = "每天 ${slot.formattedTime()}（点击修改）",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Switch(
                                        enabled = hasNotificationPermission,
                                        checked = hasNotificationPermission && slot.enabled,
                                        onCheckedChange = { checked ->
                                            if (hasNotificationPermission) {
                                                updateSlot(slot.copy(enabled = checked))
                                                if (checked) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("已开启${slot.label}（${slot.formattedTime()}）")
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                enabled = hasNotificationPermission,
                                onClick = {
                                    val sent = NotificationHelper.showReminderNotification(
                                        context = context,
                                        notificationId = 3099,
                                        slotLabel = "测试提醒"
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (sent) {
                                                "测试通知已发送，下拉通知栏即可体验快捷回复记账"
                                            } else {
                                                "通知未开启，请先在系统设置中允许本应用通知"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("发送测试通知（体验快捷回复）")
                            }
                        }
                    }
                }
            }

            // 4. 桌面图标切换
            item {
                SettingsCard(title = "桌面图标切换") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "点选即可切换应用桌面图标（默认神里绫华），返回桌面后自动生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(AppIconOption.entries) { option ->
                                val isSelected = option == selectedIcon
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = !isSelected) {
                                            AppIconManager.selectIcon(context, option)
                                            selectedIcon = option
                                            scope.launch {
                                                snackbarHostState.showSnackbar("已设为「${option.displayName}」，返回桌面后自动生效")
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = option.previewRes),
                                        contentDescription = option.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 3.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isSelected) "✓ ${option.displayName}" else option.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. 生物识别解锁
            item {
                SettingsCard(title = "隐私保护（指纹 / 人脸解锁）") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (canUseBiometric) 1f else 0.45f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "进入主界面前验证身份",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (canUseBiometric) {
                                    "支持指纹、人脸识别及系统锁屏密码兜底"
                                } else {
                                    "当前设备未设置指纹、人脸或锁屏密码，该功能已禁用"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            enabled = canUseBiometric,
                            checked = canUseBiometric && biometricLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val activity = context as? FragmentActivity
                                    if (activity == null || !canUseBiometric) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("当前设备未设置指纹、人脸或锁屏密码，请先在系统设置中开启")
                                        }
                                    } else {
                                        BiometricLockManager.authenticate(
                                            activity = activity,
                                            title = "开启生物识别锁",
                                            subtitle = "验证通过后即可启用进入验证",
                                            onSuccess = {
                                                BiometricLockManager.setLockEnabled(context, true)
                                                biometricLockEnabled = true
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("已开启生物识别解锁")
                                                }
                                            },
                                            onError = { err ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("未开启：$err")
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    BiometricLockManager.setLockEnabled(context, false)
                                    biometricLockEnabled = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已关闭生物识别解锁")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 6. 本地数据备份（导出 / 导入 / 分享 JSON）
            item {
                SettingsCard(title = "本地数据备份（换机不丢数据）") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "包含全部预算周期、支出流水、快捷按钮与定时提醒设置；导入前自动校验完整性并原子恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val fileName = "superxiao-budget-backup-${LocalDate.now()}.json"
                                        exportBackupLauncher.launch(fileName)
                                    } catch (e: Exception) {
                                        scope.launch { snackbarHostState.showSnackbar("无法打开文件保存器：${e.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("导出本地")
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    } catch (e: Exception) {
                                        scope.launch { snackbarHostState.showSnackbar("无法打开文件选择器：${e.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("导入备份")
                            }
                        }
                        Button(
                            onClick = { shareBackupFile() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("一键导出并分享（微信 / QQ 等）")
                        }
                    }
                }
            }

            // 7. 快捷扣款按钮管理（支持排序、编辑、删除）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "快捷扣款按钮管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { showAddButtonDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新增按钮")
                    }
                }
            }

            itemsIndexed(state.quickButtons, key = { _, btn -> btn.id }) { index, btn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(btn.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                text = "固定扣款：¥${MoneyUtils.formatCents(btn.amountCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(
                                enabled = index > 0,
                                onClick = { viewModel.moveQuickButton(btn.id, moveUp = true) }
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
                            }
                            IconButton(
                                enabled = index < state.quickButtons.lastIndex,
                                onClick = { viewModel.moveQuickButton(btn.id, moveUp = false) }
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
                            }
                            IconButton(onClick = { editingButton = btn }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                            IconButton(onClick = { viewModel.deleteQuickButton(btn) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 8. 关于我
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToAbout),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "关于我",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "有问题？联系我",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "进入关于我",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 9. 重置当前周期流水
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("重置当前周期（仅清空本期支出流水）")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (pickingStartDate) {
        BudgetDatePickerDialog(
            initialDate = state.startDate,
            onDismiss = { pickingStartDate = false },
            onDateSelected = { selected ->
                viewModel.updateDateRange(selected, state.endDate)
                pickingStartDate = false
            }
        )
    }

    if (pickingEndDate) {
        BudgetDatePickerDialog(
            initialDate = state.endDate,
            onDismiss = { pickingEndDate = false },
            onDateSelected = { selected ->
                viewModel.updateDateRange(state.startDate, selected)
                pickingEndDate = false
            }
        )
    }

    if (showNewPeriodDialog) {
        NewPeriodDialog(
            defaultBudgetText = MoneyUtils.formatCentsForInput(state.totalBudgetCents),
            onDismiss = { showNewPeriodDialog = false },
            onConfirm = { budgetText, start, end ->
                viewModel.startNewPeriod(budgetText, start, end)
                showNewPeriodDialog = false
            }
        )
    }

    if (showAddButtonDialog) {
        QuickButtonEditDialog(
            title = "新增快捷按钮",
            initialLabel = "",
            initialAmount = "",
            onDismiss = { showAddButtonDialog = false },
            onConfirm = { label, amountText ->
                viewModel.addQuickButton(label, amountText)
                showAddButtonDialog = false
            }
        )
    }

    editingButton?.let { target ->
        QuickButtonEditDialog(
            title = "编辑快捷按钮",
            initialLabel = target.label,
            initialAmount = MoneyUtils.formatCentsForInput(target.amountCents),
            onDismiss = { editingButton = null },
            onConfirm = { label, amountText ->
                viewModel.updateQuickButton(target, label, amountText)
                editingButton = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("确认重置当前周期？") },
            text = {
                Text("此操作仅清空当前活跃周期的支出流水，并将剩余余额恢复为当前周期总预算；已归档的历史周期、起止日期、每日预算设定和快捷按钮都会完整保留。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetActivePeriodExpenses()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清空本期")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    pendingImportJson?.let { jsonText ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("确认导入备份？") },
            text = { Text("导入备份将会用备份文件内容替换当前本地的所有预算周期、支出流水和快捷按钮（若文件校验不通过，原数据不会被修改）。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackupJson(jsonText) { restoredReminders ->
                            restoredReminders.forEach { slot ->
                                ReminderPrefs.saveSlot(context, slot)
                                ReminderScheduler.scheduleSlot(context, slot)
                            }
                            reminderSlots = ReminderPrefs.loadSlots(context)
                        }
                        pendingImportJson = null
                    }
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) {
                    Text("取消")
                }
            }
        )
    }

    editingReminderSlot?.let { slot ->
        ReminderTimePickerDialog(
            slot = slot,
            onDismiss = { editingReminderSlot = null },
            onConfirm = { hour, minute ->
                val updated = slot.copy(hour = hour, minute = minute, enabled = true)
                editingReminderSlot = null
                if (hasNotificationPermission) {
                    updateSlot(updated)
                    scope.launch {
                        snackbarHostState.showSnackbar("已设置${updated.label}为每天 ${updated.formattedTime()}")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NewPeriodDialog(
    defaultBudgetText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val ym = remember { YearMonth.from(today) }
    var budgetText by remember { mutableStateOf(defaultBudgetText) }
    var startDate by remember { mutableStateOf(ym.atDay(1)) }
    var endDate by remember { mutableStateOf(ym.atEndOfMonth()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建预算周期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前周期及其所有流水会自动归档，随时可在首页右上角「历史周期」查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("新周期总预算 (元)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                        Text("起 ${startDate.format(fmt)}", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                        Text("止 ${endDate.format(fmt)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = MoneyUtils.parseYuanToCents(budgetText, allowZero = true) != null && !endDate.isBefore(startDate),
                onClick = { onConfirm(budgetText, startDate, endDate) }
            ) {
                Text("开启新周期")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (pickStart) {
        BudgetDatePickerDialog(
            initialDate = startDate,
            onDismiss = { pickStart = false },
            onDateSelected = {
                startDate = it
                pickStart = false
            }
        )
    }
    if (pickEnd) {
        BudgetDatePickerDialog(
            initialDate = endDate,
            onDismiss = { pickEnd = false },
            onDateSelected = {
                endDate = it
                pickEnd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onDateSelected(picked)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun QuickButtonEditDialog(
    title: String,
    initialLabel: String,
    initialAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var amountText by remember { mutableStateOf(initialAmount) }
    val valid = label.isNotBlank() && MoneyUtils.parseYuanToCents(amountText, allowZero = false) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("按钮名称（如：午餐、咖啡）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("固定金额 (元，最多两位小数)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onConfirm(label, amountText) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    slot: ReminderSlot,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = slot.hour,
        initialMinute = slot.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置${slot.label}时间") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
