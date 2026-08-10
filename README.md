<!--
===============================================================================
消息接力 Message Relay - GitHub README
图片目录建议：
docs/images/cover/
docs/images/advantages/
docs/images/screenshots/

README 中所有图片均使用 GitHub 支持的仓库相对路径，方便后续替换。
===============================================================================
-->

<div align="center">

# 📲 消息接力 Message Relay

### 不用服务器、不用写代码，几步设置，把安卓手机的重要消息同步到你常用的设备。

一款面向小白用户的开源 Android 消息转发工具。  
支持将 **短信、微信 / APP 通知、来电提醒** 等消息转发到 **Bark、飞书、钉钉**。

</div>

---


![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810093125635.png)

> [!IMPORTANT]
> **关于本项目**
>
> 本工具全程使用 **OpenAI Codex** 协助创作与开发，从需求梳理、界面调整到代码实现、测试和 Bug 修复均大量借助 AI 完成。
>
> AI 生成的代码难免会存在遗漏、兼容性问题或 Bug，因此本项目仍需要持续真机测试与完善。  
> 如果你在使用过程中发现问题，欢迎通过 GitHub Issues 反馈，并尽量附上设备型号、Android 版本、复现步骤和脱敏后的日志。

---

---

## 🚨 当前已知 Bug

> [!WARNING]
> **正式使用前请先了解以下已知问题。**
>
> 目前已经确认的 Bug：
>
> 1. **电话来电提醒阶段不会显示号码、联系人和归属地**  
>    当前部分设备在“正在来电”阶段只能识别到来电事件，号码、联系人和归属地可能显示为空或未知；**挂断 / 通话结束后**，应用通常可以通过系统通话记录补全这些信息。
>
> 2. **所有消息的推送时间目前会重复显示两次**  
>    APP、短信、电话等部分推送中，接收时间可能出现重复。该问题属于展示层 Bug，不影响消息本身的接收和转发。
>
> 以上是目前已经确认的问题，后续会继续修复。  
> 如果你发现其他 Bug、兼容问题或某个品牌手机上的异常，欢迎到 **[TG 聊天室](https://t.me/MSC4652)** 反馈。
>
> 提交问题时建议附上：手机型号、Android / 系统版本、消息接力版本、复现步骤和脱敏截图。请勿公开 Bark Key、Webhook、验证码、真实电话号码等敏感信息。


## 💡 为什么做这个项目？

我之前使用过同类型的短信 / 通知转发工具。

这类工具通常功能很丰富，但实际使用过程中，我发现对普通用户来说经常会遇到几个问题：

- 设置项比较多，第一次打开不知道从哪里开始；
- 推送渠道、应用规则、模板、过滤条件混在一起，学习成本较高；
- 一些功能需要理解 Webhook、通知监听、模板变量等概念；
- 收到的推送内容字段很多，看起来更像调试信息，不够简洁；
- 对只想“把另一台安卓机的重要消息转过来”的用户来说，有些功能明显过重。

所以我根据自己的实际使用习惯重新做了 **消息接力 Message Relay**。

这个项目的目标不是堆尽可能多的功能，而是：

> **把常用功能留下，把设置流程变简单，把收到的消息排版变得更清楚。**

普通用户第一次使用时，只需要完成几个核心步骤：

1. 配置一个推送渠道；
2. 选择需要转发的软件；
3. 选择消息模板；
4. 按照提示开启必要权限；
5. 发送测试消息。

完成以后就可以开始使用


### 📸 首页界面

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810093850790.jpg)

---

## 👥 适合哪些用户？

消息接力比较适合：

- 📱 手上有两台或多台手机；
- 📩 备用安卓机经常收到短信验证码；
- 💬 不想频繁查看备用机上的微信 / APP 通知；
- 📞 工作卡、生活卡放在另一台安卓设备中；
- 🍎 希望把安卓通知同步到 Bark；
- 💻 希望把手机消息同步到飞书 / 钉钉，在电脑端及时查看；
- 🧑‍💻 不想折腾复杂自动化、脚本或自建服务器；
- 🌱 第一次接触消息转发工具的小白用户。

---

## ✨ 主要功能

### 📡 多种推送渠道

支持：

- **Bark**
- **飞书自定义机器人**
- **钉钉机器人**

简单模式下只需要选择和配置主要推送渠道即可。


#### 📸 推送渠道界面

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810103306144.jpg)

### 📩 短信转发

收到短信后，可以将短信内容转发到已经配置的推送渠道。

默认展示方向：

```text
106900000000

这里是短信内容

📩 来自：106900000000

📲 卡槽：工作卡

🕒 接收时间：2026-08-08 18:00:00
```

支持识别 SIM / 卡槽信息，并优先显示用户设置的 SIM 名称。


#### 📸 短信实际推送效果

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810094412628.jpg)

