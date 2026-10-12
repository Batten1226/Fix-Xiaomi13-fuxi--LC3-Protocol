package dev.leaudio.unlock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader

// 薄荷绿配色
private val MintBg = Color(0xFFB2E6DC)
private val MintLight = Color(0xFFD4F0EA)
private val CardWhite = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF1A3A3A)
private val TextGray = Color(0xFF6B8E8E)
private val AccentGreen = Color(0xFF2E8B7A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LeAudioScreen()
            }
        }
    }
}

@Composable
fun LeAudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("leaudio", Context.MODE_PRIVATE) }

    // 从 SharedPreferences 读取保存的配置
    var sampleRate by remember { mutableStateOf(prefs.getInt("sample_rate", 48000)) }
    var bitDepth by remember { mutableStateOf(prefs.getInt("bit_depth", 16)) }
    var frameDuration by remember { mutableStateOf(prefs.getInt("frame_duration", 10)) }
    var channels by remember { mutableStateOf(prefs.getInt("channels", 2)) }
    var gameMode by remember { mutableStateOf(prefs.getBoolean("game_mode", false)) }

    var logText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var configApplied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MintBg, MintLight)
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 头部标题 =====
        Column(modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 0.dp)) {
            Text(
                "LE 音频流控制",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "LC3 编码配置 · Game 模式 · 诊断",
                fontSize = 13.sp,
                color = TextGray
            )
        }

        // ===== 流配置卡片 =====
        Spacer(Modifier.height(16.dp))
        ConfigCard(
            title = "当前配置",
            value = "${sampleRate}Hz · ${bitDepth}bit · ${frameDuration}ms · ${channels}ch",
            subtitle = if (gameMode) "游戏模式 · Low Latency" else "媒体模式 · High Reliability"
        )

        // ===== 状态网格 =====
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(24.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard("编码", "LC3", Modifier.weight(1f))
            StatusCard("分流", "ADSP", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(24.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard("输出", "BLE", Modifier.weight(1f))
            StatusCard("模式", if (gameMode) "Game" else "Media", Modifier.weight(1f))
        }

        // ===== 设置区域 =====
        Spacer(Modifier.height(24.dp))
        Text(
            "⚙  设置",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(24.dp, 0.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (configApplied) "已保存 · 蓝牙重启后生效" else "保存选择，手动重启系统后应用",
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(24.dp, 0.dp)
        )

        // 游戏模式开关
        Spacer(Modifier.height(12.dp))
        SettingsToggle(
            title = "强制开启游戏模式",
            subtitle = "切换媒体模式和游戏模式",
            checked = gameMode,
            onChange = {
                gameMode = it
                prefs.edit().putBoolean("game_mode", it).apply()
                configApplied = true
                scope.launch {
                    val cmd = if (it) {
                        "resetprop persist.bluetooth.leaudio.context_type 8; " +
                        "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                    } else {
                        "resetprop persist.bluetooth.leaudio.context_type 4; " +
                        "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                    }
                    withContext(Dispatchers.IO) { exec(cmd) }
                }
            }
        )

        // 采样率
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            title = "采样率",
            options = listOf("8 kHz", "16 kHz", "24 kHz", "32 kHz", "44.1 kHz", "48 kHz"),
            values = intArrayOf(8000, 16000, 24000, 32000, 44100, 48000),
            current = sampleRate,
            onChange = {
                sampleRate = it
                prefs.edit().putInt("sample_rate", it).apply()
                configApplied = true
            }
        )

        // 位深
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            title = "位深",
            options = listOf("16-bit", "24-bit"),
            values = intArrayOf(16, 24),
            current = bitDepth,
            onChange = {
                bitDepth = it
                prefs.edit().putInt("bit_depth", it).apply()
                configApplied = true
            }
        )

        // 帧时长
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            title = "帧时长",
            options = listOf("7.5 ms", "10 ms"),
            values = intArrayOf(8, 10),
            current = frameDuration,
            onChange = {
                frameDuration = it
                prefs.edit().putInt("frame_duration", it).apply()
                configApplied = true
            }
        )

        // 声道
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            title = "声道",
            options = listOf("单声道", "立体声"),
            values = intArrayOf(1, 2),
            current = channels,
            onChange = {
                channels = it
                prefs.edit().putInt("channels", it).apply()
                configApplied = true
            }
        )

        // 应用按钮
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.padding(24.dp, 0.dp)) {
            Button(
                onClick = {
                    configApplied = true
                    scope.launch {
                        val cmd = "resetprop persist.bluetooth.leaudio.sample_rate $sampleRate; " +
                                "resetprop persist.bluetooth.leaudio.bit_depth $bitDepth; " +
                                "resetprop persist.bluetooth.leaudio.frame_duration $frameDuration; " +
                                "resetprop persist.bluetooth.leaudio.channels $channels; " +
                                "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                        withContext(Dispatchers.IO) { exec(cmd) }
                        logText = "配置已应用: ${sampleRate}Hz / ${bitDepth}bit / ${frameDuration}ms / ${channels}ch"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("应用配置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ===== 诊断区域 =====
        Spacer(Modifier.height(24.dp))
        Text(
            "📋  诊断与反馈",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(24.dp, 0.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(24.dp, 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiagButton("采集日志", Modifier.weight(1f)) {
                running = true
                progress = "正在采集..."
                scope.launch {
                    logText = withContext(Dispatchers.IO) {
                        collectLogs(sampleRate, bitDepth, frameDuration, channels, gameMode)
                    }
                    progress = ""
                    running = false
                }
            }
            DiagButton("复制", Modifier.weight(1f)) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("logs", logText))
            }
            DiagButton("分享", Modifier.weight(1f)) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, logText)
                }, "分享日志"))
            }
        }

        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.padding(24.dp, 0.dp).fillMaxWidth()
            )
        }

        // ===== 日志输出 =====
        if (logText.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SelectionContainer {
                Text(
                    text = logText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextDark,
                    modifier = Modifier.padding(24.dp, 0.dp, 24.dp, 32.dp)
                )
            }
        } else {
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ===== 组件 =====

@Composable
fun ConfigCard(title: String, value: String, subtitle: String) {
    Column(
        modifier = Modifier
            .padding(24.dp, 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(20.dp)
    ) {
        Text(title, fontSize = 13.sp, color = TextGray)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = AccentGreen)
    }
}

@Composable
fun StatusCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(16.dp)
    ) {
        Text(label, fontSize = 12.sp, color = TextGray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
    }
}

