#!/system/bin/sh
# ============================================================
# LE Audio 一键诊断（在小米 13 / fuxi 上以 root 运行）
# 用法:  adb push diag_leaudio.sh /data/local/tmp/
#        adb shell su -c "sh /data/local/tmp/diag_leaudio.sh" | tee leaudio_diag.txt
# ============================================================

OUT=/data/local/tmp/leaudio_diag.txt
exec > "$OUT" 2>&1

echo "===== 0. 设备 ====="
getprop ro.product.device
getprop ro.product.model
getprop ro.build.version.incremental
getprop ro.build.version.release
date

echo
echo "===== 1. LE Audio 相关属性 ====="
getprop | grep -iE 'leaudio|le_audio|lc3|bluetooth\.le|btaudio|offload' | sort

echo
echo "===== 2. settings 里的 le_audio 键 ====="
echo "--- global ---"
settings list global | grep -iE 'le_audio|leaudio|bluetooth'
echo "--- secure ---"
settings list secure | grep -iE 'le_audio|leaudio|bluetooth'
echo "--- system ---"
settings list system | grep -iE 'le_audio|leaudio|bluetooth'

echo
echo "===== 3. 权限 XML ====="
ls -l /system/etc/permissions/android.hardware.bluetooth_le_audio.xml 2>&1
ls -l /vendor/etc/permissions/ 2>/dev/null | grep -i le_audio
ls -l /product/etc/permissions/ 2>/dev/null | grep -i le_audio

echo
echo "===== 4. APEX LE Audio 配置 ====="
ls -la /apex/com.android.bt/etc/bluetooth/le_audio/ 2>&1
echo "--- audio_set_configurations.json 中的 LC3 / REDMI / Buds ---"
grep -iE 'lc3|redmi|buds|24:B2:31|offload' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_configurations.json 2>/dev/null | head -40
echo "--- audio_set_scenarios.json ---"
grep -iE 'lc3|redmi|buds|media|unicast' /apex/com.android.bt/etc/bluetooth/le_audio/audio_set_scenarios.json 2>/dev/null | head -40

echo
echo "===== 5. Bluetooth 服务状态 ====="
dumpsys bluetooth_manager 2>/dev/null | head -80
echo "--- LeAudio Manager ---"
dumpsys bluetooth_manager 2>/dev/null | grep -A 30 -i "LeAudio Manager"
echo "--- AdapterServiceConfig ---"
dumpsys bluetooth_manager 2>/dev/null | grep -iE 'AdapterServiceConfig|profile=|LE_AUDIO'

echo
echo "===== 6. 音频路由 ====="
dumpsys audio 2>/dev/null | grep -iE 'Current device|ble|le_audio|bluetooth'
echo "--- media.audio_policy ---"
dumpsys media.audio_policy 2>/dev/null | grep -iE 'ble_headset|le_audio|BLUETOOTH|offload|Selected Device' | head -40

echo
echo "===== 7. 音频策略文件 ====="
echo "（找 audio_policy*.xml）"
for d in /vendor /system /odm /product /system_ext; do
  find "$d" -name 'audio_policy*.xml' 2>/dev/null
done
echo "--- 含 ble_headset / lc3 的策略片段 ---"
for f in $(find /vendor /system /odm /product -name 'audio_policy*.xml' 2>/dev/null); do
  echo "## $f"
  grep -n -iE 'ble_headset|le_audio|lc3|compress_offload|direct' "$f" 2>/dev/null | head -20
done

echo
echo "===== 8. 云控缓存 ====="
ls -la /data/data/com.xiaomi.bluetooth/files/cloud/ 2>&1
ls -la /data/user_de/0/com.xiaomi.bluetooth/files/cloud/ 2>&1
for f in /data/data/com.xiaomi.bluetooth/files/cloud/*.json \
         /data/user_de/0/com.xiaomi.bluetooth/files/cloud/*.json; do
  [ -f "$f" ] || continue
  echo "## $f"
  grep -iE 'leaudio|le_audio|unicast|bca|bcs|fuxi|zizhan' "$f" | head -30
done

echo
echo "===== 9. 开发者选项设置值 ====="
settings get global bluetooth_le_audio_enabled
settings get global bluetooth_le_audio_offload_enabled
settings get global bluetooth_le_audio_disabled
settings get global bluetooth_le_audio_offload_disabled
settings get global bluetooth_le_audio_bypass_allowlist
settings get secure bluetooth_le_audio_enabled
settings get secure miui_bluetooth_le_audio_enabled
settings get secure miui_le_audio_enabled
settings get secure miui_le_audio_open
settings get secure enable_bluetooth_le_audio_editing

echo
echo "===== 10. 最近蓝牙/音频日志 ====="
logcat -d -t 300 2>/dev/null | grep -iE 'AdapterServiceConfig|LeAudio|le_audio|LC3|btaudio_offload|CloudControl|MiuiCloudControl|audio_stream|session|offload|BTAudio' | tail -120

echo
echo "===== 11. LSPosed / Magisk ====="
ls -la /data/adb/modules/ 2>&1
ls -la /data/adb/lspd/ 2>&1 | head
echo "--- 已启用模块 ---"
for d in /data/adb/modules/*/; do
  [ -f "$d/module.prop" ] && echo "## $d" && cat "$d/module.prop"
done

echo
echo "===== 诊断结束 ====="
echo "输出已写入 $OUT"
