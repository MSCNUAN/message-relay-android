# 贡献指南

感谢你愿意参与消息接力。仓库地址：[https://github.com/MSCNUAN/message-relay-android](https://github.com/MSCNUAN/message-relay-android)。

请先阅读 `README.md`、`SECURITY.md` 和 `AGENTS.md`。这个项目涉及通知、短信、电话、Webhook 和推送 Token，提交前请特别注意脱敏。

## Fork 和分支流程

1. Fork 仓库。
2. 从 `main` 创建功能分支，例如 `fix/template-preview`。
3. 保持改动聚焦，不要把无关格式化和功能修改混在同一个 PR。
4. 提交 PR 前运行项目测试命令。

## 构建命令

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## 测试命令

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

有可用设备或模拟器时再运行：

```powershell
.\gradlew.bat :app:installDebug connectedDebugAndroidTest --no-daemon --console=plain
```

## Commit 和 PR 建议

- Commit 信息使用简短中文或英文，说明实际改动。
- PR 描述写清楚问题、方案、测试结果和影响范围。
- 修改 Room Entity 时必须提供 Migration。
- 新增 DataStore 字段必须有默认值。
- 不要清空或重置用户已有配置。
- 涉及 UI 的 PR 请附上已脱敏截图。

## Bug 复现所需信息

请尽量提供：

- 手机品牌和型号；
- Android 版本；
- 厂商系统版本；
- 单卡、双卡或 eSIM；
- 应用版本；
- 复现步骤；
- 预期结果；
- 实际结果；
- 是否开启仅息屏时推送；
- 是否开启免打扰；
- 已脱敏日志；
- 已脱敏截图。

## 支持提交的贡献类型

- Bug 修复；
- UI 可读性和无障碍改进；
- 文档补充；
- 单元测试和 Compose 测试；
- 真机兼容性验证；
- Bark、飞书、钉钉兼容性修复；
- 诊断和脱敏能力改进。

## 禁止提交的敏感信息

不要提交：

- Bark Token；
- 飞书或钉钉 Webhook；
- GitHub Token；
- Android 签名文件或密码；
- 完整电话号码；
- 短信验证码；
- 完整通知正文；
- 用户备份；
- 数据库文件；
- 未脱敏日志。

## 贡献许可

向本项目提交贡献，即表示你同意贡献内容按 GPL-3.0-only 许可证发布。
