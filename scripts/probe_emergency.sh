#!/system/bin/sh
echo "===== 1. 当前连接状态 ====="
dumpsys bluetooth_manager | grep -E "C6:AE|Connected|Active|state.*:" | head -15
echo "===== 2. 最近连接/断开日志 ====="
logcat -d -b all -t 300 | grep -iE "C6:AE|connect|disconnect|LeAudio|CSIP|bond|pair|ACL" | tail -30
echo "===== 3. 当前 policy ====="
dumpsys bluetooth_manager | grep "C6:AE" | grep "profile connection" | head -1
echo "===== 4. LeAudio 状态 ====="
dumpsys bluetooth_manager | grep -A 8 "LeAudio Manager" | head -10
echo "===== 5. LSPosed 模块状态 ====="
logcat -d -b all -t 100 | grep -i "LeAudioUnlock" | tail -10
echo "===== done ====="
