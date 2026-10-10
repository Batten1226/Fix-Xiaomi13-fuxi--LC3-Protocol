#!/system/bin/sh
# 解锁开发者选项 LE Audio 开关
# 根因：Controller 里 isLeAudioSupported() == 10 才放行
# 10 是 FEATURE_SUPPORTED（MIUI/Android 16 的常量）

echo "===== 1. 设属性 ====="
resetprop ro.bluetooth.leaudio_switcher.supported true
resetprop ro.bluetooth.leaudio.le_audio_connection_by_default true
resetprop persist.bluetooth.leaudio.bypass_allow_list true
resetprop ro.bluetooth.leaudio_offload.supported true

echo "===== 2. 验证 ====="
getprop ro.bluetooth.leaudio_switcher.supported
getprop ro.bluetooth.leaudio.le_audio_connection_by_default
getprop persist.bluetooth.leaudio.bypass_allow_list

echo "===== 3. isLeAudioSupported 当前值 ====="
# 通过 dumpsys 看
dumpsys bluetooth_manager | grep -iE "isLeAudioSupported|LeAudioSupport" | head -5

echo "===== 完成 ====="
echo "请在 LSPosed 管理器启用 LE Audio Unlock 模块，然后重启蓝牙"
