package io.github.messagerelay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId

private val Indigo = Color(0xFF5368FF)
private val Ink = Color(0xFF17203B)
private val Cloud = Color(0xFFF7F8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessageRelayApp { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
    }
}

@Composable
fun MessageRelayApp(openPermission: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppSettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = AppSettings())
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        RelayDatabase.get(context).relayDao().ensureTemplates(repository.current())
        val secure = SecureStore(context)
        secure.get("channels")?.let { raw -> runCatching { ChannelSender.parse(raw) }.getOrNull()?.let { secure.put("channels", ChannelSender.serialize(ChannelSelection.singleEnabled(it))) } }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Indigo, background = Cloud, surface = Color.White, onBackground = Ink)) {
        if (!settings.onboardingComplete) {
            Onboarding(openPermission, repository)
        } else {
            Scaffold(bottomBar = {
                NavigationBar {
                    listOf("首页", "规则", "记录", "设置").forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("首", "规", "录", "设")[index]) }, label = { Text(label) })
                    }
                }
            }) { padding ->
                when (tab) {
                    0 -> Home(Modifier.padding(padding), settings, repository)
                    1 -> Rules(Modifier.padding(padding))
                    2 -> Records(Modifier.padding(padding))
                    else -> SettingsScreen(Modifier.padding(padding), settings, repository)
                }
            }
        }
    }
}

@Composable
private fun Onboarding(openPermission: () -> Unit, repository: AppSettingsRepository) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0).map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first }
    }
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

    Page("四步完成首次配置", Modifier) {
        Text("第 ${step + 1} 步，共 4 步", color = Color.Gray)
        when (step) {
            0 -> Panel { Text("开启通知访问", fontWeight = FontWeight.Bold); Text("点击下方按钮，在系统页面允许“消息接力”读取通知。", color = Color.Gray); Button(openPermission) { Text("打开通知访问设置") } }
            1 -> Panel {
                Text("选择来源应用（可多选）", fontWeight = FontWeight.Bold)
                OutlinedTextField(search, { search = it }, label = { Text("搜索应用") }, modifier = Modifier.fillMaxWidth())
                apps.filter { search.isBlank() || it.first.contains(search, true) || it.second.contains(search, true) }.forEach { app ->
                    val checked = selectedSources.any { it.packageName == app.second }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(app.first); Text(app.second, color = Color.Gray, fontSize = 11.sp) }
                        Checkbox(checked, { value -> selectedSources = if (value) SelectedSources.add(selectedSources, SourceSelection(app.first, app.second, TemplateCatalog.recommend(app.first, app.second))) else selectedSources.filterNot { it.packageName == app.second } })
                    }
                }
                OutlinedTextField(manualPackage, { manualPackage = it }, label = { Text("手动输入包名") }, modifier = Modifier.fillMaxWidth())
                Button({ selectedSources = SelectedSources.add(selectedSources, SourceSelection(manualPackage, manualPackage, TemplateCatalog.recommend(manualPackage, manualPackage))); manualPackage = "" }, enabled = manualPackage.isNotBlank()) { Text("添加包名") }
                selectedSources.forEach { source ->
                    Text("${source.appName} · ${source.packageName}", fontWeight = FontWeight.Bold)
                    TemplateSelector(source.templateId) { id -> selectedSources = selectedSources.map { if (it.packageName == source.packageName) it.copy(templateId = id) else it } }
                    TextButton({ selectedSources = selectedSources.filterNot { it.packageName == source.packageName } }) { Text("移除") }
                }
            }
            2 -> {
                ChannelChoice(selectedChannel, { selectedChannel = it })
                SelectedChannelFields(selectedChannel, dingtalk, { dingtalk = it }, dingSecret, { dingSecret = it }, feishu, { feishu = it }, feiSecret, { feiSecret = it }, bark, { bark = it })
            }
            else -> Panel {
                Text("发送测试消息", fontWeight = FontWeight.Bold)
                Text("至少一个渠道测试成功后才能完成配置。", color = Color.Gray)
                Button(onClick = {
                    scope.launch {
                        val channels = selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret)
                        SecureStore(context).put("channels", ChannelSender.serialize(channels))
                        val results = withContext(Dispatchers.IO) { channels.map { ChannelSender.send(it, "消息接力测试", "渠道配置成功") } }
                        testPassed = results.any(DeliveryResult::success)
                        status = if (testPassed) "测试成功，可以完成配置" else results.firstOrNull()?.error ?: "请先配置渠道"
                    }
                }) { Text("发送测试") }
                if (status.isNotBlank()) Text(status, color = if (testPassed) Color(0xFF119B75) else Color.Red)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    if (step == 1) dao.saveRules(selectedSources.map { RuleEntity(it.packageName, it.appName, templateId = it.templateId) })
                    if (step == 2) SecureStore(context).put("channels", ChannelSender.serialize(selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret)))
                    if (step < 3) step++ else repository.setOnboardingComplete(true)
                }
            },
            enabled = when (step) { 1 -> selectedSources.isNotEmpty() && selectedSources.all { it.templateId.isNotBlank() }; 2 -> selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret).isNotEmpty(); 3 -> testPassed; else -> true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (step == 3) "完成配置" else "继续") }
    }
}

