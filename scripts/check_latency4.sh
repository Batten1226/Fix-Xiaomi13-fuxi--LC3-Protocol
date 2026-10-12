#!/system/bin/sh
echo "==== 当前 stream 配置详情 ===="
dumpsys bluetooth_manager | grep -A 20 "LeAudio Manager:" | grep -E "Configuration|stream|speed|hal"
echo "==== Game 场景的 Low_Latency QoS ===="
grep -B 3 -A 25 'QoS_Config_Low_Latency' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | grep -E 'name|sdu|latency|rtn|interval|target|presentation|framing' | head -15
echo "==== Media 场景的默认 QoS ===="
grep -B 3 -A 25 'QoS_Config_High_Reliability' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | grep -E 'name|sdu|latency|rtn|interval|target|presentation|framing' | head -15
echo "==== context mask ===="
dumpsys bluetooth_manager | grep -E "context type mask|Context"
