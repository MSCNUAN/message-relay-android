package io.github.messagerelay

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long,
    val contentType: String
)

data class GitHubRelease(
    val tagName: String,
    val name: String?,
    val body: String?,
    val htmlUrl: String,
    val publishedAt: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val currentVersion: String, val release: GitHubRelease) : UpdateCheckResult
    data class Latest(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String, val detail: String = "") : UpdateCheckResult
}

object VersionComparator {
    fun compare(left: String, right: String): Int {
        val a = normalize(left)
        val b = normalize(right)
        val max = maxOf(a.size, b.size)
        for (index in 0 until max) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun normalize(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

class UpdateRepository(
    private val apiUrl: String = GITHUB_RELEASES_API
) {
    fun check(currentVersion: String, includePrerelease: Boolean): UpdateCheckResult = runCatching {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val status = connection.responseCode
        val text = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (status == 403 && text.contains("rate limit", ignoreCase = true)) {
            return@runCatching UpdateCheckResult.Error("GitHub 请求次数暂时达到限制，请稍后再试", "HTTP 403")
        }
        if (status !in 200..299) {
            return@runCatching UpdateCheckResult.Error("检查更新失败，请检查网络后重试", "HTTP $status")
        }
        val releases = parseReleases(text)
            .filterNot(GitHubRelease::draft)
            .filter { includePrerelease || !it.prerelease }
            .sortedWith { a, b -> VersionComparator.compare(b.tagName, a.tagName) }
        val latest = releases.firstOrNull { VersionComparator.isNewer(it.tagName, currentVersion) }
        if (latest != null) {
            UpdateCheckResult.UpdateAvailable(currentVersion, latest)
        } else {
            UpdateCheckResult.Latest(currentVersion)
        }
    }.getOrElse {
        UpdateCheckResult.Error("检查更新失败，请检查网络后重试", it.javaClass.simpleName)
    }

    fun parseReleases(raw: String): List<GitHubRelease> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val assets = item.optJSONArray("assets") ?: JSONArray()
            GitHubRelease(
                tagName = item.optString("tag_name"),
                name = item.optString("name").takeIf(String::isNotBlank),
                body = item.optString("body").takeIf(String::isNotBlank),
                htmlUrl = item.optString("html_url").ifBlank { GITHUB_RELEASES_URL },
                publishedAt = item.optString("published_at").takeIf(String::isNotBlank),
                prerelease = item.optBoolean("prerelease", false),
                draft = item.optBoolean("draft", false),
                assets = (0 until assets.length()).map { assetIndex ->
                    val asset = assets.getJSONObject(assetIndex)
                    ReleaseAsset(
                        name = asset.optString("name"),
                        browserDownloadUrl = asset.optString("browser_download_url"),
                        size = asset.optLong("size", 0),
                        contentType = asset.optString("content_type")
                    )
                }
            )
        }
    }
}

const val GITHUB_REPOSITORY_URL = "https://github.com/MSCNUAN/message-relay-android"
const val GITHUB_RELEASES_URL = "https://github.com/MSCNUAN/message-relay-android/releases"
const val GITHUB_ISSUES_URL = "https://github.com/MSCNUAN/message-relay-android/issues"
const val GITHUB_RELEASES_API = "https://api.github.com/repos/MSCNUAN/message-relay-android/releases?per_page=10"
const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
