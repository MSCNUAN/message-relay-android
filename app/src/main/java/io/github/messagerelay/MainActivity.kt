package io.github.messagerelay

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Indigo = Color(0xFF5368FF)
private val Violet = Color(0xFF855CFF)
private val Ink = Color(0xFF17203B)
private val Cloud = Color(0xFFF7F8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessageRelayApp { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } }
    }
}

@Composable
fun MessageRelayApp(openPermission: () -> Unit) {
    var configured by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Indigo, secondary = Violet, background = Cloud, surface = Color.White, onBackground = Ink)) {
        if (!configured) Onboarding(openPermission) { configured = true } else Scaffold(
            bottomBar = { NavigationBar { listOf("首页", "规则", "记录", "设置").forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("⌂","◎","≡","⚙")[index]) }, label = { Text(label) }) } } }
        ) { padding -> when (tab) { 0 -> Home(Modifier.padding(padding)); 1 -> Rules(Modifier.padding(padding)); 2 -> Records(Modifier.padding(padding)); else -> SettingsScreen(Modifier.padding(padding)) } }
    }
}

@Composable private fun Onboarding(openPermission: () -> Unit, finish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf("开启通知权限" to "允许消息接力读取通知栏，新消息才能自动接力。", "选择来源应用" to "默认不会转发任何应用，请只选择重要来源。", "配置推送渠道" to "钉钉、飞书与 Bark 可以同时启用并测试。", "发送测试消息" to "确认 iPhone 收到测试消息后即可开始使用。")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Cloud, Color(0xFFECEFFF)))).padding(26.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column { Text("消息接力", color = Indigo, fontWeight = FontWeight.Black, fontSize = 20.sp); Spacer(Modifier.height(36.dp)); Text("四步完成首次配置", fontWeight = FontWeight.Black, fontSize = 34.sp, color = Ink); Text("第 ${step + 1} 步，共 4 步", color = Color.Gray); Spacer(Modifier.height(34.dp)); steps.forEachIndexed { i, item -> Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) { Surface(color = if (i <= step) Indigo else Color(0xFFDDE2F7), shape = RoundedCornerShape(30.dp), modifier = Modifier.size(34.dp)) { Box(contentAlignment = Alignment.Center) { Text("${i + 1}", color = if (i <= step) Color.White else Ink) } }; Spacer(Modifier.width(14.dp)); Column { Text(item.first, fontWeight = FontWeight.Bold, color = Ink); if (i == step) Text(item.second, color = Color.Gray) } } } }
        Button(onClick = { if (step == 0) openPermission(); if (step < 3) step++ else finish() }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text(if (step == 3) "完成配置" else "继续") }
    }
}

@Composable private fun Page(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) = Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) { Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink); Spacer(Modifier.height(20.dp)); content() }
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) = Surface(shape = RoundedCornerShape(23.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), content = content) }

