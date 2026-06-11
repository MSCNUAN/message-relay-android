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
}
