# AGENTS.md

本文供后续 Codex 或维护者继续处理消息接力项目时参考。

## 项目定位

消息接力是 Kotlin + Jetpack Compose + Material 3 编写的 Android 通知转发工具。核心能力包括通知监听、规则筛选、模板渲染、Bark/飞书/钉钉推送、记录、备份恢复、短信事件、电话事件和每应用独立规则。

## 维护规则

1. 不要把全部代码继续堆进 `MainActivity.kt`；新增复杂逻辑应拆到独立文件或状态层。
2. UI 不应直接执行数据库和网络操作，优先通过 Repository、Worker 或业务对象完成。
3. 修改 Room Entity 时必须提供 Migration，并兼容旧用户数据。
4. 新增 DataStore 字段必须有默认值。
5. 不得清空用户现有配置，不得在升级时静默丢弃渠道、规则和模板。
6. 不得在日志中输出 Token、Webhook、完整电话号码和验证码。
7. 未经真机验证的电话、SIM、AOD/Doze 和厂商后台能力不能写成稳定。
8. 所有用户提示使用简体中文。
9. README、CHANGELOG 和应用内更新日志需要保持事实一致。
10. 源码仓库不提交 APK、数据库、备份、日志、签名文件或本机私有配置。

## 常用命令

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:installDebug connectedDebugAndroidTest --no-daemon --console=plain
```

`connectedDebugAndroidTest` 需要可用 Android 设备或模拟器。