@Composable
fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(24.dp, 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(subtitle, fontSize = 12.sp, color = TextGray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
        )
    }
}

@Composable
fun SettingsDropdown(
    title: String,
    options: List<String>,
    values: IntArray,
    current: Int,
    onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("leaudio", Context.MODE_PRIVATE) }
    val currentIndex = values.indexOf(current).coerceAtLeast(0)
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(24.dp, 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.weight(1f))
        Box {
            Text(
                options[currentIndex],
                fontSize = 14.sp,
                color = AccentGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MintLight)
                    .padding(12.dp, 6.dp)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            onChange(values[i])
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardWhite),
        contentPadding = PaddingValues(0.dp, 10.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
    }
}

// ===== 日志采集 =====

private fun collectLogs(sr: Int, bd: Int, fd: Int, ch: Int, game: Boolean): String {
    val sb = StringBuilder()
    sb.appendLine("===== LE Audio Unlock 诊断日志 =====")
    sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
    sb.appendLine("配置: ${sr}Hz / ${bd}bit / ${fd}ms / ${ch}ch / Game=$game")
    sb.appendLine()
    sb.appendLine("===== 1. LE Audio 属性 =====")
    sb.appendLine(exec("getprop | grep -iE 'leaudio|le_audio|bap|csip'"))
    sb.appendLine("===== 2. LeAudio Manager =====")
    sb.appendLine(exec("dumpsys bluetooth_manager | grep -A 25 'LeAudio Manager'"))
    sb.appendLine("===== 3. 连接策略 =====")
    sb.appendLine(exec("dumpsys bluetooth_manager | grep 'profile connection policy'"))
    sb.appendLine("===== 4. CIS 状态 =====")
    sb.appendLine(exec("dumpsys bluetooth_manager | grep -A 6 'id.*active.*dir.*cis_id'"))
    sb.appendLine("===== 5. 音频路由 =====")
    sb.appendLine(exec("dumpsys audio | grep 'Current device'"))
    sb.appendLine("===== 6. LSPosed 日志 =====")
    sb.appendLine(exec("grep LeAudioUnlock /data/adb/lspd/log/verbose_*.log | tail -50"))
    sb.appendLine("===== 7. Profile 初始化 =====")
    sb.appendLine(exec("logcat -d -b all | grep 'AdapterServiceConfig: init' | tail -30"))
    return sb.toString()
}

private fun exec(cmd: String): String = try {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    val result = process.inputStream.bufferedReader().use(BufferedReader::readText)
    process.waitFor()
    result.trim()
} catch (e: Exception) {
    "执行失败: ${e.message}"
}
