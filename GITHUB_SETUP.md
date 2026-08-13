# GitHub 仓库设置建议

仓库地址：[https://github.com/MSCNUAN/message-relay-android](https://github.com/MSCNUAN/message-relay-android)

## 基本信息

- 仓库名称：`message-relay-android`
- 描述：开源 Android 通知转发工具，支持将短信、微信、电话和其他 App 通知转发到 Bark、飞书或钉钉。
- Website：`https://www.nuan1145.eu.cc/`
- 默认分支：`main`
- 许可证：`GPL-3.0-only`

## Topics

```text
android
kotlin
jetpack-compose
material3
notification-listener
notification-forwarder
message-relay
sms-forwarder
call-notification
dual-sim
bark
feishu
dingtalk
webhook
room
datastore
workmanager
open-source
chinese
android-app
```

## Release 发布流程

1. 确认 README、LICENSE、CHANGELOG、CONTRIBUTING、SECURITY、CODE_OF_CONDUCT 和 GitHub 模板已提交。
2. 确认源码仓库不包含 APK、日志、数据库、备份、签名文件和敏感凭证。
3. 运行构建和测试命令。
4. 使用 release keystore 构建正式签名 APK。
5. 计算 APK SHA-256。
6. 创建 tag，例如 `v0.1.3`。
7. 创建 GitHub Release：
   - 标题：`消息接力 v0.1.3 Beta`
   - 类型：Beta 可标记为 Pre-release；正式稳定版不要标记 Pre-release。
   - APK：`MessageRelay-v0.1.3-beta.apk`
8. Release Notes 写明新增、优化、修复、已知问题、Android 版本要求、安装说明、APK SHA-256 和源码标签。

## App 内版本更新检查

App 会请求公开 GitHub Releases API：

```text
GET https://api.github.com/repos/MSCNUAN/message-relay-android/releases?per_page=10
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

不要把个人 GitHub Token 放进 APK。Debug 构建允许检测 Pre-release，Release 构建默认只提示正式 Release。

## 发布前注意

本地整理阶段不要擅自创建远程仓库、推送代码、发布 Release 或上传 APK。公开前请再次确认 Bark 地址、Webhook、Token、电话号码、验证码和日志均已脱敏。
