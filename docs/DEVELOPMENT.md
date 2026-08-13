# 开发与验证

## 环境要求

- Android Studio 或命令行 Gradle 环境。
- JDK 21。
- Android SDK，当前 `compileSdk=36`、`minSdk=26`、`targetSdk=36`。
- 可选：Android 模拟器、MuMu 模拟器或真机。

## 本地构建

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Debug APK 默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintDebug --no-daemon --console=plain
```

有模拟器或真机时运行：

```powershell
.\gradlew.bat :app:installDebug connectedDebugAndroidTest --no-daemon --console=plain
```

## 模拟器 QA

建议至少检查：

- 首页：运行状态、配置进度、推送渠道、软件选择、模板入口。
- 记录：全部、成功、失败、已过滤标签和详情操作。
- 设置：主题、使用教程、推送渠道、SIM 管理、版本更新、关于页。
- 推送渠道：Bark、飞书、钉钉保存与测试。
- 软件选择：App 设置、仅息屏时推送、关键词、电话通知类型。

Logcat 重点检查：

```text
AndroidRuntime
FATAL EXCEPTION
SecurityException
SQLiteException
IllegalStateException
```

## 正式签名

不要把签名密码写入仓库。Release 构建通过临时环境变量读取签名信息：

```powershell
$env:MESSAGE_RELAY_KEYSTORE_PATH="C:\path\release.jks"
$env:MESSAGE_RELAY_KEYSTORE_PASSWORD="***"
$env:MESSAGE_RELAY_KEY_ALIAS="***"
$env:MESSAGE_RELAY_KEY_PASSWORD="***"
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

发布前用 Android build tools 验证 APK 签名，并计算 SHA-256。

## 常见维护注意

- 修改 Room Entity 必须增加迁移并补测试。
- 新增 DataStore 字段必须有默认值。
- 修改备份格式必须保证旧备份可导入，失败时不能覆盖当前配置。
- 不要在日志、Issue、截图和测试数据中暴露 Token、Webhook、完整号码、验证码或完整通知正文。
- 电话、双卡、AOD/Doze 和厂商后台行为需要真机复测，不能只凭模拟器标记稳定。
