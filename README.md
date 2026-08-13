# 消息接力 Message Relay

开源 Android 通知转发工具。它可以把你选择的短信、微信、电话和其他 App 通知，在本机完成筛选和模板渲染后，转发到 Bark、飞书或钉钉。

仓库地址：[https://github.com/MSCNUAN/message-relay-android](https://github.com/MSCNUAN/message-relay-android)

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![Status](https://img.shields.io/badge/status-v0.1.3%20Beta-orange)
![License](https://img.shields.io/badge/license-GPL--3.0--only-blue)

## 小白也能上手

App 使用 `首页 / 记录 / 设置` 三页结构。第一次使用只需要按首页提示完成三件事：

1. 配置推送渠道：Bark、飞书、钉钉任选一种作为主渠道。
2. 选择要转发的软件：例如短信、微信、电话。
3. 选择消息模板：内置模板可以直接预览和测试。

如果已经配置好飞书或钉钉，并且所有设备都能看到同一个机器人消息或群聊，就可以借助飞书/钉钉自己的多端同步能力，在电脑、平板和其他手机上接收转发消息。

## 核心功能

- Bark、飞书、钉钉推送渠道。
- 多个 Bark 设备配置，支持 Bark 名称、声音、图标 URL、分组、提醒等级和持续响铃参数。
- Bark 设备可绑定 App：某个 App 可指定只发到某几个 Bark，未绑定时走主渠道或高级多渠道规则。
- 来源应用选择、关键词包含/排除、每应用模板、每应用仅息屏时推送。
- 电话通知类型：未接来电、来电提醒、来电已接通。
- 短信验证码关键词筛选。
- 四套简单模板：简洁、标准、隐私、原始通知。
- 高级自定义模板、关键词过滤、应用独立规则。
- 免打扰、重要关键词例外、发送延迟、通知合并、自动重试。
- 转发记录、失败重试、仍然发送、复制、删除和诊断。
- 基础配置备份与恢复。
- 深色 / 浅色 / 跟随系统主题。
- 内置使用教程、更新日志、关于页和 GitHub Releases 版本更新检查。

## 当前版本

- applicationId：`io.github.messagerelay`
- versionName：`0.1.3`
- versionCode：`4`
- minSdk：`26`
- targetSdk：`36`

当前仍是 Beta 测试版。电话、双卡、AOD/Doze、厂商后台保活和真实公网推送送达，建议在自己的真机上继续回归。

## GitHub 更新检查

App 内置 `设置 -> 帮助与关于 -> 版本更新` 页面：

- 当前版本读取 `BuildConfig.VERSION_NAME` 和 `BuildConfig.VERSION_CODE`。
- 默认开启自动检查更新，最多每 24 小时请求一次 GitHub Releases。
- 用户可以关闭自动检查；关闭后启动 App 不会主动请求 GitHub。
- 手动点击 `检查更新` 不受 24 小时限制。
- 检测到新版本后只引导用户打开 GitHub Release 页面自行下载。
- 不内置 GitHub Token，不静默下载 APK，不自动安装 APK。

使用的公开接口：

```text
GET https://api.github.com/repos/MSCNUAN/message-relay-android/releases?per_page=10
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

Debug 构建允许检测 prerelease，Release 构建默认只提示正式 Release。

## 权限说明

App 可能会申请以下权限，均用于本地识别和转发你选择的消息：

- 通知访问：读取你选择来源 App 的通知。
- 通知权限：显示前台状态通知。
- 设备应用列表 / 查询所有软件包：读取已安装应用，方便选择来源。
- 电话状态：识别来电提醒、接通和未接来电。
- 通话记录：可选，用于尽力补全号码。
- 联系人：可选，用于显示联系人名称。
- 短信接收：用于短信验证码等短信事件识别。

通知筛选、模板渲染和规则判断都在本机完成。不要在 Issues、截图或日志中公开 Bark 地址、Webhook、签名密钥、完整手机号、验证码或完整通知正文。

## 构建

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Debug APK 默认输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

本项目源码仓库不提交 APK。公开分发时请在 GitHub Releases 上传签名后的 APK，并附带 SHA-256。

## 开发文档

- [架构说明](docs/ARCHITECTURE.md)：按 UI、设置、通知入口、规则、模板、渠道、备份和更新检查拆解项目。
- [开发与验证](docs/DEVELOPMENT.md)：本地构建、测试、模拟器 QA、正式签名和维护注意事项。
- [开源前检查清单](docs/OPEN_SOURCE_CHECKLIST.md)：发布前的文件、敏感信息和构建检查。
- [GitHub Release 模板](docs/RELEASE_TEMPLATE.md)：发布说明和校验信息模板。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:installDebug connectedDebugAndroidTest --no-daemon --console=plain
```

`connectedDebugAndroidTest` 需要连接 Android 模拟器或测试设备。

## 已知限制

- 不同厂商对后台、自启、锁后台、省电限制和 AOD/Doze 的处理差异很大。
- 电话号码、联系人和归属地能否补全取决于系统权限、Android 版本和厂商限制。
- 离线号码归属地和运营商信息可能因携号转网不准确。
- Bark、飞书、钉钉实际送达取决于网络、机器人配置和服务端状态。
- 当前主要面向 GitHub / 自用分发，未按 Google Play 政策做权限裁剪。

## 许可证

GPL-3.0-only

Copyright (C) 2026 MSCNUAN