### 📞 电话通知

支持的电话通知类型：

- 来电提醒
- 来电接通
- 未接来电

默认展示方向：

```text
13423876512

来自：张三

📍 归属地：上海 · 联通

📲 卡槽：工作卡

🔔 提醒：未接来电

🕒 接收时间：2026-08-08 18:00:00
```

> [!NOTE]
> 电话号码、联系人和归属地能否在“响铃阶段”立即获取，会受到 Android 版本、手机厂商电话框架以及系统 API 的限制。  
> 某些设备可能需要在通话结束后才能从系统通话记录中补全更多信息。


#### 📸 电话设置

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810103410032.jpg)

#### 📸 电话实际推送效果

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810104033765.jpg)

### 💬 APP 通知转发

可选择微信以及其他安装在手机上的 APP。

默认简洁模板：

```text
微信

📝：内容：这里是通知内容

🕒：接收时间：2026-08-08 18:00:00
```

项目会尽量读取 Android 通知实际提供的信息，但不会尝试读取其他 APP 的私有数据库。

> 微信联系人、群聊名称等信息是否能够识别，取决于微信当前版本实际写入 Android 通知的数据。


#### 📸 软件选择界面

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810104438164.jpg)

#### 📸 APP 实际推送效果

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810104648687.jpg)

### 🔒 仅锁屏时推送

每个 APP 可以单独开启：

**仅锁屏时推送**

开启后：

- 手机锁屏：正常转发；
- 屏幕关闭：正常转发；
- AOD / 息屏显示：正常转发；
- 锁屏界面被通知点亮：正常转发；
- 手机已经解锁并正在使用：不转发；
- 被过滤的旧消息不会在之后锁屏时补发。



### 📲 双卡与 SIM 名称

支持识别设备中的 SIM，并允许给 SIM 设置更容易理解的本地名称，例如：

```text
SIM 1 → 工作卡
SIM 2 → 生活卡
```


#### 📸 SIM 管理

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810103101264.jpg)

### 🌙 免打扰

支持设置免打扰时间，例如：

```text
23:00 → 08:00
```

可以保留验证码、来电、未接来电等重要消息例外。


#### 📸 免打扰设置

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810102455776.jpg)

### 📝 消息模板

内置多种模板：

- 简洁
- 标准
- 隐私
- 原始通知

模板页面提供预览，尽量保证预览效果与实际推送结果一致。


#### 📸 消息模板与预览

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810102118043.jpg)

### 🧾 转发记录

应用内可以查看：

- 全部
- 成功
- 失败
- 已过滤

支持设置记录保存时间：

- 7 天
- 30 天
- 90 天
- 永久
- 仅保存状态


#### 📸 转发记录

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810105134938.jpg)
-->

### 💾 备份与恢复




#### 📸 备份与恢复

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810100051189.jpg)

### 🛠️ 后台运行检查

提供统一的后台运行检查入口，用于辅助确认：

- 通知使用权
- 应用通知权限
- 电话相关权限
- 电池优化
- 后台运行状态

部分厂商的“自启动”“锁定后台”等设置无法通过 Android 通用 API 可靠自动判断，需要用户按照系统界面手动确认。


#### 📸 后台运行检查

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810100244068.jpg)

---

## ⚖️ 项目优缺点

