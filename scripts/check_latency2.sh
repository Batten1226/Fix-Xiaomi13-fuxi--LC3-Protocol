#!/system/bin/sh
echo "==== Game 场景详情 ===="
grep -A 20 '"name": "Game"' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_scenarios.json | head -25
echo "==== Low_Latency QoS 参数 ===="
grep -B 2 -A 15 'QoS_Config_Low_Latency' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | head -40
echo "==== 默认 QoS 参数 ===="
grep -B 2 -A 15 'QoS_Config_High_Reliability\|QoS_Config_Balanced' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | head -40
echo "==== 当前 Configuration ===="
dumpsys bluetooth_manager | grep -E "Configuration:|stream.*speed|data_interval"
