package io.github.messagerelay

import org.junit.Assert.*
import org.junit.Test
import android.telephony.TelephonyManager
import android.view.Display
import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime

class CoreLogicTest {
    private val message = RelayMessage("com.sms", "短信", "验证码", "1234", 0)

    @Test fun `filter requires allowed app and keywords`() {
        val rule = RelayRule(setOf("com.sms"), listOf("验证"), listOf("广告"))
        assertTrue(rule.matches(message))
        assertFalse(rule.matches(message.copy(body = "广告验证码")))
    }

    @Test fun `quiet hours can cross midnight and urgent keyword bypasses`() {
        val quiet = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0), listOf("验证码", "来电", "未接来电"))
        val normal = message.copy(title = "普通通知", body = "明天开会")
        assertTrue(quiet.shouldQueue(normal, LocalTime.of(23, 0)))
        assertTrue(quiet.shouldQueue(normal, LocalTime.of(22, 0)))
        assertFalse(quiet.shouldQueue(normal, LocalTime.of(7, 0)))
        assertFalse(quiet.shouldQueue(message.copy(title = "短信验证码"), LocalTime.of(23, 0)))
        assertFalse(quiet.shouldQueue(message.copy(title = "未接来电"), LocalTime.of(23, 0)))
    }

    @Test fun `template rejects unknown variables`() {
        assertEquals("短信", MessageTemplate().renderTitle(message))
        assertThrows(IllegalArgumentException::class.java) { MessageTemplate("{{bad}}", "{{body}}").renderTitle(message) }
    }

    @Test fun `template renders all supported variables`() {
        val template = MessageTemplate("{{app}}：{{title}}", "{{body}}\n{{time}}")
        assertEquals("${message.app}：${message.title}", template.renderTitle(message))
        assertEquals("${message.body}\n${TimeFormatter.formatRecordDetailTime(message.time)}", template.renderBody(message))
    }

    @Test fun `dedupe rejects same message for sixty seconds`() {
        val dedupe = DedupeWindow()
        assertTrue(dedupe.accept(message, 1_000))
        assertFalse(dedupe.accept(message, 60_999))
        assertTrue(dedupe.accept(message, 61_001))
    }

    @Test fun `only network 429 and server errors are retryable`() {
        assertTrue(DeliveryPolicy.shouldRetry(null, networkError = true))
        assertTrue(DeliveryPolicy.shouldRetry(429))
        assertTrue(DeliveryPolicy.shouldRetry(503))
        assertFalse(DeliveryPolicy.shouldRetry(400))
        assertFalse(DeliveryPolicy.shouldRetry(401))
        assertFalse(DeliveryPolicy.shouldRetry(204))
    }

    @Test fun `bark requires https endpoint`() {
        assertTrue(ChannelValidation.isValid(ChannelConfig("bark", "https://api.day.app/token")))
        assertFalse(ChannelValidation.isValid(ChannelConfig("bark", "http://api.day.app/token")))
        assertFalse(ChannelValidation.isValid(ChannelConfig("bark", "not-a-url")))
    }

    @Test fun `feishu signature is deterministic`() {
        assertEquals(
            "fiWS2+gh28DOydAv7hzONH/mDn9+b1Y4Y5ivXWXy8vA=",
            ChannelSignatures.feishu("secret", 1_700_000_000)
        )
    }

    @Test fun `encrypted backup rejects wrong password and unknown version`() {
        val encrypted = BackupCodec.encrypt("""{"version":1}""", "correct".toCharArray())
        assertEquals("""{"version":1}""", BackupCodec.decrypt(encrypted, "correct".toCharArray()))
        assertThrows(Exception::class.java) { BackupCodec.decrypt(encrypted, "wrong".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decrypt("message-relay-backup-v2.a.b.c", "correct".toCharArray()) }
    }

    @Test fun `relay response keeps independent channel outcomes`() {
        val results = ChannelSender.parseRelayResults(
            """{"results":[{"type":"dingtalk","ok":true,"status":200},{"type":"bark","ok":false,"status":503}]}""",
            listOf(ChannelConfig("dingtalk", "https://oapi.dingtalk.com/a"), ChannelConfig("bark", "https://api.day.app/a"))
        )
        assertTrue(results[0].success)
        assertFalse(results[1].success)
        assertTrue(results[1].retryable)
    }

    @Test fun `built in templates include communication presets`() {
        assertEquals(
            setOf("general", "simple", "standard", "privacy", "raw", "phone", "sms", "wechat", "work_wechat", "qq", "dingtalk", "feishu"),
            TemplateCatalog.builtIns.map { it.id }.toSet()
        )
        val callMessage = RelayMessage("com.android.dialer", "电话", "来电提醒", "联系人：张三\n号码：13800138000\n归属地：上海市\n号码段运营商：中国联通\nSIM：工作卡\n提醒：未接来电", 0)
        val smsMessage = RelayMessage("com.sms", "短信", "106846831601642", "【哔哩哔哩】29052短信登录验证码，5分钟内有效，请勿泄露。", 0)
        assertEquals("13800138000", TemplateCatalog.byId("phone").template().renderTitle(callMessage))
        assertEquals("106846831601642", TemplateCatalog.byId("sms").template().renderTitle(smsMessage))
        assertEquals("短信", TemplateCatalog.byId("wechat").template().renderTitle(message))
        assertTrue(TemplateCatalog.byId("wechat").template().renderBody(message).contains("📝：内容：1234"))
        assertTrue(TemplateCatalog.byId("wechat").template().renderBody(message).contains(TimeFormatter.formatRecordDetailTime(message.time)))
        assertTrue(TemplateCatalog.byId("phone").template().renderBody(callMessage).contains("来自：张三"))
        assertTrue(TemplateCatalog.byId("phone").template().renderBody(callMessage).contains("📍 归属地：上海 · 联通"))
        assertTrue(TemplateCatalog.byId("phone").template().renderBody(callMessage).contains("📲 卡槽：工作卡"))
        assertFalse(TemplateCatalog.byId("phone").template().renderBody(callMessage).contains("号码来源"))
        assertTrue(TemplateCatalog.byId("sms").template().renderBody(smsMessage).contains("📩 来自：106846831601642"))
    }

    @Test fun `template recommendation uses package and app name`() {
        assertEquals("wechat", TemplateCatalog.recommend("微信", "com.tencent.mm"))
        assertEquals("work_wechat", TemplateCatalog.recommend("企业微信", "com.tencent.wework"))
        assertEquals("sms", TemplateCatalog.recommend("信息", "com.android.messaging"))
        assertEquals("phone", TemplateCatalog.recommend("电话", "com.android.dialer"))
        assertEquals("general", TemplateCatalog.recommend("日历", "com.android.calendar"))
    }

    @Test fun `selected apps are deduplicated by package`() {
        val selected = SelectedSources.add(emptyList(), SourceSelection("短信", "com.sms", "sms"))
        val duplicate = SelectedSources.add(selected, SourceSelection("另一名称", "com.sms", "general"))
        assertEquals(1, duplicate.size)
        assertEquals("短信", duplicate.single().appName)
    }

    @Test fun `multiple bark channels are retained with metadata`() {
        val channels = listOf(
            ChannelConfig("dingtalk", "https://oapi.dingtalk.com/a", enabled = false),
            ChannelConfig("feishu", "https://open.feishu.cn/a"),
            ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "iPhone", sound = "bell", icon = "https://example.com/icon.png"),
            ChannelConfig("bark", "https://api.day.app/b", id = "bark_b", name = "备用机")
        )
        val parsed = ChannelSender.parse(ChannelSender.serialize(channels))
        assertEquals(listOf("dingtalk", "feishu", "bark", "bark"), parsed.map(ChannelConfig::type))
        assertEquals(listOf("钉钉", "飞书", "iPhone", "备用机"), parsed.map(ChannelConfig::name))
        assertFalse(parsed.first { it.type == "dingtalk" }.enabled)
        assertEquals("bell", parsed.first { it.id == "bark_a" }.sound)
        assertEquals("https://example.com/icon.png", parsed.first { it.id == "bark_a" }.icon)
    }

    @Test fun `bark target selection filters only bark channels`() {
        val channels = listOf(
            ChannelConfig("feishu", "https://open.feishu.cn/a", id = "feishu_a", name = "飞书"),
            ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "iPhone"),
            ChannelConfig("bark", "https://api.day.app/b", id = "bark_b", name = "备用机")
        )
        assertEquals(listOf("飞书", "iPhone", "备用机"), ChannelSelection.enabled(channels).map(ChannelConfig::name))
        assertEquals(listOf("飞书", "备用机"), ChannelSelection.enabled(channels, "bark_b").map(ChannelConfig::name))
        assertEquals(listOf("feishu"), ChannelSelection.enabled(channels, ChannelSelection.NO_BARK_TARGETS).map(ChannelConfig::type))
    }

    @Test fun `record retention computes cleanup cutoff and status only body policy`() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, RecordRetentionPolicy.cutoffMillis("7", now))
        assertEquals(now - 30L * 24 * 60 * 60 * 1000, RecordRetentionPolicy.cutoffMillis("30", now))
        assertEquals(now - 90L * 24 * 60 * 60 * 1000, RecordRetentionPolicy.cutoffMillis("90", now))
        assertNull(RecordRetentionPolicy.cutoffMillis("forever", now))
        assertNull(RecordRetentionPolicy.cutoffMillis("status_only", now))
        assertTrue(RecordRetentionPolicy.shouldKeepBody("30"))
        assertFalse(RecordRetentionPolicy.shouldKeepBody("status_only"))
    }

    @Test fun `simple primary channel keeps bark when rule has no explicit bark binding`() {
        val channels = listOf(
            ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "iPhone"),
            ChannelConfig("bark", "https://api.day.app/b", id = "bark_b", name = "备用机")
        )
        assertEquals(listOf("iPhone"), ChannelSelection.primaryEnabled(channels, "bark_a", "").map(ChannelConfig::name))
        assertTrue(ChannelSelection.primaryEnabled(channels, "bark_a", ChannelSelection.NO_BARK_TARGETS).isEmpty())
    }

    @Test fun `bark channel app binding overrides bark targets only`() {
        val channels = listOf(
            ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "iPhone"),
            ChannelConfig("bark", "https://api.day.app/b", id = "bark_b", name = "备用机", boundAppPackages = "com.tencent.mm")
        )
        val settings = AppSettings(primaryChannelId = "bark_a", multiChannelSend = false)
        val rule = RuleEntity(
            packageName = "com.tencent.mm",
            appName = "微信",
            templateId = "wechat",
            barkTargetIds = "bark_b",
            useIndependentBarkRoute = true,
            barkSound = "bell",
            barkIconUrl = "https://example.com/icon.png"
        )
        val route = PerAppRouteResolver.resolve(channels, settings, rule)
        assertTrue(route is RouteResolution.Targets)
        val targets = (route as RouteResolution.Targets).channels
        assertEquals(listOf("备用机"), targets.map(ChannelConfig::name))
        assertEquals("", targets.single().sound)
        assertEquals("", targets.single().icon)
    }

    @Test fun `simple template presets render without custom variables`() {
        val body = TemplateCatalog.byId(TemplateCatalog.STANDARD_ID).template().renderBody(message)
        assertEquals("短信", TemplateCatalog.byId(TemplateCatalog.STANDARD_ID).template().renderTitle(message))
        assertEquals("短信", TemplateCatalog.byId("privacy").template().renderTitle(message))
        assertTrue(body.contains("📝：内容：1234"))
        assertFalse(body.contains("应用："))
        assertFalse(body.contains("标题："))
        assertEquals(1, Regex("接收时间").findAll(body).count())
    }

    @Test fun `bark target appends sound and icon query parameters`() {
        val channel = ChannelConfig("bark", "https://api.day.app/token", sound = "minuet", icon = "https://example.com/icon 1.png")
        val url = ChannelSender.target(channel, "标题 1", "正文 2").first
        assertTrue(url.startsWith("https://api.day.app/token/%E6%A0%87%E9%A2%98%201/%E6%AD%A3%E6%96%87%202"))
        assertTrue(url.contains("sound=minuet"))
        assertTrue(url.contains("icon=https%3A%2F%2Fexample.com%2Ficon+1.png"))
    }

    @Test fun `relay failures use custom channel names`() {
        val results = ChannelSender.sendRelay(
            "http://relay.example.com",
            "",
            listOf(ChannelConfig("bark", "https://api.day.app/a", id = "bark_a", name = "iPhone")),
            message,
            "标题",
            "正文"
        )
        assertEquals("iPhone", results.single().channel)
        assertEquals("中转地址必须使用 HTTPS", results.single().error)
    }

    @Test fun `time formatter uses local zone and chinese labels`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-06T10:00:00Z").toEpochMilli()
        assertEquals("今天 18:00", TimeFormatter.formatRecordListTime("2026-08-06T10:00:00Z", now, zone))
        assertEquals("昨天 18:42", TimeFormatter.formatRecordListTime("2026-08-05T10:42:00Z", now, zone))
        assertEquals("08月03日 15:30", TimeFormatter.formatRecordListTime("2026-08-03T07:30:00Z", now, zone))
        assertEquals("2025年12月31日 23:59", TimeFormatter.formatRecordListTime("2025-12-31T15:59:00Z", now, zone))
        assertEquals("2026-08-06 18:00:00", TimeFormatter.formatRecordDetailTime("2026-08-06T10:00:00Z", zone))
        assertEquals("2026-08-06 18:00:00 +08:00", TimeFormatter.formatLogTime("2026-08-06T10:00:00Z", zone))
        assertEquals("时间未知", TimeFormatter.formatRecordDetailTime("bad-time", zone))
    }

    @Test fun `screen off only policy allows and filters correctly`() {
        assertTrue(ScreenOffOnlyPolicy.decide(false) { lockState(false, false, Display.STATE_ON) } is ScreenOffOnlyDecision.Allow)
        assertTrue(ScreenOffOnlyPolicy.decide(true) { lockState(true, true, Display.STATE_ON) } is ScreenOffOnlyDecision.Allow)
        assertTrue(ScreenOffOnlyPolicy.decide(true) { lockState(false, true, Display.STATE_OFF) } is ScreenOffOnlyDecision.Allow)
        assertTrue(ScreenOffOnlyPolicy.decide(true) { lockState(false, true, Display.STATE_DOZE) } is ScreenOffOnlyDecision.Allow)
        assertTrue(ScreenOffOnlyPolicy.decide(true) { lockState(null, null, Display.STATE_UNKNOWN) } is ScreenOffOnlyDecision.Allow)
        val filtered = ScreenOffOnlyPolicy.decide(true) { lockState(false, false, Display.STATE_ON) }
        assertTrue(filtered is ScreenOffOnlyDecision.Filter)
        assertEquals(LockDecision.FILTER, (filtered as ScreenOffOnlyDecision.Filter).state.decision)
        assertTrue(ScreenOffOnlyPolicy.decide(true, object : LockStateProvider {
            override fun snapshot(): LockStateSnapshot = error("broken")
        }) is ScreenOffOnlyDecision.Allow)
    }

    @Test fun `call state tracker recognizes incoming events without duplicates`() {
        val missed = CallSessionTracker()
        assertEquals(CallEventType.INCOMING_RINGING, missed.onStateChanged(TelephonyManager.CALL_STATE_RINGING, "10086")?.eventType)
        assertNull(missed.onStateChanged(TelephonyManager.CALL_STATE_RINGING, "10086"))
        assertEquals(CallEventType.MISSED_CALL, missed.onStateChanged(TelephonyManager.CALL_STATE_IDLE, "10086")?.eventType)

        val answered = CallSessionTracker()
        assertEquals(CallEventType.INCOMING_RINGING, answered.onStateChanged(TelephonyManager.CALL_STATE_RINGING, "10010")?.eventType)
        assertEquals(CallEventType.CALL_ANSWERED, answered.onStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "10010")?.eventType)
        assertNull(answered.onStateChanged(TelephonyManager.CALL_STATE_IDLE, "10010"))

        val outgoing = CallSessionTracker()
        assertNull(outgoing.onStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "10000"))
        assertNull(outgoing.onStateChanged(TelephonyManager.CALL_STATE_IDLE, "10000"))
    }

    @Test fun `call state tracker does not let blank number overwrite valid number`() {
        val tracker = CallSessionTracker()
        val ringing = tracker.onStateChanged(TelephonyManager.CALL_STATE_RINGING, "13800138000")
        assertEquals("13800138000", ringing?.number)
        assertNull(tracker.onStateChanged(TelephonyManager.CALL_STATE_RINGING, ""))
        val missed = tracker.onStateChanged(TelephonyManager.CALL_STATE_IDLE, "")
        assertEquals("13800138000", missed?.number)
    }

    @Test fun `sms duplicate guard suppresses notification echo only inside ttl`() {
        SmsDuplicateGuard.registerBroadcast("106900000000", "测试短信", 100_000)
        assertTrue(SmsDuplicateGuard.shouldSuppressNotification("com.android.messaging", "106900000000", "测试短信", 105_000))
        assertFalse(SmsDuplicateGuard.shouldSuppressNotification("com.android.messaging", "106900000000", "测试短信", 130_000))
        assertFalse(SmsDuplicateGuard.shouldSuppressNotification("com.android.messaging", "106900000001", "测试短信", 105_000))
        assertFalse(SmsDuplicateGuard.shouldSuppressNotification("com.android.messaging", "106900000000", "另一条短信", 105_000))
    }

    @Test fun `call screening store keeps recent realtime number`() {
        RealtimeIncomingCallStore.put("13900139000", 200_000)
        assertEquals("13900139000", RealtimeIncomingCallStore.recent(202_000)?.phoneNumber)
        assertNull(RealtimeIncomingCallStore.recent(210_000))
    }

    @Test fun `call event type parsing keeps defaults for old data`() {
        assertEquals(CallEventTypes.default, CallEventTypes.parse(""))
        assertEquals(setOf(CallEventType.CALL_ANSWERED), CallEventTypes.parse("CALL_ANSWERED"))
        assertTrue(CallEventTypes.isValid("MISSED_CALL,INCOMING_RINGING"))
    }

    private fun lockState(keyguardLocked: Boolean?, deviceLocked: Boolean?, displayState: Int?) =
        LockStateSnapshot(
            isKeyguardLocked = keyguardLocked,
            isDeviceLocked = deviceLocked,
            displayState = displayState,
            isInteractive = displayState == Display.STATE_ON,
            capturedAt = 1_000,
            decision = if (keyguardLocked == false && deviceLocked == false && displayState == Display.STATE_ON) LockDecision.FILTER else LockDecision.ALLOW,
            reason = if (keyguardLocked == false && deviceLocked == false && displayState == Display.STATE_ON) "已解锁" else "允许"
        )
}
