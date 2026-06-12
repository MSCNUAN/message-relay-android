package io.github.messagerelay

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class CoreLogicTest {
    private val message = RelayMessage("com.sms", "短信", "验证码", "1234", 0)

    @Test fun `filter requires allowed app and keywords`() {
        val rule = RelayRule(setOf("com.sms"), listOf("验证"), listOf("广告"))
        assertTrue(rule.matches(message))
        assertFalse(rule.matches(message.copy(body = "广告验证码")))
    }

    @Test fun `quiet hours can cross midnight and urgent keyword bypasses`() {
        val quiet = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0), listOf("紧急"))
        assertTrue(quiet.shouldQueue(message, LocalTime.of(23, 0)))
        assertTrue(quiet.shouldQueue(message, LocalTime.of(22, 0)))
        assertFalse(quiet.shouldQueue(message, LocalTime.of(7, 0)))
        assertFalse(quiet.shouldQueue(message.copy(title = "紧急验证码"), LocalTime.of(23, 0)))
    }

    @Test fun `template rejects unknown variables`() {
        assertEquals("[短信] 验证码", MessageTemplate().renderTitle(message))
        assertThrows(IllegalArgumentException::class.java) { MessageTemplate("{{bad}}", "{{body}}").renderTitle(message) }
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
            setOf("general", "phone", "sms", "wechat", "work_wechat", "qq", "dingtalk", "feishu"),
            TemplateCatalog.builtIns.map { it.id }.toSet()
        )
        assertEquals("来电提醒：验证码", TemplateCatalog.byId("phone").template().renderTitle(message))
        assertEquals("短信：验证码", TemplateCatalog.byId("sms").template().renderTitle(message))
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

    @Test fun `only first enabled channel is retained`() {
        val channels = listOf(
            ChannelConfig("dingtalk", "https://oapi.dingtalk.com/a", enabled = false),
            ChannelConfig("feishu", "https://open.feishu.cn/a"),
            ChannelConfig("bark", "https://api.day.app/a")
        )
        assertEquals("feishu", ChannelSelection.singleEnabled(channels).single().type)
        assertEquals(1, ChannelSender.parse(ChannelSender.serialize(channels)).size)
    }
}
