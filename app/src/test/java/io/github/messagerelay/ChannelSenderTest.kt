package io.github.messagerelay

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class ChannelSenderTest {
    private lateinit var server: MockWebServer
    private lateinit var originalFactory: SSLSocketFactory
    private lateinit var originalVerifier: HostnameVerifier

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory(), false); start() }
        originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultSSLSocketFactory(clientCertificates.sslSocketFactory())
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }

    @After fun cleanup() {
        HttpsURLConnection.setDefaultSSLSocketFactory(originalFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(originalVerifier)
        server.shutdown()
    }

    @Test fun `direct sender returns successful structured result`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = ChannelSender.send(ChannelConfig("feishu", server.url("/hook").toString()), "标题", "正文")
        assertTrue(result.success)
        assertEquals(200, result.httpStatus)
        assertFalse(result.retryable)
        assertTrue(server.takeRequest().body.readUtf8().contains("标题"))
    }

    @Test fun `direct sender marks 429 retryable`() {
        server.enqueue(MockResponse().setResponseCode(429))
        val result = ChannelSender.send(ChannelConfig("dingtalk", server.url("/hook").toString()), "标题", "正文")
        assertFalse(result.success)
        assertEquals(429, result.httpStatus)
        assertTrue(result.retryable)
    }

    @Test fun `bark http success still checks api code`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":400,"message":"bad device key"}"""))
        val result = ChannelSender.send(ChannelConfig("bark", server.url("/token").toString()), "标题", "正文")
        assertFalse(result.success)
        assertEquals(200, result.httpStatus)
        assertTrue(result.error.orEmpty().contains("Bark 返回错误"))
    }

    @Test fun `feishu http success still checks response code`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":9499,"msg":"sign error"}"""))
        val result = ChannelSender.send(ChannelConfig("feishu", server.url("/hook").toString()), "标题", "正文")
        assertFalse(result.success)
        assertEquals(200, result.httpStatus)
        assertTrue(result.error.orEmpty().contains("飞书返回错误"))
    }
}
