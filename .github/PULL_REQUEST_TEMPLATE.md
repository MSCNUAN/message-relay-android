# PR 说明

## 改动内容

-

## 影响范围

-

## 测试结果

- [ ] `.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`
- [ ] `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
- [ ] `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- [ ] `.\gradlew.bat :app:installDebug connectedDebugAndroidTest --no-daemon --console=plain`

## 数据兼容

- [ ] 不涉及 Room / DataStore / 备份恢复
- [ ] 涉及 Room，已提供 Migration
- [ ] 涉及 DataStore，已提供默认值
- [ ] 涉及备份恢复，已兼容旧字段

## 安全检查

- [ ] 未提交 Token、Webhook、签名文件、密码、完整号码、验证码、日志、数据库或备份
- [ ] 日志和诊断输出已脱敏

## 截图

如涉及 UI，请附上已脱敏截图。
