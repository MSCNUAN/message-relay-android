package io.github.messagerelay

import android.content.ClipData
import android.content.ClipboardManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private val Indigo = Color(0xFF5368FF)
private val Success = Color(0xFF119B75)
private val Warning = Color(0xFFE6A500)
private val Danger = Color(0xFFD92D20)

private data class UiColors(
    val ink: Color,
    val muted: Color,
    val page: Color,
    val card: Color,
    val border: Color
)

private val LightUi = UiColors(
    ink = Color(0xFF17203B),
    muted = Color(0xFF667085),
    page = Color(0xFFF7F8FF),
    card = Color.White,
    border = Color(0xFFE6E9F5)
)

private val DarkUi = UiColors(
    ink = Color(0xFFE8ECF8),
    muted = Color(0xFFB8C0D8),
    page = Color(0xFF101525),
    card = Color(0xFF182033),
    border = Color(0xFF303A55)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MessageRelayApp {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Outlined.Home),
    Records("记录", Icons.Outlined.History),
    Settings("设置", Icons.Outlined.Settings)
}

private enum class SubPage {
    PushChannels,
    AppSelection,
    TemplatePresets,
    QuietHours,
    BackgroundHealth,
    BackupRestore,
    Manual,
    Advanced,
    AdvancedRules,
    AdvancedTemplates,
    SimManagement,
    AppRuleSettings,
    VersionUpdate,
    About
}

