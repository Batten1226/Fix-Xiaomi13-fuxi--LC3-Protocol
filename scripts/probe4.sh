#!/system/bin/sh
echo "==== 已连接设备 ===="
dumpsys bluetooth_manager | grep -E "Connected|Active|C6:AE" | head -12
echo "==== miui unicast 开关 ===="
getprop | grep -iE 'adv_audio|unicast|qcom.*le|le.*qcom|miui.*le|le.*miui'
echo "==== LeAudio allow list ===="
dumpsys bluetooth_manager | grep -A 5 "Reconnection mode"
echo "==== BLE / CSIP 连接 ===="
dumpsys bluetooth_manager | grep -iE "LeAudioStateMachine|CsipSet|group_id|setActive|ActiveDevice" | head -12
echo "==== profile init ===="
logcat -d -b all | grep "AdapterServiceConfig: init" | grep -iE "LE_AUDIO|CSIP|VOLUME|BASS|HAP" | tail -8
echo "==== done ===="
