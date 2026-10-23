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
    val requestBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        bluetoothText = BluetoothInspector.describe(context)
    }

    fun refresh() {
        carText = NiuInspector.describe(context)
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
            Text("LSPosed 首版仅在小牛 APP 5.19.0 启动时写入本地兼容检测标记，不隐藏控件、不 Hook 车控或蓝牙钥匙。界面精简将在实机页面结构确认后逐项启用。")
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
