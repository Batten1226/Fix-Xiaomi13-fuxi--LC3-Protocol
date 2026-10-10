package dev.leaudio.unlock

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader

class MainActivity : Activity() {

    private lateinit var logText: TextView
    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "LE Audio Unlock"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        val status = TextView(this).apply {
            text = "模块已加载（若 LSPosed 已启用）\n目标：解锁 LE Audio / LC3 / 硬件分流"
            textSize = 13f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(status)

        val btnCollect = Button(this).apply {
            text = "📋 采集诊断日志"
            setOnClickListener { collectLogs() }
        }
        layout.addView(btnCollect)

        val btnCopy = Button(this).apply {
            text = "📋 复制日志到剪贴板"
            setOnClickListener { copyLogs() }
        }
        layout.addView(btnCopy)

        val btnShare = Button(this).apply {
            text = "📤 分享日志"
            setOnClickListener { shareLogs() }
        }
        layout.addView(btnShare)

        logText = TextView(this).apply {
            text = "点击「采集诊断日志」开始"
            textSize = 11f
            setPadding(0, 24, 0, 0)
        }
        scroll = ScrollView(this).apply {
            addView(logText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        layout.addView(scroll)

        setContentView(layout)
    }

    private fun collectLogs() {
        logText.text = "正在采集...\n"
        Thread {
            val sb = StringBuilder()
            sb.appendLine("===== LE Audio Unlock 诊断日志 =====")
            sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            sb.appendLine()

            // 1. 系统属性
            sb.appendLine("===== 1. LE Audio 相关属性 =====")
            sb.appendLine(exec("getprop | grep -iE 'leaudio|le_audio|bap|csip|vc\\.controller'"))

            // 2. 蓝牙状态
            sb.appendLine("===== 2. LeAudio Manager =====")
            sb.appendLine(exec("dumpsys bluetooth_manager | grep -A 25 'LeAudio Manager'"))

            // 3. 连接策略
            sb.appendLine("===== 3. 耳机连接策略 =====")
            sb.appendLine(exec("dumpsys bluetooth_manager | grep 'profile connection policy'"))

            // 4. CIS 状态
            sb.appendLine("===== 4. CIS 状态 =====")
            sb.appendLine(exec("dumpsys bluetooth_manager | grep -A 6 'id.*active.*dir.*cis_id'"))

            // 5. 音频路由
            sb.appendLine("===== 5. 音频输出 =====")
            sb.appendLine(exec("dumpsys audio | grep 'Current device'"))

            // 6. 开发者选项设置
            sb.appendLine("===== 6. Settings 值 =====")
            sb.appendLine(exec("settings list global | grep -i le_audio"))
            sb.appendLine(exec("settings list secure | grep -i le_audio"))

            // 7. LSPosed 日志
            sb.appendLine("===== 7. LSPosed 模块日志 =====")
            sb.appendLine(exec("grep LeAudioUnlock /data/adb/lspd/log/verbose_*.log | tail -50"))

            // 8. 蓝牙 profile init
            sb.appendLine("===== 8. Profile 初始化 =====")
            sb.appendLine(exec("logcat -d -b all | grep 'AdapterServiceConfig: init' | tail -30"))

            handler.post {
                logText.text = sb.toString()
            }
        }.start()
    }

    private fun copyLogs() {
        val text = logText.text.toString()
        if (text == "点击「采集诊断日志」开始" || text == "正在采集...") {
            Toast.makeText(this, "请先采集日志", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LE Audio Unlock Logs", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun shareLogs() {
        val text = logText.text.toString()
        if (text == "点击「采集诊断日志」开始" || text == "正在采集...") {
            Toast.makeText(this, "请先采集日志", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "LE Audio Unlock 诊断日志")
        }
        startActivity(Intent.createChooser(intent, "分享日志"))
    }

    private fun exec(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(process.inputStream.reader())
            val result = reader.readText()
            process.waitFor()
            reader.close()
            result.trim()
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
