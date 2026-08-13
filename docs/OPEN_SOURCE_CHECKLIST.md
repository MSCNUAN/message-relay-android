# 开源前检查清单

仓库地址：[https://github.com/MSCNUAN/message-relay-android](https://github.com/MSCNUAN/message-relay-android)

## 必备文件

- [ ] `README.md`
- [ ] `LICENSE`
- [ ] `CHANGELOG.md`
- [ ] `CONTRIBUTING.md`
- [ ] `SECURITY.md`
- [ ] `CODE_OF_CONDUCT.md`
- [ ] `.gitignore`
- [ ] `.github/PULL_REQUEST_TEMPLATE.md`
- [ ] `.github/ISSUE_TEMPLATE/bug_report.yml`
- [ ] `.github/ISSUE_TEMPLATE/feature_request.yml`
- [ ] `.github/workflows/android-ci.yml`
- [ ] `docs/ARCHITECTURE.md`
- [ ] `docs/DEVELOPMENT.md`

## 敏感信息检查

- [ ] 没有提交 APK / AAB。
- [ ] 没有提交 `.jks`、`.keystore`、签名密码或 keystore 配置。
- [ ] 没有提交 Bark Token。
- [ ] 没有提交飞书 / 钉钉 Webhook。
- [ ] 没有提交 GitHub Token。
- [ ] 没有提交完整电话号码、验证码、通知正文。
- [ ] 没有提交数据库、备份、诊断日志或私有截图。

## 构建检查

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## 发布检查

- [ ] `versionName` 和 `versionCode` 已确认。
- [ ] `CHANGELOG.md` 已补充当前版本。
- [ ] `docs/RELEASE_TEMPLATE.md` 已同步当前版本。
- [ ] GitHub Release 上传的是正式签名 APK，而不是 Debug APK。
- [ ] Release Notes 已填写 APK SHA-256。
- [ ] Release 标记为 Beta / Pre-release，除非已经完成稳定版真机验收。

## 推荐仓库设置

- 默认分支：`main`
- License：`GPL-3.0-only`
- Issues：开启
- Discussions：可选
- Actions：开启
- Releases：开启
