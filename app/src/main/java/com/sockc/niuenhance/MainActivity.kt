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
import com.sockc.niuenhance.diagnostics.NiuInspector
import com.sockc.niuenhance.diagnostics.UiScanInspector
import com.sockc.niuenhance.UiMenuCatalog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Dashboard()
            }
        }
    }
}

@Composable
private fun Dashboard() {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val installedVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    }
    var bluetoothText by remember { mutableStateOf("点击刷新检测蓝牙") }
    var musicText by remember { mutableStateOf("点击刷新检测音乐") }
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
    }

    LaunchedEffect(Unit) { refresh() }

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
