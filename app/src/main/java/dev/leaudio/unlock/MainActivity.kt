package dev.leaudio.unlock

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = """
                LE Audio Unlock
                ----------------
                LSPosed 模块已在后台生效（若已启用）。
                查看 Hook 日志：
                  adb logcat -s LeAudioUnlock

                当前目标：
                  1. 开发者选项开关去灰
                  2. 强制 LE_AUDIO profile enabled
                  3. 绕过 MIUI 云控白名单
                  4. 放行 isLeAudioSupported
            """.trimIndent()
            setPadding(48, 48, 48, 48)
            textSize = 14f
        }
        setContentView(tv)
    }
}
