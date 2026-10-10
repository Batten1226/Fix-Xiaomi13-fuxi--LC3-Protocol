#!/system/bin/sh
# 修复 LE Audio：把 OTA 清掉的配置补回来，强制启用 profile
# 前提：root

echo "===== 1. 设置 profile 系统属性 ====="
# Android 14+ 用 bluetooth.profile.* 控制 profile 启用
resetprop bluetooth.profile.le_audio.enabled true
resetprop bluetooth.profile.le_audio_offload.enabled true
resetprop bluetooth.profile.vc.enabled true
resetprop bluetooth.profile.ccp.server.enabled true
resetprop bluetooth.profile.mcp.server.enabled true
resetprop bluetooth.profile.csip_set_coordinator.enabled true
resetprop bluetooth.profile.hap.client.enabled true
resetprop bluetooth.profile.bass.client.enabled true

# persist 层
resetprop persist.bluetooth.leaudio.enabled true
resetprop persist.bluetooth.leaudio.profile.enable true
resetprop persist.bluetooth.leaudio.profile.enabled true
resetprop persist.bluetooth.leaudio.enable_unicast true
resetprop persist.bluetooth.leaudio.enable_bca true
resetprop persist.bluetooth.leaudio.enable_bcs true
resetprop persist.bluetooth.leaudio.available true
resetprop persist.bluetooth.leaudio_offload.enabled true
resetprop persist.bluetooth.leaudio_offload.supported true
resetprop persist.bluetooth.leaudio_offload.disabled false

# cloud.disable：false = 不让云控关掉 LE Audio
resetprop persist.bluetooth.leaudio.cloud.disable false

echo "===== 2. 设置 settings ====="
# global
settings put global bluetooth_le_audio_enabled 1
settings put global bluetooth_le_audio_offload_enabled 1
settings put global bluetooth_le_audio_disabled 0
settings put global bluetooth_le_audio_offload_disabled 0
settings put global bluetooth_le_audio_bypass_allowlist 1
settings put global bluetooth_disabled_profiles 0
# secure
settings put secure miui_bluetooth_le_audio_enabled 1
settings put secure miui_le_audio_enabled 1
settings put secure miui_le_audio_open 1
settings put secure enable_bluetooth_le_audio_editing 1

echo "===== 3. 设置耳机 LE_AUDIO 连接策略 ====="
# REDMI Buds 8 Pro: 24:B2:31:B3:C6:AE
# CONNECTION_POLICY_ALLOWED = 100
# 用 service call 蓝牙不太好使，改用 settings + 重启后由栈自己协商
# 先把设备的 metadata 里 le_audio 字段打开
echo "（连接策略会在重启蓝牙后由 LeAudioService 自己管理）"

echo "===== 4. 重启蓝牙 ====="
svc bluetooth disable
sleep 3
svc bluetooth enable
sleep 5

echo "===== 5. 验证 ====="
echo "--- props ---"
getprop bluetooth.profile.le_audio.enabled
getprop persist.bluetooth.leaudio.enabled
getprop persist.bluetooth.leaudio.cloud.disable
echo "--- LeAudioService ---"
dumpsys bluetooth_manager | grep -A 8 "LeAudio Manager"
echo "--- logcat ---"
logcat -d -t 50 | grep -iE 'AdapterServiceConfig|LeAudioService|le_audio|profile=' | tail -20
echo "--- 耳机连接策略 ---"
dumpsys bluetooth_manager | grep "C6:AE" | head -3

echo "===== 完成 ====="
