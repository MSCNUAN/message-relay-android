package io.github.messagerelay

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Round2QaSeedTest {
    @Test
    fun seedRound2QaData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = AppSettingsRepository(context)
            val dao = RelayDatabase.get(context).relayDao()
            val now = System.currentTimeMillis()
            val bark = ChannelConfig(
                type = "bark",
                url = "https://api.day.app/testtoken",
                id = "bark_a",
                name = "Bark 1"
            )

            dao.ensureTemplates(repository.current())
            dao.deleteAllRecords()
            dao.deleteAllRules()
            dao.saveRule(RuleEntity("com.sms", "短信", "验证码", templateId = TemplateCatalog.STANDARD_ID))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "验证码", body = "123456", status = "成功", channelResults = """[{"channel":"Bark 1","success":true}]""", createdAt = now - 3_000))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "发送失败", body = "网络错误", status = "发送失败", channelResults = """[{"channel":"Bark 1","success":false,"error":"网络错误"}]""", createdAt = now - 2_000))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "广告短信", body = "促销内容", status = "已过滤", channelResults = """[{"reason":"规则未命中或命中排除关键词"}]""", createdAt = now - 1_000))
            dao.addRecord(DeliveryRecord(packageName = "com.sms", app = "短信", title = "测试口令", body = "本条为 QA 测试通知 ABCD", status = "成功", channelResults = """[{"channel":"Bark 1","success":true}]""", createdAt = now))

            repository.setPaused(false)
            repository.setThemeMode("light")
            repository.setOnboardingComplete(true)
            repository.setPrimaryChannelId(bark.id)
            repository.setSelectedTemplatePreset(TemplateCatalog.STANDARD_ID)
            repository.setQuiet(true, "23:00", "08:00", "验证码\n来电\n未接来电")
            repository.setHistoryRetention("30")
            repository.setAdvancedAcknowledged(false)
            val serializedChannels = ChannelSender.serialize(listOf(bark))
            check(serializedChannels.contains("bark_a")) { "QA channel serialization failed: $serializedChannels" }
            SecureStore(context).put("channels", serializedChannels)
            check(SecureStore(context).get("channels")?.contains("bark_a") == true) { "QA channel secure store readback failed" }
        }
    }
}
