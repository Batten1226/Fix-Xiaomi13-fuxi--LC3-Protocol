#!/system/bin/sh
# 补齐剩余 LE Audio 子 profile + 提升耳机连接策略

echo "===== 1. CSIP / VCP / BASS 属性 ====="
# CsipSetCoordinatorService.isEnabled() -> isProfileCsipSetCoordinatorEnabled
resetprop bluetooth.profile.csip.set.coordinator.enabled true
# VolumeControlService.isEnabled() -> isProfileVcpControllerEnabled
resetprop bluetooth.profile.vc.controller.enabled true
# BassClientService.isEnabled() -> isProfileBapBroadcastAssistEnabled
resetprop bluetooth.profile.bap.broadcast.assistant.enabled true
# HAP
resetprop bluetooth.profile.hap.client.enabled true

echo "===== 2. 验证 ====="
getprop bluetooth.profile.csip.set.coordinator.enabled
getprop bluetooth.profile.vc.controller.enabled
getprop bluetooth.profile.bap.broadcast.assistant.enabled

echo "===== 3. 重启蓝牙 ====="
svc bluetooth disable
sleep 3
svc bluetooth enable
sleep 6

echo "===== 4. profile init ====="
logcat -d -b all | grep "AdapterServiceConfig: init" | tail -30

echo "===== 5. 耳机策略 & LeAudio ====="
dumpsys bluetooth_manager | grep -A 20 "LeAudio Manager" | head -22
echo "--- C6:AE ---"
dumpsys bluetooth_manager | grep "C6:AE" | head -3

echo "===== 6. 尝试把 LE_AUDIO 策略设为 100 ====="
# 用 cmd bluetooth 没有直接命令，用 am broadcast 触发
# 先看当前蓝牙是否已连接耳机
dumpsys bluetooth_manager | grep -E "Connected|Active" | head -8

echo "===== 完成 ====="
