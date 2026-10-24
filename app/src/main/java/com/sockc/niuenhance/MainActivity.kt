package com.sockc.niuenhance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sockc.niuenhance.diagnostics.BluetoothInspector
import com.sockc.niuenhance.diagnostics.MediaInspector
import com.sockc.niuenhance.diagnostics.AudioRouteInspector
import com.sockc.niuenhance.diagnostics.AudioToneTest
import com.sockc.niuenhance.diagnostics.NiuInspector
import com.sockc.niuenhance.diagnostics.UiScanInspector
import com.sockc.niuenhance.UiMenuCatalog

class MainActivity : ComponentActivity() {
    private var resumeCounter by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCounter++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Dashboard(resumeCounter)
            }
        }
    }
}

@Composable
private fun Dashboard(resumeCounter: Int) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val installedVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    }
    var bluetoothText by remember { mutableStateOf("点击刷新检测蓝牙") }
    var musicText by remember { mutableStateOf("点击刷新检测音乐") }
    var audioRouteText by remember { mutableStateOf(AudioRouteInspector.describe(context)) }
    var testToneText by remember { mutableStateOf("尚未测试 · 仅在停车时使用") }
    var playingTest by remember { mutableStateOf(AudioToneTest.isActive()) }
    var carText by remember { mutableStateOf(NiuInspector.describe(context)) }
    var uiScanText by remember { mutableStateOf(UiScanInspector.summary(context)) }
    var uiScanDetail by remember { mutableStateOf(UiScanInspector.detail(context)) }
    var menuLabels by remember { mutableStateOf(UiScanInspector.discovered(context)) }
    var scanExpanded by remember { mutableStateOf(false) }
    val uiPrefs = remember { context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE) }
    val menuFlags = remember {
        mutableStateMapOf<String, Boolean>().apply {
            UiMenuCatalog.items.forEach { put(it.key, uiPrefs.getBoolean(it.key, false)) }
        }
    }
    val requestBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        bluetoothText = BluetoothInspector.describe(context)
    }

    fun refresh() {
        carText = NiuInspector.describe(context)
        uiScanText = UiScanInspector.summary(context)
        uiScanDetail = UiScanInspector.detail(context)
        menuLabels = UiScanInspector.discovered(context)
        bluetoothText = BluetoothInspector.describe(context)
        musicText = MediaInspector.describe(context)
        audioRouteText = AudioRouteInspector.describe(context)
    }

    LaunchedEffect(resumeCounter) { refresh() }
    DisposableEffect(Unit) {
        onDispose { AudioToneTest.stop() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("NiuEnhance", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("2026 FX 风速 · V$installedVersion 诊断版", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Block("车辆与兼容性") {
            Text(carText)
            OutlinedButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage("com.niu.cloud")
                if (intent != null) context.startActivity(intent)
            }) { Text("打开小牛官方 APP") }
        }
        Block("原车音响诊断 · 优先排查") {
            Text(audioRouteText)
            Text("先连接 NIU Link 多媒体蓝牙，停车时测试。测试音走正常媒体音频链路，并不解除原车功放限幅。",
                style = MaterialTheme.typography.bodySmall)
            Text(testToneText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !playingTest, onClick = {
                    audioRouteText = AudioRouteInspector.describe(context)
                    if (!AudioRouteInspector.hasBluetoothMediaRoute(context)) {
                        testToneText = "当前未检测到蓝牙媒体输出，请先连接 FX 多媒体蓝牙并开始播放音乐。"
                    } else {
                        testToneText = "准备播放 440Hz、3秒固定低电平测试音。确保车辆停车，先暂停其他音乐。"
                        playingTest = AudioToneTest.start { result ->
                            (context as? android.app.Activity)?.runOnUiThread {
                                playingTest = false
                                testToneText = result + "。请比较与音乐播放的响度。"
                            }
                        }
                    }
                }) { Text("播放3秒测试音") }
                OutlinedButton(enabled = playingTest, onClick = {
                    AudioToneTest.stop()
                    testToneText = "正在停止测试音"
                }) { Text("停止") }
            }
            OutlinedButton(onClick = {
                audioRouteText = AudioRouteInspector.describe(context)
            }) { Text("刷新音频路由") }
            Text("安全说明：测试信号峰值约 -13 dBFS，不自动提高系统音量。若提示音与测试音均明显偏小，需重点检查原车功放/扬声器；NiuEnhance 无法直接提高车辆本身的提示音。",
                style = MaterialTheme.typography.bodySmall)
        }
        Block("官方 APP 界面精简") {
            Text(uiScanText)
            OutlinedButton(onClick = { scanExpanded = !scanExpanded }) {
                Text(if (scanExpanded) "收起详细诊断" else "查看详细诊断")
            }
            if (scanExpanded) {
                Text(uiScanDetail, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("自动发现的可配置菜单", fontWeight = FontWeight.SemiBold)
            val visibleMenus = UiMenuCatalog.items.filter { item ->
                item.label in menuLabels || menuFlags[item.key] == true ||
                    item.key == "hide_zoo" || item.key == "hide_team"
            }
            if (menuLabels.isEmpty()) {
                Text("尚未匹配「我的」页面。保留原有两项开关，扫描成功后自动显示其他菜单。",
                    style = MaterialTheme.typography.bodySmall)
            }
            visibleMenus.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label)
                        Text(UiScanInspector.menuStatus(context, item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = menuFlags[item.key] == true, onCheckedChange = { enabled ->
                        menuFlags[item.key] = enabled
                        uiPrefs.edit()
                            .putBoolean(item.key, enabled)
                            .putLong(item.key + "_changed_at", System.currentTimeMillis())
                            .apply()
                        uiScanText = UiScanInspector.summary(context)
                        uiScanDetail = UiScanInspector.detail(context)
                    })
                }
            }
            Text("所有菜单默认保留。我的设备、消息通知和我的订单属于重要入口，建议保持显示。" +
                "仅在官方 APP 5.19.0「我的」页面找到唯一、独立、安全的菜单容器时才隐藏。" +
                "切换后重新进入官方 APP，再回来刷新确认。",
                style = MaterialTheme.typography.bodySmall)
        }
        Block("双蓝牙诊断") {
            Text(bluetoothText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 31 &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                        PackageManager.PERMISSION_GRANTED
                    ) requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else bluetoothText = BluetoothInspector.describe(context)
                }) { Text("检测蓝牙") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("蓝牙设置") }
            }
        }
        Block("音乐中心") {
            Text(musicText)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { MediaInspector.control(context, "previous"); musicText = MediaInspector.describe(context) }) { Text("上一首") }
                OutlinedButton(onClick = { MediaInspector.control(context, "toggle"); musicText = MediaInspector.describe(context) }) { Text("播放/暂停") }
                OutlinedButton(onClick = { MediaInspector.control(context, "next"); musicText = MediaInspector.describe(context) }) { Text("下一首") }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("授权媒体会话读取") }
            Text("需要系统通知使用权；不读取或上传消息内容。", style = MaterialTheme.typography.bodySmall)
        }
        Block("安全说明") {
            Text("仅对小牛官方 APP 5.19.0「我的」页面中可单独确认的菜单进行可选隐藏。无法确认时跳过；车况、蓝牙钥匙、导航、动力和 OTA 均不修改。")
            Text("所有数据保存在本机；无后台上传和远程控制。", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("刷新全部状态") }
    }
}

@Composable
private fun Block(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                body()
            }
        )
    }
}
