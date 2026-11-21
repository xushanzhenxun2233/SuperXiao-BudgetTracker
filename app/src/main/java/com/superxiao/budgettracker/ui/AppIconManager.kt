package com.superxiao.budgettracker.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.superxiao.budgettracker.R

enum class AppIconOption(
    val aliasClassName: String,
    val displayName: String,
    val previewRes: Int
) {
    AYAKA("com.superxiao.budgettracker.IconAyakaAlias", "神里绫华", R.drawable.icon_preview_ayaka),
    HUTAO("com.superxiao.budgettracker.IconHutaoAlias", "胡桃", R.drawable.icon_preview_hutao),
    NAVY("com.superxiao.budgettracker.IconNavyAlias", "派蒙", R.drawable.icon_preview_navy),
    KEQING("com.superxiao.budgettracker.IconKeqingAlias", "刻晴", R.drawable.icon_preview_keqing)
}

object AppIconManager {
    private const val PREF_NAME = "budget_icon_prefs"
    private const val KEY_DESIRED_ICON = "desired_icon_name"

    /** 返回用户当前选中的图标（优先读取待生效的选择，其次读取系统当前启用的别名） */
    fun getCurrentIcon(context: Context): AppIconOption {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val desiredName = prefs.getString(KEY_DESIRED_ICON, null)
        if (desiredName != null) {
            AppIconOption.entries.firstOrNull { it.name == desiredName }?.let { return it }
        }
        return getActiveSystemIcon(context)
    }

    /** 前台仅保存用户选择，不触发系统杀进程；真正切换延迟到退到后台 onStop 时执行 */
    fun selectIcon(context: Context, target: AppIconOption) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DESIRED_ICON, target.name)
            .apply()
    }

    /** 在 Activity.onStop()（退回桌面/后台）时调用，安全应用待切换的桌面图标 */
    fun applyPendingIconIfNeeded(context: Context) {
        val desired = getCurrentIcon(context)
        val active = getActiveSystemIcon(context)
        if (desired == active) return

        try {
            val pm = context.packageManager
            val targetComp = ComponentName(context.packageName, desired.aliasClassName)
            if (pm.getComponentEnabledSetting(targetComp) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            for (option in AppIconOption.entries) {
                if (option != desired) {
                    val comp = ComponentName(context.packageName, option.aliasClassName)
                    if (pm.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun getActiveSystemIcon(context: Context): AppIconOption {
        val pm = context.packageManager
        for (option in AppIconOption.entries) {
            val state = try {
                pm.getComponentEnabledSetting(
                    ComponentName(context.packageName, option.aliasClassName)
                )
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return option
            }
        }
        return AppIconOption.AYAKA
    }
}
