# 岩契笺 (YanQiJian)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Room SQLite](https://img.shields.io/badge/Storage-Room_SQLite_v2-003B57?style=flat-square&logo=sqlite&logoColor=white)
![Android](https://img.shields.io/badge/Android-API_26%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Offline](https://img.shields.io/badge/Network-100%25_Offline-16A34A?style=flat-square)

一款**纯离线、零网络权限、金融级整数分精度**的 Android 原生减法预算管控工具。  
拒绝事后焦虑式「累计记账」与臃肿分类图表，以**预算倒扣模型 + 每日结余推演 + 系统级零摩擦录入**为核心，用现代 Android 工程实践打造极致克制且可靠的本地财务工具。

> **立即下载 APK**：前往 [Releases v2.5](https://github.com/xushanzhenxun2233/SuperXiao-BudgetTracker/releases/tag/v2.5) 获取最新构建的 `YanQiJian-v2.5.apk`。

---

## 界面预览（按核心链路排序）

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/screenshots/home_dashboard.jpg" alt="01 首页预算倒扣看板与快捷扣款" width="100%" /><br />
      <b>01 · 预算倒扣看板 & 极速录入</b><br />
      <sub>整数分余额实时派生 · 每日结余预测 · 金额+时段智能备注联想 · 流水任意改期</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/screenshots/settings_features.jpg" alt="02 系统级能力集成与数据备份" width="100%" /><br />
      <b>02 · 系统能力 & 数据可迁移</b><br />
      <sub>原生 RemoteInput 通知快捷记账 · 4 款动态别名图标 · SAF/FileProvider 原子备份</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="docs/screenshots/biometric_lock.jpg" alt="03 硬件级生物识别锁" width="100%" /><br />
      <b>03 · 硬件级生物识别隐私锁</b><br />
      <sub>Android BiometricPrompt 指纹/人脸认证 · 切后台 onStop 即刻锁定 · 配置变更免重验</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/screenshots/release_overview.jpg" alt="04 v2.0 工程特性与开源发布" width="100%" /><br />
      <b>04 · v2.5 开源工程规格</b><br />
      <sub>零 INTERNET 权限 · Room 事务一致写入 · Material You 动态取色与深浅色适配</sub>
    </td>
  </tr>
</table>

---

## 硬核工程特性与底层实现

### 1. 金融级金额账本与零漂移架构
- **整数分（`Long` cents）全链路存储**：彻底摒弃二进制浮点（`Float`/`Double`）参与财务运算，输入层采用 `BigDecimal.movePointRight(2).longValueExact()` 严格校验两位小数与上界防溢出。
- **派生余额设计（Single Source of Truth）**：剩余可用金额绝不作为独立可变字段落库，而是由 `totalBudgetCents - SUM(expenses.amountCents)` 通过 Room `Flow` + `StateFlow` 响应式实时派生，从根源杜绝账目漂移。
- **Room v2 规范化 Schema & 无损迁移**：多周期 `budget_periods` + 外键级联索引 `expense_records(periodId, timestampMillis)` + 排序 `quick_buttons`，内置 `MIGRATION_1_2` 平滑升级旧版浮点数据，零数据丢失。

### 2. 确定性日期边界与每日结余推演
- 基于 `java.time.LocalDate` 与 `ChronoUnit.DAYS` 计算包含当日的剩余天数，完整覆盖未开始、进行中、截止当日、已过期（零除保护）以及闰年/跨年区间；监听 `Lifecycle.Event.ON_RESUME` 在跨午夜切回前台时自动刷新。
- 支持自定义每日计划花销，实时推演 `projectedEndBalanceCents = remainingCents - plannedDailyCents × remainingDays`：有结余直接给出到期预计剩额，出现缺口立刻计算每日降本建议上限。

### 3. 零摩擦录入与系统原生深度集成
- **金额 + 时段智能备注联想**：手动记账输入金额后，自动检索跨周期全部历史流水与快捷按钮，对**同金额 + 同时段**（早/中/下午/晚/凌晨 5 档时间桶）动态加权排序，一键点选填入。
- **Android 原生 `RemoteInput` 通知栏快捷回复**：结合 `AlarmManager.setAndAllowWhileIdle` 与 `BOOT_COMPLETED` 开机重调度；在通知栏直接回复 `25 午餐`，`QuickReplyReceiver` 通过 `goAsync()` + Room 单事务异步解析落库，并原地刷新通知反馈最新剩余余额。
- **防进程回收的动态桌面图标引擎**：基于 4 组 `<activity-alias>`（神里绫华 / 胡桃 / 派蒙 / 刻晴），前台仅持久化目标选择，延迟至 `Activity.onStop()` 退回后台时执行 `PackageManager.setComponentEnabledSetting`，规避 Android 前台禁用活跃别名触发系统强制杀进程的经典坑。

### 4. 隐私安全与离线可迁移
- **零联网权限**：Manifest 不声明 `INTERNET` 权限，无任何第三方统计/广告 SDK，100% 本地离线运行。
- **生命周期感知生物识别锁**：基于 `androidx.biometric.BiometricPrompt`（按 API 30+ / API 26–29 区分合法认证器组合防崩溃），采用全屏不透明遮罩层保持 `NavHost` 状态栈不销毁；`onStop()` 切后台即刻锁定，而应用内切换深浅色模式或旋转屏幕触发的配置重建自动保持已解锁会话。
- **版本化 JSON 备份（SAF + `FileProvider`）**：无需存储权限即可导出/导入带 `schemaVersion` 的完整 UTF-8 JSON（覆盖多周期、流水、快捷按钮与提醒计划），导入前全量校验版本号、ID 唯一性、外键引用与金额合法性，校验通过后在 Room 单事务中原子替换；另支持 `FileProvider` 一键分享备份至微信/QQ。

---

## 技术栈与项目结构

- **语言与框架**：Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09.00) · Material 3 (Dynamic Color) · Navigation Compose
- **持久化与并发**：Room 2.6.1 (KSP) · Kotlin Coroutines & Flow · Lifecycle-aware StateFlow
- **系统集成**：`androidx.biometric:biometric:1.1.0` · `NotificationCompat` + `RemoteInput` · `AlarmManager` · SAF & `FileProvider`

```text
app/src/main/java/com/yanqijian/budgettracker/
├── MainActivity.kt                  # 单 Activity 宿主、全局白天剪影背景、生命周期生物识别锁与延迟换图标
├── data/
│   ├── MoneyUtils.kt                # BigDecimal 精确解析、整数分格式化、跨月/闰年剩余天数与日均算法
│   ├── BudgetEntities.kt            # Room v2 多周期 / 外键流水 / 快捷按钮表定义
│   ├── BudgetDao.kt                 # Flow 响应式查询与批量事务 DAO
│   ├── BudgetDatabase.kt            # Room 单例与 MIGRATION_1_2 无损迁移脚本
│   ├── BudgetRepository.kt          # Room withTransaction 原子扣款、周期归档、备份导入导出
│   └── BackupManager.kt             # 版本化 JSON 序列化与全量前置校验器
├── notification/
│   ├── ReminderPrefs.kt             # 定时提醒时段配置持久化
│   ├── ReminderScheduler.kt         # AlarmManager 定时调度与开机恢复
│   ├── NotificationHelper.kt        # RemoteInput 通知构建与三层兜底跳转系统通知设置
│   ├── ReminderReceiver.kt          # 闹钟触发与次日自动续约广播接收器
│   └── QuickReplyReceiver.kt        # 通知栏快捷回复正则解析与后台事务入库
├── security/
│   └── BiometricLockManager.kt      # API 兼容生物识别封装（指纹 / 人脸 / 锁屏密码兜底）
└── ui/
    ├── BudgetViewModel.kt           # 多流 combine 状态聚合与单向数据流动作分发
    ├── HomeScreen.kt                # 预算大卡片、智能备注联想、快捷扣款与时间修改器
    ├── HistoryScreen.kt             # 历史归档周期可展开明细页
    ├── SettingsScreen.kt            # 预算/周期/提醒/图标/隐私锁/SAF 备份与未授权置灰保护
    ├── AboutMeScreen.kt             # 关于作者与扫码联系页
    ├── AppIconManager.kt            # 延迟生效的多角色桌面图标切换器
    └── theme/Theme.kt               # Google Material 3 动态取色与完整深浅色色阶
```

---

## 本地构建与单元测试

环境要求：JDK 17+ · Android SDK (API 34) · Gradle 8.7+

```powershell
# 运行 JUnit 4 领域单元测试（精度、日期边界、JSON 备份回环与异常校验）并编译 Debug APK
gradle testDebugUnitTest assembleDebug

# 输出产物路径
# app/build/outputs/apk/debug/app-debug.apk
```
