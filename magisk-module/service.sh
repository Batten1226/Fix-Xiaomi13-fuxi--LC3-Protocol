#!/system/bin/sh
# 等开机完成后补 settings，并重启蓝牙让 profile 生效

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done
sleep 2

# settings
settings put global bluetooth_le_audio_enabled 1
settings put global bluetooth_le_audio_offload_enabled 1
settings put global bluetooth_le_audio_disabled 0
settings put global bluetooth_le_audio_offload_disabled 0
settings put global bluetooth_le_audio_bypass_allowlist 1
settings put global bluetooth_disabled_profiles 0
settings put secure miui_bluetooth_le_audio_enabled 1
settings put secure miui_le_audio_enabled 1
settings put secure miui_le_audio_open 1
settings put secure enable_bluetooth_le_audio_editing 1

# 重启蓝牙，让 profile 重新 init
svc bluetooth disable
sleep 2
svc bluetooth enable