![](https://cdn.jsdelivr.net/gh/MSCNUAN/blog-images@main/img/20260810095910667.png)

### ✅ 优点

- 面向小白重新整理设置流程；
- 不需要自己搭建中转服务器；
- 支持 Bark、飞书、钉钉；
- 支持短信、电话和普通 APP 通知；
- 推送模板尽量简洁，不堆调试字段；
- 支持仅锁屏时推送；
- 支持双卡与 SIM 自定义名称；
- 支持免打扰、记录和备份恢复；
- 项目源码公开，可自行检查和编译。

### ⚠️ 限制

- Android 不同厂商后台策略差异较大；
- 微信联系人和群聊名称取决于微信实际写入的通知字段；
- 部分 Android 手机在响铃阶段可能拿不到完整来电号码；
- 电话联系人和归属地可能需要等通话记录写入后才能补全；
- 部分能力需要更多品牌真机测试；
- 本项目大量使用 Codex 辅助开发，出现 Bug 和兼容问题在所难免。

---


## 🖼️ README 图片清单

这版 README 已经把**软件截图分散插入到对应功能介绍中**，不会再把所有截图集中堆在文章后面。

最终准备以下图片即可：

| 图片 | 文件路径 | 对应文章位置 |
|---|---|---|
| 项目介绍图 | `docs/images/cover/message-relay-intro.png` | README 开头 |
| 项目优缺点图 | `docs/images/advantages/pros-and-cons.png` | 项目优缺点 |
| 首页 | `docs/images/screenshots/01-home.png` | 项目介绍 / 使用流程后 |
| 推送渠道 | `docs/images/screenshots/02-push-channel.png` | 多种推送渠道 |
| 短信推送 | `docs/images/screenshots/03-sms-push.png` | 短信转发 |
| 电话设置 | `docs/images/screenshots/04-call-settings.png` | 电话通知 |
| 电话推送 | `docs/images/screenshots/05-call-push.png` | 电话通知 |
| 软件选择 | `docs/images/screenshots/06-app-selection.png` | APP 通知转发 |
| APP 推送 | `docs/images/screenshots/07-app-push.png` | APP 通知转发 |
| 仅锁屏 | `docs/images/screenshots/08-locked-only.png` | 仅锁屏时推送 |
| SIM 管理 | `docs/images/screenshots/09-sim-management.png` | 双卡与 SIM |
| 免打扰 | `docs/images/screenshots/10-dnd.png` | 免打扰 |
| 消息模板 | `docs/images/screenshots/11-message-template.png` | 消息模板 |
| 转发记录 | `docs/images/screenshots/12-history.png` | 转发记录 |
| 备份恢复 | `docs/images/screenshots/13-backup.png` | 备份与恢复 |
| 后台检查 | `docs/images/screenshots/14-background-check.png` | 后台运行检查 |
| 设置 / 深色 | `docs/images/screenshots/15-settings-dark.png` | 设计思路 |

> 每个图片位置都已经写好 Markdown 注释。图片准备好后，把文件放到对应目录，再取消该位置 `![...](...)` 的注释即可。


---

## 🚀 快速开始

### 第一步：安装 APK

进入仓库的 **Releases** 页面，下载最新版本 APK。

建议只从本项目官方 GitHub 仓库获取安装包。

### 第二步：配置推送渠道

首次使用建议先配置一个渠道：

- Bark
- 飞书
- 钉钉

保存后先发送一次测试消息。

### 第三步：选择需要转发的软件

进入：

```text
首页 → 软件选择
```

默认推荐：

- 短信
- 电话
- 微信

也可以搜索其他已经安装的 APP。

### 第四步：开启必要权限

按照应用中的提示开启需要的权限。

例如：

- 通知使用权
- 通知权限
- 短信相关权限
- 电话状态权限
- 通话记录权限
- 联系人权限（用于显示电话联系人名称）

不同 Android 版本可能显示不同的权限名称。

### 第五步：检查后台运行

如果锁屏一段时间后不再转发，请重点检查：

- 电池优化
- 后台运行
- 自启动
- 最近任务锁定

不同品牌手机设置方法可能不同。

---

## 🎯 设计思路

### 1. 小白优先

常用功能尽量放在首页和基础设置中。

不要求用户先理解：

- 包名
- Webhook 原理
- JSON
- 模板变量
- Android 后台机制

### 2. 设置流程尽量短

普通用户只需要完成：

```text
推送渠道
↓
软件选择
↓
消息模板
↓
权限检查
```

### 3. 推送内容尽量简洁

相比把所有字段全部塞进通知，消息接力更倾向于：

**第一眼先看到重要内容，技术信息放到记录详情和诊断工具。**

### 4. 出错尽量说人话

尽可能把技术错误转换成普通用户能理解的中文提示。


### 📸 设置页 / 深色模式

<!--
截图：docs/images/screenshots/15-settings-dark.png
建议展示：设置页整体结构，并使用深色模式展示 Material 3 适配效果。
上传图片后取消下一行注释：
![设置页深色模式](docs/images/screenshots/15-settings-dark.png)
-->

---

## 🔐 隐私说明

消息接力本身不依赖作者自建的消息中转服务器。

消息会根据你的配置发送到对应的第三方服务，例如：

- Bark
- 飞书
- 钉钉

请注意：

- Bark Key 属于敏感凭证；
- 飞书 Webhook 属于敏感凭证；
- 钉钉 Webhook 属于敏感凭证；
- 备份文件可能包含配置和历史消息；
- Debug / 诊断日志在公开前必须脱敏。

不要在 GitHub Issue 中直接上传包含真实验证码、电话号码、联系人姓名或完整 Webhook 的截图。

---

## ⚠️ 已知限制

### 微信联系人 / 群聊名称

应用只能读取微信实际发布到 Android 通知系统中的数据。

如果当前微信版本没有在通知中提供发送者、群聊名称等字段，消息接力无法读取微信私有数据库强行补全。

### 电话号码 / 联系人 / 归属地

电话相关能力会受到 Android 版本、手机品牌、电话框架以及通话记录写入时间影响。

部分手机可能在响铃阶段无法立即拿到完整号码，而在挂断后才能通过通话记录补全。

### Android 后台限制

不同厂商的后台、省电和自启动策略差异很大。

某些设备可能需要额外设置才能长期稳定运行。

---

## 🐛 Bug 与反馈

本项目全程使用 Codex 协助开发。

由于：

- Android 厂商差异大；
- 通知结构会随着 APP 版本变化；
- 电话 / SIM API 在不同系统上的行为不同；
- AI 生成代码本身也可能存在遗漏；

出现 Bug 在所难免。

遇到问题时，建议提交：

```text
手机品牌：
手机型号：
Android版本：
系统版本：
消息接力版本：

问题类型：
[ ] 短信
[ ] 电话
[ ] 微信 / APP通知
[ ] Bark
[ ] 飞书
[ ] 钉钉
[ ] 后台运行
[ ] 其他

复现步骤：

预期结果：

实际结果：
```

上传日志前，请先确认日志已经脱敏。

---

---

## 🌐 暖暖の小窝 · 相关链接与自用推荐

### 🎬 暖暖の小窝 · 资源导航

📝 **个人博客（更多教程 / 软件分享）**  
👉 https://www.nuan1145.eu.cc/

📦 **个人资源站（综合导航 / 必备收藏）**  
👉 https://tools.nuan1145.eu.cc/

### 🔥 个人 TG 频道 & 聊天群

📢 **频道（最新资源 / 更新）**  
👉 https://t.me/NUAN114514

💬 **聊天室（交流 / 答疑 / Bug 反馈）**  
👉 https://t.me/MSC4652

### 🧰 自用工具推荐

🔧 **自用内网穿透工具（cpolar）**  
👉 https://www.cpolar.com/?channel=0&invite=6DaX

📂 **PikPak 磁力下载**  
👉 https://mypikpak.com/drive/activity/invited?invitation-code=66396543

💾 **123 网盘（提取码：NUAN）**  
👉 https://www.123684.com/s/R2hjVv-

💰 **币安 Binance（新人注册福利）**  
👉 https://www.bmwweb.academy/referral/earn-together/refer2earn-usdc/claim?hl=zh-TC&ref=GRO_28502_RAE9X&utm_source=default

🌍 **海外账号 / AI / 流媒体 / 游戏**  
👉 https://accboyytbnn.acceboy.com/

🍎 **苹果外区 ID / 软件 / 充值卡购买**  
👉 https://goso002.com/?from=24529

### 🚀 自用网络工具推荐

✈️ **相关说明 / 推荐入口**  
👉 https://t.me/NUAN114514/5

> [!CAUTION]
> **温馨提示**
>
> 以上部分链接可能包含推广 / 邀请关系，相关服务仅作信息分享。  
> 涉及账号、资金、通信服务、跨境服务、数字资产、网络工具或版权内容时，请先确认自己是否符合相应的年龄、地区与平台使用条件，并自行判断风险，遵守当地法律法规与平台规则。  
> 部分链接可能随时失效，实际情况以对应官方页面为准。


## 🧪 测试说明

每次准备发布新版本时，建议至少检查：

- APP 是否可以正常启动；
- Bark 测试推送；
- 飞书测试推送；
- 短信真实转发；
- 微信 / APP 通知转发；
- 电话提醒；
- 仅锁屏时推送；
- 免打扰；
- 记录页面；
- 备份恢复；
- 深色模式；
- 大字体；
- 旧版本覆盖升级。

即使自动化测试全部通过，也不能完全代替真机测试。

---

## 🧑‍💻 开发者

### 技术栈

项目主要使用：

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- DataStore
- WorkManager
- NotificationListenerService
- Android Telephony API

### 克隆仓库

```bash
git clone https://github.com/MSCNUAN/message-relay-android.git
cd message-relay-android
```

### Debug 构建

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

Linux / macOS：

```bash
./gradlew :app:assembleDebug
```

### 单元测试

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Linux / macOS：

```bash
./gradlew :app:testDebugUnitTest
```

---

## 🗺️ 后续计划

- [ ] 持续完善不同品牌手机兼容性
- [ ] 完善微信通知字段解析
- [ ] 完善电话响铃阶段号码识别
- [ ] 完善双卡 / eSIM 真机测试
- [ ] 增加更多自动化测试
- [ ] 完善 GitHub Release 流程
- [ ] 根据用户反馈持续精简使用体验

---

## 📄 开源许可

本项目计划使用：

**GNU General Public License v3.0**

```text
GPL-3.0-only
```

Copyright (C) 2026 MSCNUAN

---

## 👤 作者

**暖暖（MSCNUAN）**

项目主要根据个人实际使用需求进行设计和调整。

如果这个项目刚好解决了你的需求，欢迎：

- ⭐ Star
- 🐛 提交 Issue
- 🔧 提交 Pull Request
- 💬 分享你的设备兼容情况

---

<div align="center">

**消息接力 Message Relay**

让备用安卓机不再需要一直拿在手里，也尽量不错过重要消息。

</div>
