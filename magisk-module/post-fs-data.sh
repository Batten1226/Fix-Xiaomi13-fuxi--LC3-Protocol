#!/system/bin/sh
# LE Audio profile 属性 —— 开机时强制启用
# 根因：LeAudioService.isEnabled() 读
#   BluetoothProperties.isProfileBapUnicastClientEnabled()
#   = bluetooth.profile.bap.unicast.client.enabled
# 属性不存在时 orElse(FALSE) -> profile 不启动 -> LeAudioService 为 null

MODDIR=${0%/*}

# ---- BAP（核心） ----
resetprop bluetooth.profile.bap.unicast.client.enabled true
resetprop bluetooth.profile.bap.broadcast.source.enabled true
resetprop bluetooth.profile.bap.broadcast.assistant.enabled true

# ---- 辅助 profile ----
resetprop bluetooth.profile.csip.set.coordinator.enabled true
resetprop bluetooth.profile.vc.controller.enabled true
resetprop bluetooth.profile.hap.client.enabled true
resetprop bluetooth.profile.mcp.server.enabled true
resetprop bluetooth.profile.ccp.server.enabled true

# ---- 经典 profile 保持 ----
resetprop bluetooth.profile.a2dp.enabled true
resetprop bluetooth.profile.hfp.enabled true
resetprop bluetooth.profile.hid.host.enabled true

# ---- persist 兜底 ----
resetprop persist.bluetooth.leaudio.enabled true
resetprop persist.bluetooth.leaudio_offload.enabled true
resetprop persist.bluetooth.leaudio_offload.supported true
resetprop persist.bluetooth.leaudio_offload.disabled false
resetprop persist.bluetooth.leaudio.cloud.disable false

# ---- 开发者选项开关解锁 ----
# Controller 判定：isLeAudioSupported()==10 才放行
# 以及 ro.bluetooth.leaudio_switcher.supported=true
resetprop ro.bluetooth.leaudio_switcher.supported true
resetprop ro.bluetooth.leaudio.le_audio_connection_by_default true
resetprop persist.bluetooth.leaudio.bypass_allow_list true

# ---- settings（在 service.sh 里做，因为 settings 服务要等开机） ----
