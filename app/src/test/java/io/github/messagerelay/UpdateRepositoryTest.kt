package io.github.messagerelay

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After fun cleanup() {
        server.shutdown()
    }

    @Test fun `version comparator handles v prefix and suffixes`() {
        assertTrue(VersionComparator.isNewer("v0.1.1", "0.1.0"))
        assertTrue(VersionComparator.isNewer("v0.10.0", "v0.9.0"))
        assertTrue(VersionComparator.isNewer("v1.0.0", "v0.9.9"))
        assertEquals(0, VersionComparator.compare("v0.1.0-debug", "0.1.0"))
        assertEquals(0, VersionComparator.compare("v0.1.0-beta", "v0.1.0-rc1"))
        assertTrue(VersionComparator.compare("0.1.0", "0.1.1") < 0)
    }

    @Test fun `repository finds newer release and sends github headers`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {
                    "tag_name": "v0.1.2",
                    "name": "v0.1.2",
                    "body": "新增更新检查",
                    "html_url": "https://github.com/release",
                    "published_at": "2026-08-10T00:00:00Z",
                    "prerelease": false,
                    "draft": false,
                    "assets": []
                  }
                ]
                """.trimIndent()
            )
        )
        val result = UpdateRepository(server.url("/releases").toString()).check("0.1.1", includePrerelease = false)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("v0.1.2", (result as UpdateCheckResult.UpdateAvailable).release.tagName)
        assertEquals("新增更新检查", result.release.body)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
    }

    @Test fun `repository ignores drafts and reports latest when no newer usable release`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"tag_name":"v9.9.9","html_url":"https://github.com/draft","prerelease":false,"draft":true,"assets":[]},
                  {"tag_name":"v0.1.1","html_url":"https://github.com/current","prerelease":false,"draft":false,"assets":[]}
                ]
                """.trimIndent()
            )
        )
        val result = UpdateRepository(server.url("/releases").toString()).check("0.1.1", includePrerelease = false)
        assertTrue(result is UpdateCheckResult.Latest)
    }

    @Test fun `debug builds can detect prerelease while release builds ignore it`() {
        val body = """
            [
              {"tag_name":"v0.1.2-debug","html_url":"https://github.com/pre","prerelease":true,"draft":false,"assets":[]}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        assertTrue(UpdateRepository(server.url("/debug").toString()).check("0.1.1", includePrerelease = true) is UpdateCheckResult.UpdateAvailable)
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        assertTrue(UpdateRepository(server.url("/release").toString()).check("0.1.1", includePrerelease = false) is UpdateCheckResult.Latest)
    }

    @Test fun `repository handles empty malformed and http errors safely`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertTrue(UpdateRepository(server.url("/empty").toString()).check("0.1.1", includePrerelease = true) is UpdateCheckResult.Latest)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{bad json"))
        assertTrue(UpdateRepository(server.url("/bad").toString()).check("0.1.1", includePrerelease = true) is UpdateCheckResult.Error)

        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val notFound = UpdateRepository(server.url("/missing").toString()).check("0.1.1", includePrerelease = true)
        assertTrue(notFound is UpdateCheckResult.Error)
        assertEquals("HTTP 404", (notFound as UpdateCheckResult.Error).detail)

        server.enqueue(MockResponse().setResponseCode(403).setBody("API rate limit exceeded"))
        val limited = UpdateRepository(server.url("/limited").toString()).check("0.1.1", includePrerelease = true)
        assertTrue(limited is UpdateCheckResult.Error)
        assertTrue((limited as UpdateCheckResult.Error).message.contains("限制"))
    }
}