@Composable
fun MessageRelayApp(openPermission: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppSettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val scheme = if (dark) {
        darkColorScheme(primary = Indigo, background = Color(0xFF101525), surface = Color(0xFF182033), onBackground = Color(0xFFE8ECF8))
    } else {
        lightColorScheme(primary = Indigo, background = LightUi.page, surface = LightUi.card, onBackground = LightUi.ink)
    }
    val colors = if (dark) DarkUi else LightUi
    var tab by remember { mutableStateOf(MainTab.Home) }
    var subPage by remember { mutableStateOf<SubPage?>(null) }
    var editingApp by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        val dao = RelayDatabase.get(context).relayDao()
        dao.ensureTemplates(repository.current())
        reconcileUpgradeState(context, repository, dao)
    }
    LaunchedEffect(settings.onboardingComplete, settings.autoCheckUpdates, settings.lastUpdateCheckAt) {
        if (settings.onboardingComplete && settings.autoCheckUpdates) {
            val now = System.currentTimeMillis()
            if (now - settings.lastUpdateCheckAt >= UPDATE_CHECK_INTERVAL_MS) {
                withContext(Dispatchers.IO) {
                    UpdateRepository().check(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
                }
                repository.setLastUpdateCheckAt(now)
            }
        }
    }

    MaterialTheme(colorScheme = scheme) {
        if (!settings.onboardingComplete) {
            Onboarding(openPermission, repository, colors)
            return@MaterialTheme
        }
        BackHandler(enabled = subPage != null) { subPage = null }
        Scaffold(
            containerColor = colors.page,
            bottomBar = {
                if (subPage == null) {
                    NavigationBar(containerColor = colors.card) {
                        MainTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (subPage) {
                SubPage.PushChannels -> PushChannelScreen(modifier, settings, repository, colors)
                SubPage.AppSelection -> SimpleAppSelectionScreen(modifier, settings, repository, colors) { app ->
                    editingApp = app
                    subPage = SubPage.AppRuleSettings
                }
                SubPage.TemplatePresets -> SimpleTemplatePresetScreen(modifier, settings, repository, colors)
                SubPage.QuietHours -> QuietHoursScreen(modifier, settings, repository, colors)
                SubPage.BackgroundHealth -> BackgroundHealthScreen(modifier, openPermission, colors)
                SubPage.BackupRestore -> BackupRestoreScreen(modifier, colors)
                SubPage.Manual -> UserManualScreen(modifier, colors)
                SubPage.Advanced -> AdvancedSettingsScreen(
                    modifier = modifier,
                    settings = settings,
                    repository = repository,
                    colors = colors,
                    onOpenRules = { subPage = SubPage.AdvancedRules },
                    onOpenTemplates = { subPage = SubPage.AdvancedTemplates }
                )
                SubPage.AdvancedRules -> Rules(modifier, colors)
                SubPage.AdvancedTemplates -> PageScaffold("自定义消息模板", "高级用户可编辑模板变量。", modifier, colors) { TemplateLibrary(colors) }
                SubPage.SimManagement -> SimManagementScreen(modifier, colors)
                SubPage.AppRuleSettings -> editingApp?.let { AppRuleSettingsScreen(modifier, it.first, it.second, settings, colors) }
                    ?: SimpleAppSelectionScreen(modifier, settings, repository, colors) { app ->
                        editingApp = app
                        subPage = SubPage.AppRuleSettings
                    }
                SubPage.VersionUpdate -> VersionUpdateScreen(modifier, settings, repository, colors)
                SubPage.About -> AboutMessageRelayScreen(modifier, colors)
                null -> when (tab) {
                    MainTab.Home -> Home(
                        modifier = modifier,
                        settings = settings,
                        repository = repository,
                        openPermission = openPermission,
                        colors = colors,
                        onOpenChannel = { subPage = SubPage.PushChannels },
                        onOpenApps = { subPage = SubPage.AppSelection },
                        onOpenTemplates = { subPage = SubPage.TemplatePresets },
                        onOpenQuiet = { subPage = SubPage.QuietHours },
                        onOpenBackground = { subPage = SubPage.BackgroundHealth },
                        onOpenBackup = { subPage = SubPage.BackupRestore },
                        onOpenRecords = { tab = MainTab.Records }
                    )
                    MainTab.Records -> Records(modifier, colors) { subPage = SubPage.AdvancedRules }
                    MainTab.Settings -> SettingsHub(
                        modifier = modifier,
                        settings = settings,
                        repository = repository,
                        colors = colors,
                        onOpenManual = { subPage = SubPage.Manual },
                        onOpenChannel = { subPage = SubPage.PushChannels },
                        onOpenApps = { subPage = SubPage.AppSelection },
                        onOpenTemplates = { subPage = SubPage.TemplatePresets },
                        onOpenQuiet = { subPage = SubPage.QuietHours },
                        onOpenBackground = { subPage = SubPage.BackgroundHealth },
                        onOpenBackup = { subPage = SubPage.BackupRestore },
                        onOpenSimManagement = { subPage = SubPage.SimManagement },
                        onOpenAdvanced = { subPage = SubPage.Advanced },
                        onOpenVersionUpdate = { subPage = SubPage.VersionUpdate },
                        onOpenAbout = { subPage = SubPage.About }
                    )
                }
            }
        }
    }
}

@Composable
private fun Onboarding(openPermission: () -> Unit, repository: AppSettingsRepository, colors: UiColors) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val apps = remember { loadInstalledApps(context) }
    var step by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var manualPackage by remember { mutableStateOf("") }
    var selectedSources by remember { mutableStateOf(emptyList<SourceSelection>()) }
    var selectedChannel by remember { mutableStateOf("bark") }
    var dingtalk by remember { mutableStateOf("") }
    var dingSecret by remember { mutableStateOf("") }
    var feishu by remember { mutableStateOf("") }
    var feiSecret by remember { mutableStateOf("") }
    var bark by remember { mutableStateOf("") }
    var testPassed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    PageScaffold("四步完成首次配置", "按顺序完成权限、来源、渠道和测试。", colors = colors) {
        StatusBadge("第 ${step + 1} 步，共 4 步", Indigo, colors)
        Spacer(Modifier.height(12.dp))
        when (step) {
            0 -> SectionCard("开启通知访问", "只读取你选择来源 App 的通知，并在本机完成筛选后转发。", Icons.Outlined.Notifications, colors) {
                PrimaryAction("打开通知访问设置", colors, onClick = openPermission)
            }
            1 -> SourceSelectionCard(apps, search, { search = it }, manualPackage, { manualPackage = it }, selectedSources, { selectedSources = it }, colors)
            2 -> SectionCard("渠道参数", "Webhook 必须使用 HTTPS。", Icons.Outlined.Notifications, colors) {
                ChannelChoice(selectedChannel, { selectedChannel = it }, colors)
                SelectedChannelFields(selectedChannel, dingtalk, { dingtalk = it }, dingSecret, { dingSecret = it }, feishu, { feishu = it }, feiSecret, { feiSecret = it }, bark, { bark = it }, colors)
            }
            else -> SectionCard("发送测试消息", "至少一个渠道测试成功后才能完成配置。", Icons.Outlined.CheckCircle, colors) {
                PrimaryAction("发送测试", colors) {
                    scope.launch {
                        val channels = selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret)
                        SecureStore(context).put("channels", ChannelSender.serialize(channels))
                        val results = withContext(Dispatchers.IO) { channels.map { ChannelSender.send(it, "消息接力测试", "渠道配置成功") } }
                        testPassed = results.any(DeliveryResult::success)
                        status = if (testPassed) "测试成功，可以完成配置" else results.firstOrNull()?.error ?: "请先配置渠道"
                    }
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    StatusBadge(status, if (testPassed) Success else Danger, colors)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scope.launch {
                    if (step == 1) {
                        dao.saveRules(selectedSources.map {
                            RuleEntity(it.packageName, it.appName, defaultIncludesForTemplate(it.templateId), templateId = it.templateId)
                        })
                    }
                    if (step == 2) SecureStore(context).put("channels", ChannelSender.serialize(selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret)))
                    if (step < 3) step++ else repository.setOnboardingComplete(true)
                }
            },
            enabled = when (step) {
                1 -> selectedSources.isNotEmpty()
                2 -> selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret).isNotEmpty()
                3 -> testPassed
                else -> true
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text(if (step == 3) "完成配置" else "继续", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Home(
    modifier: Modifier,
    settings: AppSettings,
    repository: AppSettingsRepository,
    openPermission: () -> Unit,
    colors: UiColors,
    onOpenChannel: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenQuiet: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val since = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val count by dao.recordCountSince(since).collectAsState(initial = 0)
    val queued by dao.queuedCount().collectAsState(initial = 0)
    val records by dao.records().collectAsState(initial = emptyList())
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    val channels = ChannelSelection.normalized(storedChannels(context))
    val primaryChannels = ChannelSelection.primaryEnabled(channels, settings.primaryChannelId)
    val ready = primaryChannels.isNotEmpty() && rules.isNotEmpty() && settings.selectedTemplatePreset.isNotBlank()

    PageScaffold("消息接力", "简单模式优先，按状态卡逐项修复。", modifier, colors) {
        SectionCard("运行状态", null, Icons.Outlined.PlayCircle, colors) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if (settings.paused) "转发应用通知已关闭" else "转发应用通知已开启", color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("今日已接力 $count 条 · 待发送 $queued 条", color = colors.muted)
                }
                Switch(!settings.paused, onCheckedChange = { scope.launch { repository.setPaused(!it) } })
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(if (ready) "首次配置进度" else "需要修复配置", if (ready) "关键配置已完成。" else "按下面三项完成基础配置。", Icons.Outlined.CheckCircle, colors) {
            SetupStep("1. 推送渠道", primaryChannels.isNotEmpty(), onOpenChannel, colors)
            SetupStep("2. 软件选择", rules.isNotEmpty(), onOpenApps, colors)
            SetupStep("3. 消息模板", settings.selectedTemplatePreset.isNotBlank(), onOpenTemplates, colors)
            OutlinedButton(onClick = openPermission, modifier = Modifier.fillMaxWidth()) { Text("检查通知访问权限") }
        }
        Spacer(Modifier.height(12.dp))
        FeatureCard("推送渠道", primaryChannels.firstOrNull()?.name ?: "待配置", Icons.Outlined.Notifications, Modifier.fillMaxWidth(), onOpenChannel, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("软件选择", if (rules.isEmpty()) "待选择" else "已选择 ${rules.size} 个", Icons.Outlined.List, Modifier.fillMaxWidth(), onOpenApps, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("消息模板", simpleTemplateName(settings.selectedTemplatePreset), Icons.Outlined.CheckCircle, Modifier.fillMaxWidth(), onOpenTemplates, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("免打扰", if (settings.quietEnabled) "${settings.quietStart}-${settings.quietEnd}" else "未开启", Icons.Outlined.Schedule, Modifier.fillMaxWidth(), onOpenQuiet, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("后台运行", "权限与保活检查", Icons.Outlined.PlayCircle, Modifier.fillMaxWidth(), onOpenBackground, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("记录保存", retentionLabel(settings.historyRetention), Icons.Outlined.History, Modifier.fillMaxWidth(), onOpenRecords, colors)
        Spacer(Modifier.height(10.dp))
        FeatureCard("备份与恢复", "导出配置", Icons.Outlined.CheckCircle, Modifier.fillMaxWidth(), onOpenBackup, colors)
        Spacer(Modifier.height(12.dp))
        SectionCard("最近记录", null, Icons.Outlined.History, colors) {
            records.take(5).forEach { RecordLine("${it.app} · ${it.status}", it.title, colors) }
            if (records.isEmpty()) EmptyText("暂无记录", colors)
        }
    }
}

@Composable
private fun Records(modifier: Modifier, colors: UiColors, onAdjustRules: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val records by dao.records().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf<DeliveryRecord?>(null) }
    val visible = when (tab) {
        "成功" -> records.filter { it.status == "成功" }
        "失败" -> records.filter { it.status != "成功" && it.status != "已过滤" }
        "已过滤" -> records.filter { it.status == "已过滤" }
        else -> records
    }
    PageScaffold("记录", "查看全部、成功、失败和已过滤消息。", modifier, colors) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("全部", "成功", "失败", "已过滤").forEach { item ->
                OutlinedButton(
                    onClick = { tab = item },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) { Text(item, fontSize = 13.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(12.dp))
        visible.forEach { record ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selected = record },
                colors = CardDefaults.outlinedCardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("${record.app} · ${record.status}", color = colors.ink, fontWeight = FontWeight.Bold)
                    Text(record.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(TimeFormatter.formatRecordListTime(record.createdAt), color = colors.muted, fontSize = 12.sp)
                }
            }
        }
        if (visible.isEmpty()) EmptyText("暂无发送记录", colors)
    }
    selected?.let { record ->
        RecordDetailDialog(
            record = record,
            colors = colors,
            onDismiss = { selected = null },
            onRetry = { selected = null },
            onDelete = { scope.launch { dao.deleteRecord(record.id); selected = null } },
            onCopy = { copyToClipboard(context, "${record.title}\n${record.body}") },
            onAdjustRules = { selected = null; onAdjustRules() }
        )
    }
}

@Composable
private fun SettingsHub(
    modifier: Modifier,
    settings: AppSettings,
    repository: AppSettingsRepository,
    colors: UiColors,
    onOpenManual: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenQuiet: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSimManagement: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenVersionUpdate: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdvancedDialog by remember { mutableStateOf(false) }
    PageScaffold("设置", "常用功能放前面，高级功能集中管理。", modifier, colors) {
        SettingsGroup("基础设置", colors)
        SectionCard("外观", "默认跟随系统，也可以固定浅色或深色。", Icons.Outlined.Settings, colors) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    OutlinedButton(onClick = { scope.launch { repository.setThemeMode(mode) } }) { Text(label) }
                }
            }
        }
        SettingNavRow("推送渠道", "配置 Bark、飞书、钉钉，并选择主推送渠道。", Icons.Outlined.Notifications, onOpenChannel, colors)
        SettingNavRow("软件选择", "选择短信、电话、微信或其他 App。", Icons.Outlined.List, onOpenApps, colors)
        SettingNavRow("SIM 卡管理", "查看电话相关的 SIM 信息。", Icons.Outlined.Notifications, onOpenSimManagement, colors)
        SettingNavRow("消息模板", "选择简洁、标准、隐私或原始通知模板。", Icons.Outlined.CheckCircle, onOpenTemplates, colors)
        SettingNavRow("免打扰", if (settings.quietEnabled) "${settings.quietStart}-${settings.quietEnd}" else "未开启", Icons.Outlined.Schedule, onOpenQuiet, colors)
        SettingNavRow("后台运行", "检查通知访问、通知权限和后台保活。", Icons.Outlined.PlayCircle, onOpenBackground, colors)
        SettingNavRow("备份与恢复", "导出或恢复基础配置。", Icons.Outlined.CheckCircle, onOpenBackup, colors)
        SettingsGroup("高级设置", colors)
        SettingNavRow("高级设置区域", "关键词过滤、应用独立规则、自定义模板和多渠道同时发送。", Icons.Outlined.Tune, {
            if (settings.advancedAcknowledged) onOpenAdvanced() else showAdvancedDialog = true
        }, colors)
        SettingsGroup("帮助与关于", colors)
        SettingNavRow("使用教程", "第一次使用不知道怎么填？按步骤看这里。", Icons.Outlined.List, onOpenManual, colors)
        ChangelogSection(colors)
        SettingNavRow("版本更新", updateSubtitle(settings), Icons.Outlined.CheckCircle, onOpenVersionUpdate, colors)
        SettingNavRow("GitHub 项目", "源码、README、Release 与问题反馈", Icons.Outlined.CheckCircle, {
            openExternalLink(context, GITHUB_REPOSITORY_URL)
        }, colors)
        SettingNavRow("问题反馈", "在 GitHub Issues 反馈问题或建议", Icons.Outlined.Tune, {
            openExternalLink(context, GITHUB_ISSUES_URL)
        }, colors)
        SettingNavRow("关于消息接力", "版本、开源许可与开发信息", Icons.Outlined.CheckCircle, onOpenAbout, colors)
    }
    if (showAdvancedDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedDialog = false },
            title = { Text("进入高级设置？") },
            text = { Text("这里包含关键词过滤、自定义模板和多渠道发送等功能，错误配置可能影响消息转发。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.setAdvancedAcknowledged(true) }
                    showAdvancedDialog = false
                    onOpenAdvanced()
                }) { Text("继续进入") }
            },
            dismissButton = { TextButton(onClick = { showAdvancedDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PushChannelScreen(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository, colors: UiColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    var refreshKey by remember { mutableIntStateOf(0) }
    val existing = remember { ChannelSelection.normalized(storedChannels(context)) }
    var type by remember { mutableStateOf(existing.firstOrNull()?.type ?: "bark") }
    var name by remember { mutableStateOf(existing.firstOrNull()?.name ?: "Bark 1") }
    var url by remember { mutableStateOf(existing.firstOrNull()?.url.orEmpty()) }
    var secret by remember { mutableStateOf(existing.firstOrNull()?.secret.orEmpty()) }
    var sound by remember { mutableStateOf(existing.firstOrNull()?.sound.orEmpty()) }
    var icon by remember { mutableStateOf(existing.firstOrNull()?.icon.orEmpty()) }
    var status by remember { mutableStateOf("") }
    val secureStore = remember { SecureStore(context) }
    val unreadableChannels = secureStore.hasUnreadableValue("channels")
    @Suppress("UNUSED_VARIABLE")
    val currentRefresh = refreshKey
    val channels = ChannelSelection.normalized(storedChannels(context))
    PageScaffold("推送渠道", "简单模式可以保存多个配置，但只选择一个主推送渠道。", modifier, colors) {
        if (unreadableChannels) {
            SectionCard("渠道配置读取失败", "系统无法解密已保存的渠道。请重新保存渠道，或导入之前导出的备份。", Icons.Outlined.Tune, colors) {
                StatusBadge("需要重新保存渠道", Danger, colors)
            }
            Spacer(Modifier.height(12.dp))
        }
        SectionCard("主推送渠道", "切换主渠道不会删除其他配置。", Icons.Outlined.Notifications, colors) {
            channels.forEach { channel ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, color = colors.ink, fontWeight = FontWeight.Bold)
                        Text(channelName(channel.type), color = colors.muted, lineHeight = 18.sp)
                        if (channel.type == "bark" && channel.boundPackages().isNotEmpty()) {
                            Text("已绑定 ${channel.boundPackages().size} 个 App", color = Indigo, fontSize = 13.sp)
                        }
                    }
                    RadioButton(selected = settings.primaryChannelId == channel.id, onClick = { scope.launch { repository.setPrimaryChannelId(channel.id) } })
                }
            }
            if (channels.isEmpty()) EmptyText("还没有保存渠道，请先添加一个。", colors)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("添加或更新渠道", "Bark 可填写声音和图标 URL；图标 URL 需要 http/https。", Icons.Outlined.CheckCircle, colors) {
            ChannelChoice(type, { type = it }, colors)
            OutlinedTextField(name, { name = it }, label = { Text("渠道名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("${channelName(type)} 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (type != "bark") OutlinedTextField(secret, { secret = it }, label = { Text("签名密钥（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (type == "bark") {
                OutlinedTextField(sound, { sound = it }, label = { Text("Bark 消息声音（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(icon, { icon = it }, label = { Text("Bark 消息图标 URL（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            PrimaryAction("保存渠道", colors) {
                val existingSameId = channels.firstOrNull { it.type == type && it.url == url.trim() }
                val channel = ChannelConfig(
                    type,
                    url.trim(),
                    secret.trim(),
                    true,
                    id = existingSameId?.id.orEmpty(),
                    name = name.ifBlank { channelName(type) },
                    sound = sound.trim(),
                    icon = icon.trim(),
                    boundAppPackages = existingSameId?.boundAppPackages.orEmpty()
                ).normalized(channels.size)
                if (!ChannelValidation.isValid(channel)) {
                    status = "渠道地址或图标 URL 无效"
                } else {
                    val saved = ChannelSelection.normalized(channels.filterNot { it.id == channel.id } + channel)
                    SecureStore(context).put("channels", ChannelSender.serialize(saved))
                    scope.launch { repository.setPrimaryChannelId(channel.id) }
                    status = "已保存渠道"
                    refreshKey++
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { ChannelSender.send(ChannelConfig(type, url, secret, name = name, sound = sound, icon = icon), "消息接力测试", "简单模式渠道测试") }
                    status = if (result.success) "测试发送成功" else result.error ?: "测试发送失败"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("发送测试消息") }
            if (status.isNotBlank()) StatusBadge(status, if ("成功" in status || "保存" in status) Success else Danger, colors)
        }
        Spacer(Modifier.height(12.dp))
        channels.filter { it.type == "bark" }.forEach { bark ->
            BarkBindingCard(
                bark = bark,
                rules = rules,
                channels = channels,
                colors = colors,
                onSave = { updated ->
                    SecureStore(context).put("channels", ChannelSender.serialize(channels.map { if (it.id == updated.id) updated else it }))
                    status = "${updated.name} 的 App 绑定已保存"
                    refreshKey++
                }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BarkBindingCard(
    bark: ChannelConfig,
    rules: List<RuleEntity>,
    channels: List<ChannelConfig>,
    colors: UiColors,
    onSave: (ChannelConfig) -> Unit
) {
    var selected by remember(bark.id, bark.boundAppPackages) { mutableStateOf(bark.boundPackages()) }
    SectionCard("${bark.name} 绑定 App", "可选。绑定后这些 App 的 Bark 推送只发到这个 Bark。", Icons.Outlined.Notifications, colors) {
        if (rules.isEmpty()) {
            EmptyText("请先在“软件选择”里添加要转发的 App。", colors)
        } else {
            rules.forEach { rule ->
                val checked = rule.packageName in selected
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.appName, color = colors.ink, fontWeight = FontWeight.Medium)
                        Text(rule.packageName, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(checked = checked, onCheckedChange = { value ->
                        selected = if (value) selected + rule.packageName else selected - rule.packageName
                    })
                }
            }
            val duplicateBindings = channels.filter { it.type == "bark" && it.id != bark.id }
                .filter { other -> selected.any { it in other.boundPackages() } }
            if (duplicateBindings.isNotEmpty()) {
                Text("提示：同一个 App 可以绑定多个 Bark，会同时发送到这些 Bark。", color = colors.muted, lineHeight = 18.sp)
            }
            PrimaryAction("保存绑定 App", colors) {
                onSave(bark.copy(boundAppPackages = selected.sorted().joinToString("\n")))
            }
        }
    }
}

@Composable
private fun SimpleAppSelectionScreen(
    modifier: Modifier,
    settings: AppSettings,
    repository: AppSettingsRepository,
    colors: UiColors,
    onOpenAppSettings: (Pair<String, String>) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    val installedApps = remember { loadInstalledApps(context) }
    var search by remember { mutableStateOf("") }
    PageScaffold("软件选择", "推荐短信、电话、微信；其他 App 也可以手动选择。", modifier, colors) {
        SectionCard("推荐应用", "能识别到才会显示，避免不同手机包名不一致。", Icons.Outlined.CheckCircle, colors) {
            recommendedApps(installedApps).forEach { (name, pkg) ->
                SimpleAppRow(name, pkg, rules.firstOrNull { it.packageName == pkg }, settings.selectedTemplatePreset, colors, onOpenAppSettings) { rule ->
                    scope.launch { dao.saveRule(rule) }
                }
            }
            if (recommendedApps(installedApps).isEmpty()) EmptyText("暂未识别到短信、电话、微信，可在其他应用里搜索。", colors)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("其他应用", "简单模式不显示复杂包名说明，需要细节可到高级设置。", Icons.Outlined.List, colors) {
            OutlinedTextField(search, { search = it }, label = { Text("搜索应用") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            installedApps.filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search, true) }.take(20).forEach { (name, pkg) ->
                SimpleAppRow(name, pkg, rules.firstOrNull { it.packageName == pkg }, settings.selectedTemplatePreset, colors, onOpenAppSettings) { rule ->
                    scope.launch { dao.saveRule(rule) }
                }
            }
        }
    }
}

@Composable
private fun AppRuleSettingsScreen(modifier: Modifier, appName: String, packageName: String, settings: AppSettings, colors: UiColors) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    val existing = rules.firstOrNull { it.packageName == packageName }
    val recommendedTemplate = TemplateCatalog.recommend(appName, packageName).takeIf { it != TemplateCatalog.GENERAL_ID } ?: settings.selectedTemplatePreset
    var enabled by remember(existing?.enabled, packageName) { mutableStateOf(existing?.enabled ?: true) }
    var screenOffOnly by remember(existing?.screenOffOnly, packageName) { mutableStateOf(existing?.screenOffOnly ?: false) }
    var includes by remember(existing?.includes, packageName) { mutableStateOf(existing?.includes ?: defaultIncludesForTemplate(recommendedTemplate)) }
    var excludes by remember(existing?.excludes, packageName) { mutableStateOf(existing?.excludes.orEmpty()) }
    var templateId by remember(existing?.templateId, packageName) { mutableStateOf(existing?.templateId ?: recommendedTemplate) }
    var callTypes by remember(existing?.enabledCallEventTypes, packageName) {
        mutableStateOf(CallEventTypes.parse(existing?.enabledCallEventTypes ?: CallEventTypes.serialize(CallEventTypes.default)))
    }
    var status by remember { mutableStateOf("") }
    val isPhone = TemplateCatalog.recommend(appName, packageName) == "phone" || templateId == "phone"

    PageScaffold(appName, "配置这个 App 的转发、仅息屏、模板和关键词。", modifier, colors) {
        SectionCard("转发设置", packageName, Icons.Outlined.Tune, colors) {
            SettingSwitchRow("转发这个 App 的通知", enabled, { enabled = it }, colors)
            SettingSwitchRow("仅息屏时推送", screenOffOnly, { screenOffOnly = it }, colors)
            if (screenOffOnly) StatusBadge("仅息屏时推送已开启", Indigo, colors)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("消息模板", "可随时修改模板，不影响渠道配置。", Icons.Outlined.CheckCircle, colors) {
            TemplateSelector(templateId, { templateId = it }, colors)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("关键词规则", "包含关键词为空表示不过滤；排除关键词命中时会记为已过滤。", Icons.Outlined.List, colors) {
            OutlinedTextField(includes, { includes = it }, label = { Text("包含关键词，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(excludes, { excludes = it }, label = { Text("排除关键词，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        }
        if (isPhone) {
            Spacer(Modifier.height(12.dp))
            SectionCard("电话通知类型", "至少选择一种。", Icons.Outlined.Notifications, colors) {
                CallTypeSelector(callTypes, { callTypes = it }, colors)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryAction("保存 App 设置", colors) {
            if (isPhone && callTypes.isEmpty()) {
                status = "请至少选择一种电话通知类型"
            } else {
                scope.launch {
                    dao.saveRule(
                        (existing ?: RuleEntity(packageName, appName)).copy(
                            appName = appName,
                            includes = includes,
                            excludes = excludes,
                            enabled = enabled,
                            templateId = templateId,
                            screenOffOnly = screenOffOnly,
                            enabledCallEventTypes = CallEventTypes.serialize(callTypes)
                        )
                    )
                    status = "App 设置已保存"
                }
            }
        }
        if (status.isNotBlank()) StatusBadge(status, if ("已保存" in status) Success else Danger, colors)
    }
}

@Composable
private fun SimpleTemplatePresetScreen(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository, colors: UiColors) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf("") }
    PageScaffold("消息模板", "选择模板即可使用，自定义变量编辑放在高级设置。", modifier, colors) {
        simpleTemplatePresets().forEach { template ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                    scope.launch { repository.setSelectedTemplatePreset(template.id) }
                    preview = template.template().renderTitle(previewMessage()) + "\n" + template.template().renderBody(previewMessage())
                },
                colors = CardDefaults.outlinedCardColors(containerColor = colors.card),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(template.name, color = colors.ink, fontWeight = FontWeight.Bold)
                        Text(template.body, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    StatusBadge(if (settings.selectedTemplatePreset == template.id) "已选择" else "可选择", if (settings.selectedTemplatePreset == template.id) Success else Warning, colors)
                }
            }
        }
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            SectionCard("本地预览", null, Icons.Outlined.CheckCircle, colors) { Text(preview, color = colors.ink, lineHeight = 20.sp) }
        }
    }
}

@Composable
private fun QuietHoursScreen(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository, colors: UiColors) {
    val scope = rememberCoroutineScope()
    var start by remember(settings.quietStart) { mutableStateOf(settings.quietStart) }
    var end by remember(settings.quietEnd) { mutableStateOf(settings.quietEnd) }
    var urgent by remember(settings.urgentKeywords) { mutableStateOf(settings.urgentKeywords.ifBlank { "验证码\n来电\n未接来电" }) }
    PageScaffold("免打扰", "普通消息在免打扰时段会被过滤，重要消息可以例外。", modifier, colors) {
        SectionCard("免打扰", "支持跨午夜，例如 23:00-08:00。", Icons.Outlined.Schedule, colors) {
            SettingSwitchRow("启用免打扰", settings.quietEnabled, { scope.launch { repository.setQuiet(it, start, end, urgent) } }, colors)
            OutlinedTextField(start, { start = it }, label = { Text("开始时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("结束时间 HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(urgent, { urgent = it }, label = { Text("重要消息关键词，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            PrimaryAction("保存免打扰", colors) { scope.launch { repository.setQuiet(settings.quietEnabled, start, end, urgent) } }
        }
    }
}

@Composable
private fun BackgroundHealthScreen(modifier: Modifier, openPermission: () -> Unit, colors: UiColors) {
    PageScaffold("后台运行", "应用无法自动判断所有项目，请结合手机系统后台设置确认。", modifier, colors) {
        SectionCard("检查清单", null, Icons.Outlined.PlayCircle, colors) {
            StatusRow("通知访问权限", "到系统设置中确认", Warning, colors)
            StatusRow("应用通知权限", "到系统设置中确认", Warning, colors)
            StatusRow("后台运行建议", "建议开启自启、锁后台、省电白名单", Warning, colors)
            PrimaryAction("去系统设置", colors) { openPermission() }
        }
    }
}

@Composable
private fun BackupRestoreScreen(modifier: Modifier, colors: UiColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupText by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    PageScaffold("备份与恢复", "默认备份基础配置，历史记录不默认包含。", modifier, colors) {
        SectionCard("配置文件", "备份可能包含 Bark Token / Webhook 等敏感配置，请勿公开分享。", Icons.Outlined.CheckCircle, colors) {
            OutlinedButton(onClick = { scope.launch { backupText = ConfigBackup.export(context, false); backupStatus = "已导出基础配置" } }, modifier = Modifier.fillMaxWidth()) { Text("导出基础配置") }
            OutlinedTextField(backupText, { backupText = it }, label = { Text("备份内容 / 恢复内容") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            PrimaryAction("恢复配置", colors) {
                scope.launch {
                    runCatching { ConfigBackup.import(context, backupText) }
                        .onSuccess { backupStatus = "恢复完成" }
                        .onFailure { backupStatus = it.message ?: "恢复失败" }
                }
            }
            if (backupStatus.isNotBlank()) StatusBadge(backupStatus, if ("完成" in backupStatus || "导出" in backupStatus) Success else Danger, colors)
        }
    }
}

@Composable
private fun AdvancedSettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    repository: AppSettingsRepository,
    colors: UiColors,
    onOpenRules: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var retryCount by remember(settings.maxRetryCount) { mutableStateOf(settings.maxRetryCount.toString()) }
    PageScaffold("高级设置", "适合了解通知过滤、Webhook 和消息模板的用户。", modifier, colors) {
        SectionCard("风险提示", "错误配置可能导致消息漏发、重复发送或模板显示异常。", Icons.Outlined.Tune, colors) {
            Text("只修改你明确理解的项目。", color = colors.muted)
        }
        SettingNavRow("关键词过滤与应用独立规则", "配置包含/排除关键词、模板、仅锁屏和电话类型。", Icons.Outlined.Tune, onOpenRules, colors)
        SettingNavRow("自定义消息模板", "编辑 {{app}}、{{title}}、{{body}}、{{time}} 等变量。", Icons.Outlined.List, onOpenTemplates, colors)
        SectionCard("多渠道同时发送", "开启后同一条消息会同时发送到全部启用渠道。", Icons.Outlined.Notifications, colors) {
            SettingSwitchRow("启用多渠道同时发送", settings.multiChannelSend, { scope.launch { repository.setMultiChannelSend(it) } }, colors)
        }
        SectionCard("失败自动重试", "网络错误或 429、5xx 错误会按 WorkManager 策略重试。", Icons.Outlined.History, colors) {
            SettingSwitchRow("启用失败重试", settings.retryEnabled, { scope.launch { repository.setRetryPolicy(it, retryCount.toIntOrNull() ?: settings.maxRetryCount) } }, colors)
            OutlinedTextField(retryCount, { retryCount = it.filter(Char::isDigit).take(2) }, label = { Text("最大重试次数") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            PrimaryAction("保存重试策略", colors) { scope.launch { repository.setRetryPolicy(settings.retryEnabled, retryCount.toIntOrNull() ?: 3) } }
        }
    }
}

@Composable
private fun Rules(modifier: Modifier, colors: UiColors) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    val apps = remember { loadInstalledApps(context) }
    var search by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<Pair<String, String>?>(null) }
    var include by remember { mutableStateOf("") }
    var exclude by remember { mutableStateOf("") }
    var screenOffOnly by remember { mutableStateOf(false) }
    var callTypes by remember { mutableStateOf(CallEventTypes.default) }
    var ruleError by remember { mutableStateOf("") }
    PageScaffold("应用独立规则", "按来源 App 配置关键词、模板、仅锁屏和电话通知类型。", modifier, colors) {
        SectionCard("新增规则", "先从应用列表选择来源 App，找不到时可手动搜索。", Icons.Outlined.Tune, colors) {
            OutlinedTextField(search, { search = it }, label = { Text("搜索应用或包名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            apps.filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search, true) }.take(12).forEach { app ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(app.first, color = colors.ink)
                        Text(app.second, color = colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RadioButton(selectedApp?.second == app.second, onClick = { selectedApp = app })
                }
            }
            OutlinedTextField(include, { include = it }, label = { Text("包含关键词，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(exclude, { exclude = it }, label = { Text("排除关键词，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            SettingSwitchRow("仅锁屏时推送", screenOffOnly, { screenOffOnly = it }, colors)
            if (selectedApp?.let { TemplateCatalog.recommend(it.first, it.second) == "phone" } == true) {
                CallTypeSelector(callTypes, { callTypes = it }, colors)
            }
            PrimaryAction("保存规则", colors, enabled = selectedApp != null) {
                val app = selectedApp ?: return@PrimaryAction
                val template = TemplateCatalog.recommend(app.first, app.second)
                if (template == "phone" && callTypes.isEmpty()) {
                    ruleError = "请至少选择一种电话通知类型"
                    return@PrimaryAction
                }
                scope.launch {
                    dao.saveRule(RuleEntity(app.second, app.first, include, exclude, true, template, screenOffOnly = screenOffOnly, enabledCallEventTypes = CallEventTypes.serialize(callTypes)))
                    ruleError = "规则已保存"
                }
            }
            if (ruleError.isNotBlank()) StatusBadge(ruleError, if ("保存" in ruleError) Success else Danger, colors)
        }
        Spacer(Modifier.height(12.dp))
        rules.forEach { rule ->
            SectionCard(rule.appName, rule.packageName, Icons.Outlined.List, colors) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(if (rule.enabled) "启用" else "停用", if (rule.enabled) Success else colors.muted, colors)
                    if (rule.screenOffOnly) StatusBadge("仅锁屏", Indigo, colors)
                }
                TemplateSelector(rule.templateId, { template -> scope.launch { dao.saveRule(rule.copy(templateId = template)) } }, colors)
                SettingSwitchRow("仅锁屏时推送", rule.screenOffOnly, { enabled -> scope.launch { dao.saveRule(rule.copy(screenOffOnly = enabled)) } }, colors)
                if (TemplateCatalog.recommend(rule.appName, rule.packageName) == "phone" || rule.templateId == "phone") {
                    Text("电话通知类型", color = colors.ink, fontWeight = FontWeight.Bold)
                    CallTypeSelector(CallEventTypes.parse(rule.enabledCallEventTypes), { value ->
                        if (value.isNotEmpty()) scope.launch { dao.saveRule(rule.copy(enabledCallEventTypes = CallEventTypes.serialize(value))) }
                    }, colors)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { dao.saveRule(rule.copy(enabled = !rule.enabled)) } }) { Text(if (rule.enabled) "停用" else "启用") }
                    TextButton(onClick = { scope.launch { dao.deleteRule(rule.packageName) } }) { Text("删除") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun UserManualScreen(modifier: Modifier, colors: UiColors) {
    PageScaffold("使用教程", "小白步骤版说明书。", modifier, colors) {
        listOf(
            "1. 快速开始" to "先配置推送渠道，再选择要转发的软件，最后选择模板并发送测试消息。",
            "2. 配置推送渠道" to "Bark、飞书、钉钉任选一种。配置完成后建议先发测试消息。",
            "3. 选择转发软件" to "推荐选择短信、电话、微信，也可以添加其他 App。",
            "4. 选择消息模板" to "普通用户直接选择预设模板，高级用户再修改变量。",
            "5. 设置仅锁屏时推送" to "不想使用手机时被重复提醒，可给单个应用开启仅锁屏。",
            "6. 设置电话通知类型" to "电话可选择未接来电、来电提醒和来电已接通。",
            "7. 配置免打扰" to "支持跨午夜时段和重要关键词例外。",
            "8. 后台运行与权限" to "不同手机可能需要手动开启自启、锁后台、省电白名单。",
            "9. 查看和处理转发记录" to "记录页分为全部、成功、失败和已过滤。",
            "10. 备份与恢复" to "备份可能包含渠道 Token，请妥善保存。",
            "11. 高级设置说明" to "高级设置适合了解关键词、Webhook 和规则含义的用户。",
            "12. 版本更新" to "消息接力可以通过 GitHub Releases 检查新版本。默认会自动检查，也可以在设置里的版本更新页面关闭，关闭后仍可手动检查。",
            "13. 常见问题" to "先看首页状态卡和记录详情，再按提示修复。"
        ).forEach { (title, text) -> ManualChapter(title, text, colors) }
    }
}

@Composable
private fun SimManagementScreen(modifier: Modifier, colors: UiColors) {
    val context = LocalContext.current
    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    val telephony = remember { context.getSystemService(TelephonyManager::class.java) }
    val supportsTelephony = telephony?.phoneType != TelephonyManager.PHONE_TYPE_NONE
    val subscriptions = remember(hasPhoneState) {
        if (!hasPhoneState) emptyList() else runCatching {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())
    }
    PageScaffold("SIM 卡管理", "查看电话监听状态，以及 SIM 卡在 App 中使用的名称。", modifier, colors) {
        SectionCard("电话权限状态", "电话权限只影响来电、未接来电和接通提醒。", Icons.Outlined.Notifications, colors) {
            StatusRow("电话状态权限", if (hasPhoneState) "已允许" else "未允许", if (hasPhoneState) Success else Warning, colors)
            StatusRow("联系人权限", if (hasContacts) "已允许" else "未允许", if (hasContacts) Success else Warning, colors)
            StatusRow("通话记录权限", if (hasCallLog) "已允许" else "未允许", if (hasCallLog) Success else Warning, colors)
            OutlinedButton(onClick = { openAppPermissionSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("打开系统权限设置")
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("设备能力", "真机上的双卡、eSIM 和厂商电话回调可能不同。", Icons.Outlined.CheckCircle, colors) {
            StatusRow("电话能力", if (supportsTelephony) "设备支持" else "设备不支持或模拟器不可用", if (supportsTelephony) Success else Warning, colors)
            Text("如果电话权限已允许但仍看不到 SIM，通常是系统不开放订阅信息；这不影响普通 App 通知、短信和微信通知转发。", color = colors.muted, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("SIM 列表", "读取不到时会显示原因，不会影响其他转发功能。", Icons.Outlined.List, colors) {
            when {
                !hasPhoneState -> EmptyText("需要先允许电话状态权限，才能读取活动 SIM。", colors)
                subscriptions.isEmpty() -> EmptyText("当前没有读取到活动 SIM。模拟器、部分 ROM 或未插卡设备会出现这种情况。", colors)
                else -> subscriptions.forEach { info ->
                    StatusRow("卡槽 ${info.simSlotIndex + 1}", info.displayName?.toString().orEmpty().ifBlank { "未命名 SIM" }, Indigo, colors)
                    Text("运营商：${info.carrierName?.toString().orEmpty().ifBlank { "未知" }}", color = colors.muted, lineHeight = 18.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun VersionUpdateScreen(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository, colors: UiColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }

    fun runCheck() {
        scope.launch {
            checking = true
            result = null
            val checkResult = withContext(Dispatchers.IO) {
                UpdateRepository().check(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
            }
            repository.setLastUpdateCheckAt(System.currentTimeMillis())
            result = checkResult
            checking = false
        }
    }

    PageScaffold("版本更新", "通过 GitHub Releases 检查新版本，不会自动下载或安装。", modifier, colors) {
        SectionCard("当前版本", "v${BuildConfig.VERSION_NAME} · 内部版本 ${BuildConfig.VERSION_CODE}", Icons.Outlined.CheckCircle, colors) {
            SettingSwitchRow("自动检查更新", settings.autoCheckUpdates, { enabled ->
                scope.launch { repository.setAutoCheckUpdates(enabled) }
            }, colors)
            Text("启动应用后最多每 24 小时自动检查一次 GitHub Releases。", color = colors.muted, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryAction(if (checking) "正在检查更新" else "检查更新", colors, enabled = !checking) { runCheck() }
            if (checking) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator()
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val value = result) {
            is UpdateCheckResult.UpdateAvailable -> SectionCard("发现新版本", "最新版本 ${value.release.tagName}", Icons.Outlined.Notifications, colors) {
                Text("当前版本：v${value.currentVersion}", color = colors.muted)
                value.release.publishedAt?.let { Text("发布时间：${TimeFormatter.formatRecordDetailTime(it)}", color = colors.muted) }
                val notes = value.release.body.orEmpty().take(600)
                if (notes.isNotBlank()) {
                    OutlinedButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.fillMaxWidth()) { Text("查看更新内容") }
                    if (detailsExpanded) Text(notes, color = colors.ink, lineHeight = 19.sp)
                }
                PrimaryAction("前往 GitHub 下载", colors) {
                    openExternalLink(context, value.release.htmlUrl.ifBlank { GITHUB_RELEASES_URL })
                }
            }
            is UpdateCheckResult.Latest -> SectionCard("已是最新版本", "当前 v${value.currentVersion}", Icons.Outlined.CheckCircle, colors) {
                Text("最近检查：刚刚", color = colors.muted)
            }
            is UpdateCheckResult.Error -> SectionCard("检查更新失败", "请检查网络后重试", Icons.Outlined.Tune, colors) {
                Text(value.message, color = colors.ink)
                if (value.detail.isNotBlank()) Text("高级信息：${value.detail}", color = colors.muted)
            }
            null -> SectionCard("GitHub Releases", "查看所有历史版本与 APK。", Icons.Outlined.History, colors) {
                OutlinedButton(onClick = { openExternalLink(context, GITHUB_RELEASES_URL) }, modifier = Modifier.fillMaxWidth()) { Text("查看 GitHub Releases") }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("隐私说明", "关闭自动检查后，应用不会在启动时主动检查版本。", Icons.Outlined.CheckCircle, colors) {
            Text("更新检查通过公开 GitHub Releases 完成，不需要登录 GitHub，也不需要 GitHub Token。", color = colors.muted, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun AboutMessageRelayScreen(modifier: Modifier, colors: UiColors) {
    val context = LocalContext.current
    PageScaffold("关于消息接力", "项目版本、开源许可与开发信息。", modifier, colors) {
        SectionCard("消息接力 Message Relay", null, Icons.Outlined.CheckCircle, colors) {
            StatusRow("版本", "v${BuildConfig.VERSION_NAME}", Indigo, colors)
            StatusRow("内部版本", BuildConfig.VERSION_CODE.toString(), Indigo, colors)
            StatusRow("开源许可", "GPL-3.0-only", Success, colors)
            Text("开发方式：OpenAI Codex 辅助开发", color = colors.muted, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { openExternalLink(context, GITHUB_REPOSITORY_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("GitHub 开源项目")
            }
        }
    }
}

@Composable
private fun TemplateLibrary(colors: UiColors) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("自定义模板") }
    var title by remember { mutableStateOf("{{app}}：{{title}}") }
    var body by remember { mutableStateOf("{{body}}\n{{time}}") }
    var preview by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    SectionCard("模板库", "模板决定转发消息在 Bark、飞书、钉钉里显示成什么样。", Icons.Outlined.List, colors) {
        Text("变量说明：{{app}} 是 App 名，{{title}} 是通知标题，{{body}} 是通知正文，{{time}} 是时间。", color = colors.muted, lineHeight = 19.sp)
        OutlinedTextField(name, { name = it }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("标题样式") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("正文样式") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedButton(onClick = {
            preview = MessageTemplate(title, body).renderTitle(previewMessage()) + "\n" + MessageTemplate(title, body).renderBody(previewMessage())
            status = "本地预览已生成"
        }, modifier = Modifier.fillMaxWidth()) { Text("本地预览") }
        OutlinedButton(onClick = {
            val channels = ChannelSelection.enabled(storedChannels(context))
            status = if (channels.isEmpty()) "请先保存推送渠道后再发送预览" else "发送预览到渠道"
        }, modifier = Modifier.fillMaxWidth()) { Text("发送预览到渠道") }
        PrimaryAction("保存模板", colors) {
            scope.launch {
                dao.saveTemplate(TemplateEntity("custom_${System.currentTimeMillis()}", name.ifBlank { "自定义模板" }, title, body))
                status = "模板已保存"
            }
        }
        if (preview.isNotBlank()) Text(preview, color = colors.ink, lineHeight = 19.sp)
        if (status.isNotBlank()) StatusBadge(status, if ("已" in status || "保存" in status) Success else Warning, colors)
    }
}

@Composable
private fun ChangelogSection(colors: UiColors) {
    val context = LocalContext.current
    SectionCard("更新日志", "按版本列出新增、优化和修复。", Icons.Outlined.History, colors) {
        ReleaseNotes.all(BuildConfig.VERSION_NAME).forEach { note ->
            Text(note.version, color = colors.ink, fontWeight = FontWeight.Bold)
            Text(note.date, color = colors.muted, fontSize = 13.sp)
            ReleaseNoteList("新增", note.added, colors)
            ReleaseNoteList("优化", note.improved, colors)
            ReleaseNoteList("修复", note.fixed, colors)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { openExternalLink(context, GITHUB_RELEASES_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text("查看 GitHub Releases")
        }
    }
}

@Composable
private fun AboutLinksSection(colors: UiColors) {
    SectionCard("关于与链接", "更多内容、反馈与自用资源入口。", Icons.Outlined.CheckCircle, colors) {
        listOf("个人博客", "资源导航站", "TG频道", "TG聊天室", "GitHub", "YouTube", "自用机场推荐").forEach { LinkRow(it, colors) }
    }
}

@Composable
private fun SourceSelectionCard(
    apps: List<Pair<String, String>>,
    search: String,
    onSearch: (String) -> Unit,
    manualPackage: String,
    onManualPackage: (String) -> Unit,
    selectedSources: List<SourceSelection>,
    onSelectedSources: (List<SourceSelection>) -> Unit,
    colors: UiColors
) {
    SectionCard("选择来源应用（可多选）", "只转发被选中的 App。", Icons.Outlined.List, colors) {
        StatusBadge("来源显示不全时，请允许设备应用列表 / 查询所有软件包", Warning, colors)
        Text("建议同时处理自启、锁后台、省电限制。不明白可以询问 AI。", color = colors.muted, lineHeight = 19.sp)
        OutlinedTextField(search, onSearch, label = { Text("搜索应用") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        apps.filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search, true) }.take(12).forEach { app ->
            val checked = selectedSources.any { it.packageName == app.second }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(app.first, color = colors.ink)
                    Text(app.second, color = colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Checkbox(checked, { value ->
                    onSelectedSources(
                        if (value) SelectedSources.add(selectedSources, SourceSelection(app.first, app.second, TemplateCatalog.recommend(app.first, app.second)))
                        else selectedSources.filterNot { it.packageName == app.second }
                    )
                })
            }
        }
        OutlinedTextField(manualPackage, onManualPackage, label = { Text("手动输入包名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(
            onClick = {
                onSelectedSources(SelectedSources.add(selectedSources, SourceSelection(manualPackage, manualPackage, TemplateCatalog.recommend(manualPackage, manualPackage))))
                onManualPackage("")
            },
            enabled = manualPackage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("添加包名") }
    }
}

@Composable
private fun SimpleAppRow(
    appName: String,
    packageName: String,
    rule: RuleEntity?,
    templatePreset: String,
    colors: UiColors,
    onOpenSettings: (Pair<String, String>) -> Unit,
    onRuleChange: (RuleEntity) -> Unit
) {
    val templateId = TemplateCatalog.recommend(appName, packageName).takeIf { it != TemplateCatalog.GENERAL_ID } ?: templatePreset
    val enabled = rule?.enabled == true
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(appName, color = colors.ink, fontWeight = FontWeight.Bold)
            Text(if (rule?.screenOffOnly == true) "仅息屏时推送 · ${simpleTemplateName(rule.templateId)}" else simpleTemplateName(rule?.templateId ?: templateId), color = colors.muted, fontSize = 13.sp, lineHeight = 17.sp)
            if (rule?.screenOffOnly == true) StatusBadge("仅息屏", Indigo, colors)
        }
        Column {
            Switch(enabled, onCheckedChange = { checked ->
                onRuleChange((rule ?: RuleEntity(packageName, appName, defaultIncludesForTemplate(templateId), templateId = templateId)).copy(enabled = checked))
            })
            TextButton(onClick = { onOpenSettings(appName to packageName) }) { Text("设置") }
        }
    }
}

@Composable
private fun RecordDetailDialog(record: DeliveryRecord, colors: UiColors, onDismiss: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onAdjustRules: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.app} · ${record.status}") },
        text = {
            Column {
                Text("标题：${record.title}", color = colors.ink)
                Text("正文：${record.body}", color = colors.muted, lineHeight = 18.sp)
                Text("时间：${TimeFormatter.formatRecordDetailTime(record.createdAt)}", color = colors.muted)
            }
        },
        confirmButton = {
            Row {
                when (record.status) {
                    "成功" -> TextButton(onClick = onRetry) { Text("重新发送") }
                    "已过滤" -> {
                        TextButton(onClick = onRetry) { Text("仍然发送") }
                        TextButton(onClick = onAdjustRules) { Text("调整规则") }
                    }
                    else -> {
                        TextButton(onClick = onRetry) { Text("立即重试") }
                        TextButton(onClick = {}) { Text("一键诊断") }
                    }
                }
                TextButton(onClick = onCopy) { Text(if ("验证码" in record.title || "验证码" in record.body) "复制验证码" else "复制内容") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun CallTypeSelector(selected: Set<CallEventType>, onChange: (Set<CallEventType>) -> Unit, colors: UiColors) {
    Column {
        Text("电话通知类型", color = colors.ink, fontWeight = FontWeight.Bold)
        listOf(
            CallEventType.MISSED_CALL to "未接来电",
            CallEventType.INCOMING_RINGING to "来电提醒",
            CallEventType.CALL_ANSWERED to "来电已接通"
        ).forEach { (type, label) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = colors.ink)
                Checkbox(checked = type in selected, onCheckedChange = { checked: Boolean ->
                    onChange(if (checked) selected + type else selected - type)
                })
            }
        }
    }
}

@Composable
private fun ChannelChoice(selected: String, onSelect: (String) -> Unit, colors: UiColors) {
    listOf("feishu" to "飞书", "dingtalk" to "钉钉", "bark" to "Bark").forEach { (type, label) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = colors.ink, fontWeight = FontWeight.Bold)
            RadioButton(selected == type, onClick = { onSelect(type) })
        }
    }
}

@Composable
private fun SelectedChannelFields(
    selected: String,
    dingtalk: String,
    onDing: (String) -> Unit,
    dingSecret: String,
    onDingSecret: (String) -> Unit,
    feishu: String,
    onFei: (String) -> Unit,
    feiSecret: String,
    onFeiSecret: (String) -> Unit,
    bark: String,
    onBark: (String) -> Unit,
    colors: UiColors
) {
    when (selected) {
        "dingtalk" -> {
            OutlinedTextField(dingtalk, onDing, label = { Text("钉钉 Webhook") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(dingSecret, onDingSecret, label = { Text("加签密钥（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        "feishu" -> {
            OutlinedTextField(feishu, onFei, label = { Text("飞书 Webhook") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(feiSecret, onFeiSecret, label = { Text("签名密钥（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("不知道怎么配置？可以先参考教程。", color = colors.muted)
            LinkRow("飞书推送配置参考（推荐）", colors)
        }
        else -> {
            OutlinedTextField(bark, onBark, label = { Text("Bark 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            LinkRow("Bark 推送参考", colors)
        }
    }
}

@Composable
private fun TemplateSelector(selected: String, onSelect: (String) -> Unit, colors: UiColors) {
    Column {
        Text("模板可随时修改", color = colors.muted, fontSize = 13.sp)
        simpleTemplatePresets().forEach { template ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(template.name, color = colors.ink)
                RadioButton(selected == template.id, onClick = { onSelect(template.id) })
            }
        }
    }
}

@Composable
private fun ManualChapter(title: String, text: String, colors: UiColors) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = if (expanded) "教程章节已展开：$title" else "展开教程章节：$title"
        }.clickable { expanded = !expanded },
        colors = CardDefaults.outlinedCardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = colors.ink, fontWeight = FontWeight.Bold)
            if (expanded) Text(text, color = colors.muted, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PageScaffold(title: String, subtitle: String? = null, modifier: Modifier = Modifier, colors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = colors.ink)
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.muted, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, icon: ImageVector = Icons.Outlined.CheckCircle, colors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = colors.card, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = Indigo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.muted, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FeatureCard(title: String, status: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit, colors: UiColors) {
    Surface(shape = RoundedCornerShape(12.dp), color = colors.card, shadowElevation = 1.dp, modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = Indigo)
            Spacer(Modifier.height(8.dp))
            Text(title, color = colors.ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = colors.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SetupStep(label: String, done: Boolean, onClick: () -> Unit, colors: UiColors) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.ink, fontWeight = FontWeight.Medium)
        StatusBadge(if (done) "已完成" else "待配置", if (done) Success else Warning, colors)
    }
}

@Composable
private fun SettingNavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, colors: UiColors) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = Indigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.ink, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.muted, lineHeight = 18.sp)
            }
            Text("›", color = Indigo, fontSize = 24.sp)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, colors: UiColors) {
    Text(title, color = colors.ink, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, colors: UiColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = colors.ink, fontWeight = FontWeight.Medium)
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(label: String, value: String, color: Color, colors: UiColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.muted)
        StatusBadge(value, color, colors)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color, colors: UiColors) {
    Surface(shape = RoundedCornerShape(100.dp), color = color.copy(alpha = 0.12f)) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryAction(text: String, colors: UiColors, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyText(text: String, colors: UiColors) {
    Text(text, color = colors.muted, lineHeight = 19.sp)
}

@Composable
private fun RecordLine(title: String, subtitle: String, colors: UiColors) {
    Text(title, color = colors.ink, fontWeight = FontWeight.Medium)
    Text(subtitle, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LinkRow(label: String, colors: UiColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Indigo, fontWeight = FontWeight.Bold)
        Text("打开", color = colors.muted)
    }
}

@Composable
private fun ReleaseNoteList(title: String, items: List<String>, colors: UiColors) {
    Text(title, color = colors.ink, fontWeight = FontWeight.Bold)
    items.forEach { Text("· $it", color = colors.muted, lineHeight = 18.sp) }
}

private fun channelsFromInputs(dingtalk: String, feishu: String, bark: String, dingSecret: String = "", feiSecret: String = "") =
    listOf(ChannelConfig("dingtalk", dingtalk.trim(), dingSecret), ChannelConfig("feishu", feishu.trim(), feiSecret), ChannelConfig("bark", bark.trim()))
        .filter { it.url.isNotBlank() }

private fun selectedChannelConfig(type: String, dingtalk: String, feishu: String, bark: String, dingSecret: String = "", feiSecret: String = "") =
    ChannelSelection.singleEnabled(channelsFromInputs(dingtalk, feishu, bark, dingSecret, feiSecret).filter { it.type == type })

private fun storedChannels(context: Context): List<ChannelConfig> =
    SecureStore(context).get("channels")?.let { runCatching { ChannelSender.parse(it) }.getOrDefault(emptyList()) }.orEmpty()

private suspend fun reconcileUpgradeState(context: Context, repository: AppSettingsRepository, dao: RelayDao) {
    val settings = repository.current()
    val channels = storedChannels(context)
    val rules = dao.allRules()
    if (!settings.onboardingComplete && (channels.isNotEmpty() || rules.isNotEmpty())) {
        repository.setOnboardingComplete(true)
    }
    val enabledChannels = ChannelSelection.enabled(channels)
    if (enabledChannels.isNotEmpty() && settings.primaryChannelId !in enabledChannels.map(ChannelConfig::id)) {
        repository.setPrimaryChannelId(enabledChannels.first().id)
    }
}

private fun openAppPermissionSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun loadInstalledApps(context: Context): List<Pair<String, String>> =
    runCatching {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0)
            .map { appInfo -> appInfo.loadLabel(packageManager).toString() to appInfo.packageName }
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }
            .sortedWith(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second })
    }.getOrDefault(emptyList())

private fun recommendedApps(apps: List<Pair<String, String>>): List<Pair<String, String>> {
    val byPackage = apps.associateBy { it.second }
    fun firstKnown(label: String, packages: List<String>): Pair<String, String>? =
        packages.firstNotNullOfOrNull { pkg -> byPackage[pkg]?.let { label to it.second } }
    return listOfNotNull(
        firstKnown(
            "短信",
            listOf(
                "com.google.android.apps.messaging",
                "com.android.mms",
                "com.android.messaging",
                "com.samsung.android.messaging",
                "com.miui.mms"
            )
        ),
        firstKnown(
            "电话",
            listOf(
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.android.dialer",
                "com.android.phone",
                "com.android.server.telecom"
            )
        ),
        firstKnown(
            "微信",
            listOf(
                "com.tencent.mm",
                "com.tencent.wework"
            )
        )
    ).distinctBy { it.second }
}

private fun simpleTemplatePresets(): List<TemplateDefinition> =
    listOf("simple", TemplateCatalog.STANDARD_ID, "privacy", "raw").map(TemplateCatalog::byId)

private fun simpleTemplateName(id: String): String = when (id) {
    "simple" -> "简洁模板"
    "privacy" -> "隐私模板"
    "raw" -> "原始通知模板"
    else -> "标准模板"
}

private fun retentionLabel(value: String): String = when (value) {
    "7" -> "7 天"
    "90" -> "90 天"
    "forever" -> "永久"
    "status_only" -> "仅状态"
    else -> "30 天"
}

private fun defaultIncludesForTemplate(id: String): String = when (id) {
    "sms" -> "验证码"
    "phone" -> "未接来电\n来电提醒"
    else -> ""
}

private fun previewMessage() = RelayMessage("com.tencent.mm", "微信", "张三", "明天 10 点开会", System.currentTimeMillis())

private fun channelName(type: String): String = when (type) {
    "dingtalk" -> "钉钉"
    "feishu" -> "飞书"
    "bark" -> "Bark"
    else -> type
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("message-relay-record", value))
}

private fun updateSubtitle(settings: AppSettings): String =
    if (settings.lastUpdateCheckAt > 0) "当前 v${BuildConfig.VERSION_NAME} · 最近检查 ${TimeFormatter.formatRecordListTime(settings.lastUpdateCheckAt)}"
    else "检查新版本"

private fun openExternalLink(context: Context, url: String): Boolean =
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
