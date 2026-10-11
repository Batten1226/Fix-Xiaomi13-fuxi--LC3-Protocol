package dev.leaudio.unlock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader

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

    var logText by remember { mutableStateOf("点击「采集诊断日志」开始") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    // LC3 配置
    var sampleRate by remember { mutableStateOf(48000) }
    var bitDepth by remember { mutableStateOf(16) }
    var frameDuration by remember { mutableStateOf(10) }
    var channels by remember { mutableStateOf(2) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ---- LC3 配置 ----
            Text("LC3 编码配置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "调整 LE Audio 的 LC3 编码参数。修改后需要重启蓝牙生效。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            // 采样率
            ConfigDropdown("采样率", listOf("8 kHz", "16 kHz", "24 kHz", "32 kHz", "44.1 kHz", "48 kHz"), 5) {
                sampleRate = intArrayOf(8000, 16000, 24000, 32000, 44100, 48000)[it]
            }
            Spacer(Modifier.height(6.dp))
            // 位深
            ConfigDropdown("位深", listOf("16-bit", "24-bit"), 0) {
                bitDepth = intArrayOf(16, 24)[it]
            }
            Spacer(Modifier.height(6.dp))
            // 帧时长
            ConfigDropdown("帧时长", listOf("7.5 ms", "10 ms"), 1) {
                frameDuration = intArrayOf(8, 10)[it]
            }
            Spacer(Modifier.height(6.dp))
            // 声道
            ConfigDropdown("声道", listOf("单声道", "立体声"), 1) {
                channels = intArrayOf(1, 2)[it]
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val cmd = "resetprop persist.bluetooth.leaudio.sample_rate $sampleRate; " +
                                "resetprop persist.bluetooth.leaudio.bit_depth $bitDepth; " +
                                "resetprop persist.bluetooth.leaudio.frame_duration $frameDuration; " +
                                "resetprop persist.bluetooth.leaudio.channels $channels; " +
                                "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                        val result = withContext(Dispatchers.IO) { exec(cmd) }
                        logText = "LC3 配置已应用:\n" +
                                "采样率: ${sampleRate}Hz  位深: ${bitDepth}bit\n" +
                                "帧时长: ${frameDuration}ms  声道: $channels\n\n$result"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("应用 LC3 配置")
            }

            Spacer(Modifier.height(16.dp))

            // ---- 诊断 ----
            Text("诊断与反馈", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "遇到问题时采集日志，复制后发到 GitHub Issue。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(progress, style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        running = true
                        progress = "正在采集..."
                        scope.launch {
                            logText = withContext(Dispatchers.IO) { collectLogs(sampleRate, bitDepth, frameDuration, channels) }
                            progress = ""
                            running = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("采集日志")
                }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("logs", logText))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制")
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logText)
                        }, "分享日志"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("分享")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 日志输出 ----
            Text("日志输出", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ConfigDropdown(label: String, options: List<String>, defaultIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(defaultIndex) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(options[selected])
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            selected = index
                            expanded = false
                            onSelect(index)
                        }
                    )
                }
            }
        }
    }
}

private fun collectLogs(sr: Int, bd: Int, fd: Int, ch: Int): String {
    val sb = StringBuilder()
    sb.appendLine("===== LE Audio Unlock 诊断日志 =====")
    sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
    sb.appendLine("LC3: ${sr}Hz / ${bd}bit / ${fd}ms / ${ch}ch")
    sb.appendLine()
    sb.appendLine("===== 1. LE Audio 属性 =====")
    sb.appendLine(exec("getprop | grep -iE 'leaudio|le_audio|bap|csip|vc\\.controller'"))
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
