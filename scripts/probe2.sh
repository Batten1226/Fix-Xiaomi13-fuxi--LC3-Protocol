#!/system/bin/sh
echo "==== ro props ===="
getprop | grep -iE 'ro\.bluetooth\.leaudio|ro\.bluetooth\.le_audio|bluetooth\.le_audio'
echo "==== persist leaudio ===="
getprop | grep -iE 'persist\.bluetooth\.leaudio|persist\.bluetooth\.le_audio'
echo "==== dumpsys leaudio lines ===="
dumpsys bluetooth_manager | grep -iE 'le_audio|leaudio|LE_AUDIO' | head -50
echo "==== logcat profile init ===="
logcat -d -b all | grep -iE 'AdapterServiceConfig|profile= LE_AUDIO|LeAudioService|le_audio.*enabled|CloudControl|MiuiCloudControl' | tail -40
echo "==== bluetooth_config.xml live ===="
cat /data/user_de/0/com.android.bluetooth/shared_prefs/bluetooth_config.xml 2>/dev/null
echo "==== leaudio codec map live ===="
cat /data/user_de/0/com.android.bluetooth/shared_prefs/bluetooth_leaudio_codec_map.xml 2>/dev/null
echo "==== dev options keys ===="
for k in bluetooth_le_audio_enabled bluetooth_le_audio_offload_enabled bluetooth_le_audio_disabled bluetooth_le_audio_offload_disabled bluetooth_le_audio_bypass_allowlist miui_bluetooth_le_audio_enabled miui_le_audio_enabled miui_le_audio_open enable_bluetooth_le_audio_editing; do
  echo -n "$k = "
  settings get global "$k"
  echo -n "  secure: "
  settings get secure "$k"
done
echo "==== bluetooth_restricte ===="
settings get global bluetooth_restricte_blebroadcast
settings get global enable_bluetooth_restricte
settings get global bluetooth_disabled_profiles
echo "==== disabled module? ===="
ls -la /data/adb/modules/k70_bluetooth_leaudio_port/disable 2>&1
echo "==== done ===="
