# 超级魈的记账工具 (SuperXiao Budget Tracker)

一款纯离线、极简、高精度的 Android 原生预算管控记账工具（Kotlin + Jetpack Compose + Material 3 + Room）。

> **最新安装包下载**：前往 [Releases v2.0](https://github.com/xushanzhenxun2233/SuperXiao-BudgetTracker/releases/tag/v2.0) 直接下载 `SuperXiao-BudgetTracker-v2.0.apk` 安装体验。

## 界面与功能展示

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/screenshots/home_dashboard.jpg" alt="首页预算大卡片与快捷扣款" width="100%" /><br />
      <b>首页预算管控 & 快捷扣款</b><br />
      <sub>剩余金额大卡片、每日自动/计划预算测算、一键快捷扣款</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/screenshots/settings_features.jpg" alt="设置页：定时提醒、图标切换、生物识别、本地备份" width="100%" /><br />
      <b>丰富个性化设置</b><br />
      <sub>定时提醒快捷回复、4 款角色图标切换、隐私锁、JSON 备份</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="docs/screenshots/biometric_lock.jpg" alt="指纹与人脸生物识别解锁保护" width="100%" /><br />
      <b>指纹 / 人脸生物识别锁</b><br />
      <sub>切后台即刻自动锁定，支持指纹、人脸及系统锁屏密码兜底</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/screenshots/release_overview.jpg" alt="v1.5 版本特性总览" width="100%" /><br />
      <b>v1.5 版本总览</b><br />
      <sub>纯离线无广告、Google Material 3 动态取色与深浅色适配</sub>
    </td>
  </tr>
</table>

## 核心功能

- **预算管控与结余预测**：
  - 手动设置当前周期总预算与起止日期，实时展示剩余可用金额、已花费金额、剩余天数与每日预估可花。
  - 支持自定义每日计划预算（如 ¥30/天），自动测算到期能否剩钱并给出每日花销控制建议。
  - 全程采用整数分（`Long`）存储与 `BigDecimal` 校验，彻底避免浮点精度误差。
- **极速记账与时间修改**：
  - 支持自定义快捷扣款按钮（新增、编辑、删除、上下排序），一键扣款。
  - 支持手动输入任意金额与备注，支持随时修改已有流水或补记任意日期与时间。
- **多周期归档 & 本地 JSON 备份**：
  - 支持「新建预算周期」（旧周期自动归档至历史可回看）与「重置当前周期」。
  - 通过 Android SAF 导出/导入 UTF-8 JSON 备份文件，并支持一键分享至微信/QQ，换机不丢数据。
- **贴心系统集成**：
  - **定时记账提醒**：支持早上/中午/晚上自定义提醒时间，并调用安卓原生通知栏快捷回复（Direct Reply）免打开 APP 直接记账。
  - **隐私保护**：支持指纹/人脸识别及锁屏密码解锁，退出/切后台即刻自动锁定。
  - **个性化外观**：遵循 Google Material 3（Android 12+ 动态取色），适配深浅色模式、白天模式专属剪影背景，内置神里绫华/胡桃/派蒙/刻晴 4 款桌面图标切换。

## 编译与测试

环境要求：JDK 17+、Android SDK（API 34）、Gradle 8.7+。

```powershell
# 运行单元测试并打包 Debug APK
gradle testDebugUnitTest assembleDebug

# 生成的 APK 路径
# app/build/outputs/apk/debug/app-debug.apk
```
