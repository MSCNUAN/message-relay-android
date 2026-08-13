package io.github.messagerelay

data class ReleaseNote(
    val version: String,
    val date: String,
    val added: List<String>,
    val improved: List<String>,
    val fixed: List<String>
)

object ReleaseNotes {
    fun all(currentVersion: String): List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "v$currentVersion",
            date = "2026年8月",
            added = listOf(
                "Bark 渠道支持绑定 App",
                "软件选择中新增每个 App 的设置入口",
                "SIM 卡管理显示权限、设备能力和可读取到的 SIM 状态"
            ),
            improved = listOf(
                "仅息屏时推送入口更清晰",
                "首页快捷卡片改为大字体更稳定的单列布局",
                "覆盖更新时自动修复首次配置状态和主渠道指向"
            ),
            fixed = listOf(
                "修复覆盖安装后可能重新进入首次配置的问题",
                "修复渠道解密失败时可能崩溃或表现为渠道丢失的问题",
                "修复 Manifest、默认模板、过滤原因和错误提示中的中文乱码"
            )
        ),
        ReleaseNote(
            version = "v0.1.2",
            date = "2026年8月",
            added = listOf(
                "新增 GitHub 开源项目入口",
                "新增版本更新页面",
                "支持手动检查 GitHub Releases",
                "支持可关闭的自动检查更新",
                "发现新版本后可前往 GitHub 下载"
            ),
            improved = listOf(
                "关于页面新增开源项目、开发方式和 GPL-3.0-only 许可信息",
                "更新日志新增 GitHub Releases 入口",
                "使用教程补充版本更新说明"
            ),
            fixed = listOf(
                "修复版本比较不能正确处理 0.10.0 和 0.9.0 的问题",
                "避免更新检查失败影响核心消息转发流程"
            )
        ),
        ReleaseNote(
            version = "v0.1.1",
            date = "2026年8月",
            added = listOf(
                "保留全局多 Bark 设备配置，支持名称、声音、图标、分组、提醒等级和持续响铃参数",
                "保留每应用仅息屏时推送和电话通知类型设置"
            ),
            improved = listOf(
                "收缩复杂入口，降低普通用户误配概率",
                "旧备份和旧数据库字段继续兼容读取"
            ),
            fixed = listOf(
                "修复旧规则中独立 Bark 字段可能继续影响路由的风险",
                "修复旧备份导入后可能重新启用应用独立 Bark 的风险",
                "修复发布文档继续显示已移除功能的问题"
            )
        ),
        ReleaseNote(
            version = "v0.1.0",
            date = "2026年8月",
            added = listOf(
                "首页、记录、设置三页结构",
                "Bark、飞书、钉钉推送渠道",
                "来源应用选择、模板预设、免打扰、记录和备份恢复",
                "每应用仅息屏时推送、电话通知类型和 SIM 管理",
                "深色、浅色、跟随系统主题"
            ),
            improved = listOf(
                "首页改为更适合小白的配置状态与快捷入口",
                "链接行只显示名称，避免小屏幕 URL 截断",
                "时间显示统一为本地中文格式"
            ),
            fixed = listOf(
                "修复模板变量正则错误",
                "修复已过滤记录不可用的问题",
                "修复部分深色模式对比不足的问题"
            )
        )
    )
}
