#!/system/bin/sh
echo "==== LC3 低延迟/游戏配置 ===="
grep -iE 'game|low_latency|low.latency|Gaming|7_5|75ms' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json | head -20
echo "==== 场景配置 ===="
grep -iE 'GAME|game' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_scenarios.json | head -10
echo "==== CIS 延迟参数 ===="
dumpsys bluetooth_manager | grep -A 3 "id.*active.*dir.*cis_id"
echo "==== 当前 stream 配置 ===="
dumpsys bluetooth_manager | grep -A 5 "Stream creation speed"
echo "==== latency 设置 ===="
dumpsys bluetooth_manager | grep -iE "latency|latency_pre|latency_val"
