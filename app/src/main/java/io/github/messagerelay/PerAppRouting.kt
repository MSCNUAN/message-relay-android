package io.github.messagerelay

import java.net.URI

enum class BarkIconMode {
    NONE,
    BUILT_IN_URL,
    CUSTOM_URL
}

sealed class RouteResolution {
    data class Targets(val channels: List<ChannelConfig>) : RouteResolution()
    data class Failure(val message: String) : RouteResolution()
}

object PerAppRouteResolver {
    const val BUILT_IN_ICON_URL = "https://www.nuan1145.eu.cc/favicon.ico"

    fun resolve(
        channels: List<ChannelConfig>,
        settings: AppSettings,
        rule: RuleEntity?,
        manualOrTest: Boolean = false
    ): RouteResolution {
        val normalized = ChannelSelection.normalized(channels)
        val globalTargets = if (settings.multiChannelSend) {
            ChannelSelection.enabled(normalized)
        } else {
            ChannelSelection.primaryEnabled(normalized, settings.primaryChannelId)
        }
        if (manualOrTest || rule == null) return RouteResolution.Targets(globalTargets)

        val boundBark = normalized
            .filter { it.type == "bark" && it.enabled && rule.packageName in it.boundPackages() }
        val hasAnyBindingForApp = normalized.any { it.type == "bark" && rule.packageName in it.boundPackages() }
        if (!hasAnyBindingForApp) return RouteResolution.Targets(globalTargets)

        val nonBarkGlobal = globalTargets.filterNot { it.type == "bark" }
        val targets = nonBarkGlobal + boundBark
        return if (targets.isEmpty()) {
            RouteResolution.Failure("已为 ${rule.appName} 绑定 Bark，但绑定的 Bark 已停用或失效")
        } else {
            RouteResolution.Targets(targets.distinctBy(ChannelConfig::id))
        }
    }

    fun validateIcon(mode: BarkIconMode, iconUrl: String): String {
        val trimmed = iconUrl.trim()
        return when (mode) {
            BarkIconMode.NONE, BarkIconMode.BUILT_IN_URL -> ""
            BarkIconMode.CUSTOM_URL -> if (trimmed.isBlank()) {
                ""
            } else if (runCatching { URI(trimmed).scheme in setOf("http", "https") && !URI(trimmed).host.isNullOrBlank() }.getOrDefault(false)) {
                ""
            } else {
                "图标 URL 必须是有效的 http/https 地址"
            }
        }
    }

    fun parseIconMode(value: String): BarkIconMode =
        BarkIconMode.entries.firstOrNull { it.name == value } ?: BarkIconMode.NONE
}