private fun channelsFromInputs(dingtalk: String, feishu: String, bark: String, dingSecret: String = "", feiSecret: String = "") =
    listOf(ChannelConfig("dingtalk", dingtalk.trim(), dingSecret), ChannelConfig("feishu", feishu.trim(), feiSecret), ChannelConfig("bark", bark.trim()))
        .filter { it.url.isNotBlank() }.filter(ChannelValidation::isValid)

private fun selectedChannelConfig(type: String, dingtalk: String, feishu: String, bark: String, dingSecret: String = "", feiSecret: String = "") =
    ChannelSelection.singleEnabled(channelsFromInputs(dingtalk, feishu, bark, dingSecret, feiSecret).filter { it.type == type })

@Composable private fun Page(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink); Spacer(Modifier.height(18.dp)); content() }

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) =
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), content = content) }

@Composable private fun Home(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val since = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val count by dao.recordCountSince(since).collectAsState(initial = 0)
    val queued by dao.queuedCount().collectAsState(initial = 0)
    val records by dao.records().collectAsState(initial = emptyList())
    val channels = remember { SecureStore(context).get("channels")?.let(ChannelSender::parse).orEmpty() }
    Page("消息接力", modifier) {
        Panel {
            Text(if (settings.paused) "转发服务已暂停" else "转发服务运行中", color = Indigo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("今日已接力 $count 条 · 待发送 $queued 条", color = Color.Gray)
            Button(onClick = { scope.launch { repository.setPaused(!settings.paused) } }) { Text(if (settings.paused) "恢复" else "暂停") }
        }
        Spacer(Modifier.height(14.dp))
        Panel { Text("已启用渠道", fontWeight = FontWeight.Bold); Text(if (channels.isEmpty()) "尚未配置" else channels.joinToString("、") { channelName(it.type) }, color = Color.Gray) }
        Spacer(Modifier.height(14.dp))
        Panel { Text("最近记录", fontWeight = FontWeight.Bold); records.take(3).forEach { Text("${it.app} · ${it.status}${if (it.delayed) " · 延迟补发" else ""}", color = Color.Gray) }; if (records.isEmpty()) Text("暂无记录", color = Color.Gray) }
    }
}

@Composable private fun Rules(modifier: Modifier) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val rules by dao.rulesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pkg by remember { mutableStateOf("") }; var include by remember { mutableStateOf("") }; var exclude by remember { mutableStateOf("") }; var templateId by remember { mutableStateOf(TemplateCatalog.GENERAL_ID) }
    Page("转发规则", modifier) {
        Panel {
            OutlinedTextField(pkg, { pkg = it }, label = { Text("来源 App 包名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(include, { include = it }, label = { Text("包含关键词，每行一个") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(exclude, { exclude = it }, label = { Text("排除关键词，每行一个") }, modifier = Modifier.fillMaxWidth())
            TemplateSelector(templateId) { templateId = it }
            Button(onClick = { scope.launch { dao.saveRule(RuleEntity(pkg, pkg, include, exclude, templateId = templateId)); pkg = ""; include = ""; exclude = ""; templateId = TemplateCatalog.GENERAL_ID } }, enabled = pkg.isNotBlank()) { Text("保存规则") }
        }
        rules.forEach { rule -> Spacer(Modifier.height(10.dp)); Panel { Text(rule.appName, fontWeight = FontWeight.Bold); Text(rule.packageName, color = Color.Gray); TemplateSelector(rule.templateId) { id -> scope.launch { dao.saveRule(rule.copy(templateId = id)) } }; Row { Button({ scope.launch { dao.saveRule(rule.copy(enabled = !rule.enabled)) } }) { Text(if (rule.enabled) "停用" else "启用") }; Spacer(Modifier.width(8.dp)); TextButton({ scope.launch { dao.deleteRule(rule.packageName) } }) { Text("删除") } } } }
    }
}

@Composable private fun Records(modifier: Modifier) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val records by dao.records().collectAsState(initial = emptyList())
    Page("最近记录", modifier) {
        records.forEach { record -> Panel { Text("${record.app} · ${record.status}", fontWeight = FontWeight.Bold); Text(record.title, color = Color.Gray); if (record.channelResults != "[]") Text(record.channelResults, color = Color.Gray, fontSize = 11.sp); if (record.status != "成功") Button({ RelayEngine.enqueue(context, RelayMessage(record.packageName, record.app, record.title, record.body, record.createdAt), record.delayed) }) { Text("重新发送") } }; Spacer(Modifier.height(10.dp)) }
        if (records.isEmpty()) Text("暂无发送记录", color = Color.Gray)
    }
}

@Composable private fun SettingsScreen(modifier: Modifier, settings: AppSettings, repository: AppSettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember { SecureStore(context).get("channels")?.let(ChannelSender::parse).orEmpty() }
    var selectedChannel by remember { mutableStateOf(existing.firstOrNull()?.type ?: "bark") }
    var dingtalk by remember { mutableStateOf(existing.firstOrNull { it.type == "dingtalk" }?.url.orEmpty()) }
    var dingSecret by remember { mutableStateOf(existing.firstOrNull { it.type == "dingtalk" }?.secret.orEmpty()) }
    var feishu by remember { mutableStateOf(existing.firstOrNull { it.type == "feishu" }?.url.orEmpty()) }
    var feiSecret by remember { mutableStateOf(existing.firstOrNull { it.type == "feishu" }?.secret.orEmpty()) }
    var bark by remember { mutableStateOf(existing.firstOrNull { it.type == "bark" }?.url.orEmpty()) }
    var relayUrl by remember { mutableStateOf(SecureStore(context).get("relay_url").orEmpty()) }
    var relayToken by remember { mutableStateOf("") }
    var start by remember(settings.quietStart) { mutableStateOf(settings.quietStart) }; var end by remember(settings.quietEnd) { mutableStateOf(settings.quietEnd) }; var urgent by remember(settings.urgentKeywords) { mutableStateOf(settings.urgentKeywords) }
    var backupText by remember { mutableStateOf("") }; var backupPassword by remember { mutableStateOf("") }; var backupStatus by remember { mutableStateOf("") }
    Page("设置", modifier) {
        ChannelChoice(selectedChannel, { selectedChannel = it })
        SelectedChannelFields(selectedChannel, dingtalk, { dingtalk = it }, dingSecret, { dingSecret = it }, feishu, { feishu = it }, feiSecret, { feiSecret = it }, bark, { bark = it })
        Button({ SecureStore(context).put("channels", ChannelSender.serialize(selectedChannelConfig(selectedChannel, dingtalk, feishu, bark, dingSecret, feiSecret))) }) { Text("加密保存唯一渠道") }
        Spacer(Modifier.height(12.dp))
        Panel { Text("免打扰", fontWeight = FontWeight.Bold); Switch(settings.quietEnabled, { scope.launch { repository.setQuiet(it, start, end, urgent) } }); OutlinedTextField(start, { start = it }, label = { Text("开始时间 HH:mm") }); OutlinedTextField(end, { end = it }, label = { Text("结束时间 HH:mm") }); OutlinedTextField(urgent, { urgent = it }, label = { Text("紧急关键词，每行一个") }); Button({ scope.launch { repository.setQuiet(settings.quietEnabled, start, end, urgent) } }) { Text("保存免打扰") } }
        Spacer(Modifier.height(12.dp))
        Panel { Text("HTTPS 中转（可选）", fontWeight = FontWeight.Bold); OutlinedTextField(relayUrl, { relayUrl = it }, label = { Text("中转地址") }); OutlinedTextField(relayToken, { relayToken = it }, label = { Text("访问令牌") }); Button({ SecureStore(context).put("relay_url", relayUrl); if (relayToken.isNotBlank()) SecureStore(context).put("relay_token", relayToken) }, enabled = relayUrl.isBlank() || relayUrl.startsWith("https://")) { Text("保存中转配置") } }
        Spacer(Modifier.height(12.dp))
        Panel { Text("常驻状态通知", fontWeight = FontWeight.Bold); Switch(settings.persistentNotification, { scope.launch { repository.setPersistentNotification(it) } }) }
        Spacer(Modifier.height(12.dp))
        TemplateLibrary()
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("备份与恢复", fontWeight = FontWeight.Bold)
            Text("普通备份不含渠道密钥；完整备份使用密码加密。导入前会先校验格式、版本和密码。", color = Color.Gray)
            OutlinedTextField(backupPassword, { backupPassword = it }, label = { Text("完整备份密码") }, modifier = Modifier.fillMaxWidth())
            Row {
                Button({ scope.launch { backupText = ConfigBackup.export(context, false); backupStatus = "已生成普通备份" } }) { Text("普通备份") }
                Spacer(Modifier.width(8.dp))
                Button({ scope.launch { runCatching { ConfigBackup.export(context, true, backupPassword.toCharArray()) }.onSuccess { backupText = it; backupStatus = "已生成加密完整备份" }.onFailure { backupStatus = it.message.orEmpty() } } }, enabled = backupPassword.isNotBlank()) { Text("完整备份") }
            }
            OutlinedTextField(backupText, { backupText = it }, label = { Text("备份内容 / 导入内容") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button({ scope.launch { runCatching { ConfigBackup.import(context, backupText, backupPassword.takeIf(String::isNotBlank)?.toCharArray()) }.onSuccess { backupStatus = "导入成功" }.onFailure { backupStatus = it.message ?: "导入失败" } } }, enabled = backupText.isNotBlank()) { Text("校验并导入") }
            if (backupStatus.isNotBlank()) Text(backupStatus, color = Color.Gray)
        }
    }
}

@Composable private fun ChannelFields(dingtalk: String, onDing: (String) -> Unit, feishu: String, onFei: (String) -> Unit, bark: String, onBark: (String) -> Unit) {
    Panel {
        Text("推送渠道", fontWeight = FontWeight.Bold)
        Text("渠道地址必须使用 HTTPS，敏感配置使用 Android Keystore 保存。", color = Color.Gray)
        OutlinedTextField(dingtalk, onDing, label = { Text("钉钉 Webhook") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(feishu, onFei, label = { Text("飞书 Webhook") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bark, onBark, label = { Text("Bark 地址") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun ChannelChoice(selected: String, onSelect: (String) -> Unit) {
    Panel {
        Text("推送渠道（三选一）", fontWeight = FontWeight.Bold)
        listOf("dingtalk" to "钉钉", "feishu" to "飞书", "bark" to "Bark").forEach { (type, label) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); RadioButton(selected == type, { onSelect(type) }) }
        }
    }
}

@Composable private fun SelectedChannelFields(type: String, dingtalk: String, onDing: (String) -> Unit, dingSecret: String, onDingSecret: (String) -> Unit, feishu: String, onFei: (String) -> Unit, feiSecret: String, onFeiSecret: (String) -> Unit, bark: String, onBark: (String) -> Unit) {
    Panel {
        when (type) {
            "dingtalk" -> { OutlinedTextField(dingtalk, onDing, label = { Text("钉钉 Webhook") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(dingSecret, onDingSecret, label = { Text("加签密钥（可选）") }, modifier = Modifier.fillMaxWidth()) }
            "feishu" -> { OutlinedTextField(feishu, onFei, label = { Text("飞书 Webhook") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(feiSecret, onFeiSecret, label = { Text("加签密钥（可选）") }, modifier = Modifier.fillMaxWidth()) }
            else -> OutlinedTextField(bark, onBark, label = { Text("Bark 地址") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun TemplateSelector(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val templates by dao.templatesFlow().collectAsState(initial = TemplateCatalog.builtIns.map { TemplateEntity(it.id, it.name, it.title, it.body, true) })
    LaunchedEffect(Unit) { dao.ensureTemplates(AppSettingsRepository(context).current()) }
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }) { Text("模板：${templates.firstOrNull { it.id == selected }?.name ?: TemplateCatalog.byId(selected).name}") }
        DropdownMenu(expanded, { expanded = false }) {
            templates.forEach { template -> DropdownMenuItem({ Text(template.name) }, { onSelect(template.id); expanded = false }) }
        }
    }
}

@Composable private fun TemplateLibrary() {
    val context = LocalContext.current
    val dao = remember { RelayDatabase.get(context).relayDao() }
    val scope = rememberCoroutineScope()
    val templates by dao.templatesFlow().collectAsState(initial = emptyList())
    var editingId by remember { mutableStateOf<String?>(null) }; var name by remember { mutableStateOf("") }; var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("{{body}}\n{{time}}") }
    var preview by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TemplateEntity?>(null) }
    LaunchedEffect(Unit) { dao.ensureTemplates(AppSettingsRepository(context).current()) }
    Panel {
        Text("模板库", fontWeight = FontWeight.Bold)
        templates.forEach { template ->
            Text("${template.name}${if (template.builtIn) " · 内置" else ""}")
            if (!template.builtIn) Row {
                TextButton({ editingId = template.id; name = template.name; title = template.title; body = template.body }) { Text("编辑") }
                TextButton({ pendingDelete = template }) { Text("删除") }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("自定义模板名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text("标题模板") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("正文模板") }, modifier = Modifier.fillMaxWidth())
        Row {
            Button({ runCatching { val message = RelayMessage("", "预览应用", "标题", "正文", System.currentTimeMillis()); preview = "${MessageTemplate(title, body).renderTitle(message)}\n${MessageTemplate(title, body).renderBody(message)}" }.onFailure { preview = it.message.orEmpty() } }, enabled = title.isNotBlank()) { Text("预览") }
            Spacer(Modifier.width(8.dp))
            Button({ scope.launch { runCatching { MessageTemplate(title, body).renderTitle(RelayMessage("", "预览", "标题", "正文", System.currentTimeMillis())); dao.saveTemplate(TemplateEntity(editingId ?: "custom_${System.currentTimeMillis()}", name, title, body)); editingId = null; name = ""; title = ""; body = "{{body}}\n{{time}}"; preview = "" } } }, enabled = name.isNotBlank() && title.isNotBlank()) { Text(if (editingId == null) "创建自定义模板" else "保存修改") }
        }
        if (preview.isNotBlank()) Text(preview, color = Color.Gray)
    }
    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除自定义模板？") },
            text = { Text("使用“${template.name}”的规则将回退到通用模板。") },
            confirmButton = { TextButton({ scope.launch { dao.fallbackTemplate(template.id); dao.deleteCustomTemplate(template.id); pendingDelete = null } }) { Text("确认删除") } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("取消") } }
        )
    }
}

private fun channelName(type: String) = when (type) { "dingtalk" -> "钉钉"; "feishu" -> "飞书"; "bark" -> "Bark"; else -> type }
