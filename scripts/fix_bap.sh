#!/system/bin/sh
# 精确修复：LeAudioService.isEnabled() 读的是
#   BluetoothProperties.isProfileBapUnicastClientEnabled()
#   -> bluetooth.profile.bap.unicast.client.enabled
# isBroadcastEnabled() 读的是
#   BluetoothProperties.isProfileBapBroadcastSourceEnabled()
#   -> bluetooth.profile.bap.broadcast.source.enabled

echo "===== 1. 设置正确的 BAP profile 属性 ====="
resetprop bluetooth.profile.bap.unicast.client.enabled true
resetprop bluetooth.profile.bap.broadcast.source.enabled true
resetprop bluetooth.profile.bap.broadcast.assistant.enabled true
resetprop bluetooth.profile.vc.client.enabled true
resetprop bluetooth.profile.mcp.server.enabled true
resetprop bluetooth.profile.ccp.server.enabled true
resetprop bluetooth.profile.csip.set.coordinator.enabled true
resetprop bluetooth.profile.hap.client.enabled true

# 经典 profile 也确保开着
resetprop bluetooth.profile.a2dp.enabled true
resetprop bluetooth.profile.hfp.enabled true
resetprop bluetooth.profile.hid.host.enabled true

echo "===== 2. 验证属性 ====="
getprop bluetooth.profile.bap.unicast.client.enabled
getprop bluetooth.profile.bap.broadcast.source.enabled

echo "===== 3. 重启蓝牙 ====="
svc bluetooth disable
sleep 3
svc bluetooth enable
sleep 6

echo "===== 4. 验证 LeAudioService ====="
dumpsys bluetooth_manager | grep -A 6 "LeAudio Manager"
echo "--- logcat ---"
logcat -d -t 30 | grep -iE "AdapterServiceConfig|LeAudioService|isEnabled|profile=" | tail -15
echo "--- C6:AE policy ---"
dumpsys bluetooth_manager | grep "C6:AE" | head -2

echo "===== 完成 ====="
