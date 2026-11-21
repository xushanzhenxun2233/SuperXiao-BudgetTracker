package com.superxiao.budgettracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.superxiao.budgettracker.data.BudgetDatabase
import com.superxiao.budgettracker.data.BudgetRepository
import com.superxiao.budgettracker.data.MoneyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            ?: return

        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 3099)
        val defaultNote = intent.getStringExtra(NotificationHelper.EXTRA_DEFAULT_NOTE) ?: "快捷记账"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsed = parseQuickReply(replyText, defaultNote)
                if (parsed == null) {
                    NotificationHelper.showReplyFeedbackNotification(
                        context = context,
                        notificationId = notificationId,
                        title = "未识别到有效金额",
                        content = "请回复包含两位以内小数的金额，例如：25 午餐"
                    )
                } else {
                    val (amountCents, note) = parsed
                    val db = BudgetDatabase.getInstance(context.applicationContext)
                    val repo = BudgetRepository(db)
                    repo.ensureInitialized()
                    val remainingCents = repo.addExpenseTransactional(amountCents, note) ?: 0L

                    NotificationHelper.showReplyFeedbackNotification(
                        context = context,
                        notificationId = notificationId,
                        title = "已记账 -¥${MoneyUtils.formatCents(amountCents)}（$note）",
                        content = "当前周期剩余可用余额：¥${MoneyUtils.formatCents(remainingCents)}"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseQuickReply(input: String, defaultNote: String): Pair<Long, String>? {
        val regex = Regex("""\d+(\.\d+)?""")
        val match = regex.find(input) ?: return null
        val amountCents = MoneyUtils.parseYuanToCents(match.value, allowZero = false) ?: return null

        val cleanedNote = input.replaceFirst(match.value, "")
            .replace("元", "")
            .replace("块", "")
            .replace("¥", "")
            .trim()
        val note = cleanedNote.ifBlank { defaultNote }
        return amountCents to note
    }
}
