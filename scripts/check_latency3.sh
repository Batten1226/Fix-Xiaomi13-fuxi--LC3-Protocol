#!/system/bin/sh
echo "==== Game QoS 具体参数 ===="
grep -B 5 -A 20 'QoS_Config_Low_Latency' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | grep -E 'sdu|latency|rtn|interval|presentation' | head -10
echo "==== 当前 CIS 延迟 ===="
dumpsys bluetooth_manager | grep -E "cis.*latency|sdu.*latency|SDU|interval" | head -10
echo "==== 场景列表 ===="
grep '"name"' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_scenarios.json | head -15
echo "==== 当前 context ===="
dumpsys bluetooth_manager | grep -E "Configuration:|context|Context" | head -5
