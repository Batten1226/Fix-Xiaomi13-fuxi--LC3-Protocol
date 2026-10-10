#!/system/bin/sh
echo "===== 1. LeAudio Manager ====="
dumpsys bluetooth_manager | grep -A 22 "LeAudio Manager"
echo "===== 2. C6:AE policy & state ====="
dumpsys bluetooth_manager | grep "C6:AE" | head -5
echo "===== 3. 当前音频设备/codec ====="
dumpsys audio | grep -iE "Current device|codec|format|lc3|ldhc|lhdc|ble" | head -20
echo "===== 4. profile init ====="
logcat -d -b all | grep "AdapterServiceConfig: init" | grep -iE "LE_AUDIO|VOLUME|CSIP" | tail -10
echo "===== 5. 最近 LE Audio 日志 ====="
logcat -d -t 100 | grep -iE "LeAudio|le_audio|LC3|ldhc|lhdc|codec|BapUnicast|group|ASE" | tail -30
echo "===== 6. 开发者选项 Controller 类名探测 ====="
# dump Settings 进程里 leaudio 相关类
logcat -d -t 50 | grep -iE "PreferenceController|LeAudio|Offload|DeveloperOptions" | tail -15
echo "===== 7. 设备上的 settings provider 里 le_audio 键 ====="
settings list global | grep -iE "le_audio|leaudio"
settings list secure | grep -iE "le_audio|leaudio|editing"
echo "===== 8. MiuiCloudControl / 白名单 ====="
logcat -d -t 200 | grep -iE "CloudControl|MiuiCloudControl|whiteList|allowlist|SwitchLeAudio|ADV_AUDIO" | tail -20
echo "===== done ====="