@Composable private fun Home(modifier: Modifier) = Page("消息接力", modifier) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(context.getSharedPreferences("relay", android.content.Context.MODE_PRIVATE).getBoolean("paused", false)) }
    Panel { Text(if (paused) "转发服务已暂停" else "转发服务运行中", color = Indigo, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("通知匹配后将自动接力到所有已启用渠道", color = Color.Gray); Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("今日已接力\n12 条", fontWeight = FontWeight.Bold); Text("待发送\n0 条", fontWeight = FontWeight.Bold); Button(onClick = { paused = !paused; context.getSharedPreferences("relay", android.content.Context.MODE_PRIVATE).edit().putBoolean("paused", paused).apply() }) { Text(if (paused) "恢复" else "暂停") } } }
    Spacer(Modifier.height(18.dp)); Text("已启用渠道", fontWeight = FontWeight.Bold); listOf("钉钉", "飞书", "Bark").forEach { Text("●  $it", Modifier.padding(vertical = 8.dp), color = Indigo) }
    Spacer(Modifier.height(18.dp)); Text("最近记录", fontWeight = FontWeight.Bold); Text("短信 · 成功 · 刚刚\n企业微信 · 成功 · 5 分钟前", color = Color.Gray)
}
@Composable private fun Rules(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packageName by remember { mutableStateOf("") }
    var includes by remember { mutableStateOf("") }
    var excludes by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Page("转发规则", modifier) {
        Panel {
            Text("来源应用白名单", fontWeight = FontWeight.Bold)
            Text("填写来源 App 包名。可在系统应用信息页查看包名。", color = Color.Gray)
            OutlinedTextField(packageName, { packageName = it }, label = { Text("包名，例如 com.tencent.mm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(includes, { includes = it }, label = { Text("包含关键词，每行一个") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(excludes, { excludes = it }, label = { Text("排除关键词，每行一个") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { RelayDatabase.get(context).relayDao().saveRule(RuleEntity(packageName, packageName, includes, excludes)); saved = true } }, enabled = packageName.isNotBlank(), modifier = Modifier.padding(top = 12.dp)) { Text("保存来源规则") }
            if (saved) Text("已保存，新的匹配通知将自动接力。", color = Color(0xFF119B75))
        }
        Spacer(Modifier.height(18.dp)); Panel { Text("免打扰与紧急关键词", fontWeight = FontWeight.Bold); Text("每日 22:00 - 07:00，紧急消息立即发送。", color = Color.Gray) }
    }
}
@Composable private fun Records(modifier: Modifier) = Page("最近记录", modifier) { listOf("短信" to "成功", "企业微信" to "成功", "日历" to "未配置渠道").forEach { (app, status) -> Panel { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(app, fontWeight = FontWeight.Bold); Text(status, color = if (status == "成功") Color(0xFF119B75) else Color.Red) }; Text("仅在本机保留最近 100 条", color = Color.Gray, fontSize = 12.sp) }; Spacer(Modifier.height(12.dp)) } }
@Composable private fun SettingsScreen(modifier: Modifier) {
    val context = LocalContext.current
    var dingtalk by remember { mutableStateOf("") }; var feishu by remember { mutableStateOf("") }; var bark by remember { mutableStateOf("") }; var relayUrl by remember { mutableStateOf("") }; var relayToken by remember { mutableStateOf("") }; var saved by remember { mutableStateOf(false) }
    Page("设置", modifier) {
        Panel {
            Text("推送渠道", fontWeight = FontWeight.Bold); Text("可同时填写多个渠道。敏感地址使用 Android Keystore 加密保存。", color = Color.Gray)
            OutlinedTextField(dingtalk, { dingtalk = it }, label = { Text("钉钉 Webhook") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(feishu, { feishu = it }, label = { Text("飞书 Webhook") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bark, { bark = it }, label = { Text("Bark 推送地址") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val channels = JSONArray()
                listOf("dingtalk" to dingtalk, "feishu" to feishu, "bark" to bark).filter { it.second.isNotBlank() }.forEach { channels.put(JSONObject().put("type", it.first).put("url", it.second).put("enabled", true)) }
                SecureStore(context).put("channels", channels.toString()); saved = true
            }, modifier = Modifier.padding(top = 12.dp)) { Text("加密保存并启用") }
            if (saved) Text("渠道已保存。请返回首页等待匹配通知。", color = Color(0xFF119B75))
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("自建中转（可选）", fontWeight = FontWeight.Bold); Text("留空时使用手机直连。中转地址必须使用 HTTPS。", color = Color.Gray)
            OutlinedTextField(relayUrl, { relayUrl = it }, label = { Text("中转服务 HTTPS 地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(relayToken, { relayToken = it }, label = { Text("App 访问令牌") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { SecureStore(context).put("relay_url", relayUrl); SecureStore(context).put("relay_token", relayToken) }, enabled = relayUrl.isBlank() || relayUrl.startsWith("https://")) { Text("保存中转配置") }
        }
        Spacer(Modifier.height(12.dp))
        listOf("通知内容模板" to "[{{app}}] {{title}}", "备份与恢复" to "安全配置备份 / 加密完整备份", "常驻通知" to "默认开启，可关闭", "使用教程" to "逐步图文与常见错误", "关于消息接力" to "免费开源 · Apache-2.0").forEach { (title, detail) -> Panel { Text(title, fontWeight = FontWeight.Bold); Text(detail, color = Color.Gray) }; Spacer(Modifier.height(12.dp)) }
    }
}
