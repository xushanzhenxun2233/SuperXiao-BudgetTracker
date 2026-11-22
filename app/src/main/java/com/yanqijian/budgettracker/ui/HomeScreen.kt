package com.yanqijian.budgettracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanqijian.budgettracker.data.ExpenseRecordEntity
import com.yanqijian.budgettracker.data.MoneyUtils
import com.yanqijian.budgettracker.data.QuickButtonEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: BudgetViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showManualDialog by remember { mutableStateOf(false) }
    var showDailyBudgetDialog by remember { mutableStateOf(false) }
    var pendingDeleteExpense by remember { mutableStateOf<ExpenseRecordEntity?>(null) }
    var editingTimeExpense by remember { mutableStateOf<ExpenseRecordEntity?>(null) }

    // 切回前台或跨零点自动刷新剩余天数
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                title = { Text("岩契笺", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "历史周期")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一笔") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 首页核心大卡片
            item {
                BudgetSummaryCard(
                    state = state,
                    onEditDailyBudget = { showDailyBudgetDialog = true }
                )
            }

            // 2. 快捷扣款区域
            item {
                QuickButtonsSection(
                    buttons = state.quickButtons,
                    onButtonClick = { btn ->
                        viewModel.recordQuickButtonExpense(btn)
                    },
                    onManageClick = onNavigateToSettings
                )
            }

            // 3. 支出流水标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本期支出流水",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "共 ${state.expenses.size} 笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. 支出流水列表
            if (state.expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无支出记录，点击上方快捷按钮或右下角「记一笔」开始记账",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onEditTimeClick = { editingTimeExpense = expense },
                        onDeleteClick = { pendingDeleteExpense = expense }
                    )
                }
            }
        }
    }

    if (showManualDialog) {
        ManualExpenseDialog(
            allExpenses = state.allExpenses,
            quickButtons = state.quickButtons,
            onDismiss = { showManualDialog = false },
            onConfirm = { amountText, note, timestampMillis ->
                viewModel.recordExpenseFromText(amountText, note, timestampMillis)
                showManualDialog = false
            }
        )
    }

    editingTimeExpense?.let { target ->
        ExpenseDateTimePickerDialog(
            title = "修改记录时间",
            initialTimestampMillis = target.timestampMillis,
            onDismiss = { editingTimeExpense = null },
            onConfirm = { newMillis ->
                viewModel.updateExpenseTimestamp(target, newMillis)
                editingTimeExpense = null
            }
        )
    }

    if (showDailyBudgetDialog) {
        CustomDailyBudgetDialog(
            remainingCents = state.remainingCents,
            remainingDays = state.remainingDays,
            suggestedDailyCents = state.suggestedDailyBudgetCents,
            currentCustomCents = state.customDailyBudgetCents,
            onDismiss = { showDailyBudgetDialog = false },
            onSaveCustom = { amountText ->
                viewModel.updateCustomDailyBudgetFromText(amountText)
                showDailyBudgetDialog = false
            },
            onRestoreAuto = {
                viewModel.updateCustomDailyBudgetFromText(null)
                showDailyBudgetDialog = false
            }
        )
    }

    pendingDeleteExpense?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteExpense = null },
            title = { Text("确认删除记录？") },
            text = {
                Text(
                    "删除「${target.note.ifBlank { "手动支出" }}」（¥${MoneyUtils.formatCents(target.amountCents)}）后，对应金额将自动返还至当前周期余额。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(target)
                        pendingDeleteExpense = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteExpense = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BudgetSummaryCard(
    state: BudgetUiState,
    onEditDailyBudget: () -> Unit
) {
    val isNegative = state.remainingCents < 0L
    val progress = MoneyUtils.calculateSpendRatio(state.spentCents, state.totalBudgetCents)
    val rangeText = remember(state.startDate, state.endDate) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        "${state.startDate.format(fmt)} 至 ${state.endDate.format(fmt)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNegative) "剩余可用金额（已超支）" else "剩余可用金额",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isNegative) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    }
                )
                if (state.isPeriodEnded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "本期已结束",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "¥ ${MoneyUtils.formatCents(state.remainingCents)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "周期：$rangeText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已花费：¥${MoneyUtils.formatCents(state.spentCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "总预算：¥${MoneyUtils.formatCents(state.totalBudgetCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(14.dp))

            val endDateShort = remember(state.endDate) {
                state.endDate.format(DateTimeFormatter.ofPattern("MM-dd"))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "剩余天数（至 $endDateShort）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "${state.remainingDays} 天",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEditDailyBudget)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.isDailyBudgetCustom) "每日计划预算" else "每日自动日均",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "设定每日计划预算",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "¥ ${MoneyUtils.formatCents(state.effectiveDailyCents)} / 天",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val customDaily = state.customDailyBudgetCents
            val projected = state.projectedEndBalanceCents
            val plannedTotal = state.plannedTotalSpendCents

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditDailyBudget),
                shape = RoundedCornerShape(12.dp),
                color = if ((projected != null && projected < 0L) || state.isPeriodEnded) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (state.isPeriodEnded) {
                        Text(
                            text = "本预算周期已结束，可前往设置页「新建预算周期」开启新周期。",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (customDaily != null && projected != null && plannedTotal != null) {
                        if (projected >= 0L) {
                            Text(
                                text = "按每天 ¥${MoneyUtils.formatCents(customDaily)} 算，${state.remainingDays} 天预计花 ¥${MoneyUtils.formatCents(plannedTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "到期预计还能剩：¥ ${MoneyUtils.formatCents(projected)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                text = "按每天 ¥${MoneyUtils.formatCents(customDaily)} 算，${state.remainingDays} 天预计还差 ¥${MoneyUtils.formatCents(-projected)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "预算建议：每天花销控制在 ¥${MoneyUtils.formatCents(state.suggestedDailyBudgetCents)} 以内",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = "点此设定每日计划花销（如 ¥30/天），测算到期能否剩钱",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickButtonsSection(
    buttons: List<QuickButtonEntity>,
    onButtonClick: (QuickButtonEntity) -> Unit,
    onManageClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "快捷扣款",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onManageClick) {
                Text("管理按钮")
            }
        }

        if (buttons.isEmpty()) {
            Text(
                text = "暂无快捷按钮，可前往设置页添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buttons.forEach { btn ->
                    FilledTonalButton(
                        onClick = { onButtonClick(btn) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${btn.label}  -¥${MoneyUtils.formatCents(btn.amountCents)}",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(
    expense: ExpenseRecordEntity,
    onEditTimeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeText = remember(expense.timestampMillis) {
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        Instant.ofEpochMilli(expense.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEditTimeClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.note.ifBlank { "手动支出" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "修改时间",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$timeText（点此改时间）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "-¥${MoneyUtils.formatCents(expense.amountCents)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualExpenseDialog(
    allExpenses: List<ExpenseRecordEntity>,
    quickButtons: List<QuickButtonEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showTimePicker by remember { mutableStateOf(false) }

    val parsedCents = MoneyUtils.parseYuanToCents(amountText, allowZero = false)
    val isValid = parsedCents != null
    val formattedTime = remember(selectedTimestamp) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault()).format(fmt)
    }

    val suggestedNotes = remember(parsedCents, selectedTimestamp, allExpenses, quickButtons) {
        if (parsedCents == null) {
            emptyList()
        } else {
            computeSmartNoteSuggestions(
                amountCents = parsedCents,
                targetTimestampMillis = selectedTimestamp,
                allExpenses = allExpenses,
                quickButtons = quickButtons
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("支出金额 (元，最多两位小数)") },
                    isError = amountText.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestedNotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "猜你想记（点击快速填入）：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            suggestedNotes.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { noteText = suggestion },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("时间：$formattedTime")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { onConfirm(amountText, noteText, selectedTimestamp) }
            ) {
                Text("确认扣款")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showTimePicker) {
        ExpenseDateTimePickerDialog(
            title = "选择记录时间",
            initialTimestampMillis = selectedTimestamp,
            onDismiss = { showTimePicker = false },
            onConfirm = { newMillis ->
                selectedTimestamp = newMillis
                showTimePicker = false
            }
        )
    }
}

private fun timeOfDayBucket(timestampMillis: Long): Int {
    val hour = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).hour
    return when (hour) {
        in 5..10 -> 0   // 早上
        in 11..14 -> 1  // 中午
        in 15..17 -> 2  // 下午
        in 18..23 -> 3  // 晚上
        else -> 4       // 凌晨
    }
}

private fun computeSmartNoteSuggestions(
    amountCents: Long,
    targetTimestampMillis: Long,
    allExpenses: List<ExpenseRecordEntity>,
    quickButtons: List<QuickButtonEntity>
): List<String> {
    val targetBucket = timeOfDayBucket(targetTimestampMillis)
    val scores = mutableMapOf<String, Long>()
    val latestTime = mutableMapOf<String, Long>()

    allExpenses.forEach { expense ->
        if (expense.amountCents == amountCents) {
            val cleaned = expense.note.removePrefix("快捷扣款：").trim()
            if (cleaned.isNotEmpty()) {
                val sameBucketBonus = if (timeOfDayBucket(expense.timestampMillis) == targetBucket) 30L else 0L
                scores[cleaned] = (scores[cleaned] ?: 0L) + 10L + sameBucketBonus
                val prevLatest = latestTime[cleaned] ?: 0L
                if (expense.timestampMillis > prevLatest) {
                    latestTime[cleaned] = expense.timestampMillis
                }
            }
        }
    }

    quickButtons.forEach { btn ->
        if (btn.amountCents == amountCents) {
            val cleaned = btn.label.trim()
            if (cleaned.isNotEmpty()) {
                scores[cleaned] = (scores[cleaned] ?: 0L) + 5L
            }
        }
    }

    return scores.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Long>> { it.value }
                .thenByDescending { latestTime[it.key] ?: 0L }
        )
        .map { it.key }
        .take(4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDateTimePickerDialog(
    title: String,
    initialTimestampMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val initialZdt = remember(initialTimestampMillis) {
        Instant.ofEpochMilli(initialTimestampMillis).atZone(zone)
    }
    var pickedDate by remember { mutableStateOf(initialZdt.toLocalDate()) }
    var showDateDialog by remember { mutableStateOf(false) }

    val timePickerState = rememberTimePickerState(
        initialHour = initialZdt.hour,
        initialMinute = initialZdt.minute,
        is24Hour = true
    )
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("日期：${pickedDate.format(dateFmt)}（点击修改）")
                }
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newZdt = ZonedDateTime.of(
                        pickedDate,
                        LocalTime.of(timePickerState.hour, timePickerState.minute),
                        zone
                    )
                    onConfirm(newZdt.toInstant().toEpochMilli())
                }
            ) {
                Text("保存时间")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showDateDialog) {
        val initMillis = pickedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            pickedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDateDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CustomDailyBudgetDialog(
    remainingCents: Long,
    remainingDays: Int,
    suggestedDailyCents: Long,
    currentCustomCents: Long?,
    onDismiss: () -> Unit,
    onSaveCustom: (String) -> Unit,
    onRestoreAuto: () -> Unit
) {
    var amountText by remember {
        mutableStateOf(currentCustomCents?.let { MoneyUtils.formatCentsForInput(it) } ?: "")
    }
    val previewDailyCents = MoneyUtils.parseYuanToCents(amountText, allowZero = true)
    val previewProjectedCents = previewDailyCents?.let { remainingCents - it * remainingDays }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设定每日计划预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前剩余 ¥${MoneyUtils.formatCents(remainingCents)}，剩余 $remainingDays 天（自动日均 ¥${MoneyUtils.formatCents(suggestedDailyCents)}/天）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("每日计划花销 (元/天，如 30)") },
                    isError = amountText.isNotEmpty() && previewDailyCents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (previewDailyCents != null && previewProjectedCents != null) {
                    if (previewProjectedCents >= 0L) {
                        Text(
                            text = "测算结果：到期预计还能剩 ¥${MoneyUtils.formatCents(previewProjectedCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text(
                            text = "测算结果：预计还差 ¥${MoneyUtils.formatCents(-previewProjectedCents)}，建议每天不超过 ¥${MoneyUtils.formatCents(suggestedDailyCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = previewDailyCents != null,
                onClick = { onSaveCustom(amountText) }
            ) {
                Text("保存设定")
            }
        },
        dismissButton = {
            Row {
                if (currentCustomCents != null) {
                    TextButton(onClick = onRestoreAuto) {
                        Text("恢复自动")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
