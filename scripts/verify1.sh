#!/system/bin/sh
echo "===== profile init log ====="
logcat -d -b all | grep -iE "AdapterServiceConfig|init: profile=" | tail -30
echo "===== LeAudio Manager full ====="
dumpsys bluetooth_manager | grep -A 25 "LeAudio Manager"
echo "===== C6:AE full policy ====="
dumpsys bluetooth_manager | grep "C6:AE" | head -3
echo "===== dev options UI keys ====="
for k in bluetooth_le_audio_enabled bluetooth_le_audio_offload_enabled bluetooth_le_audio_disabled bluetooth_le_audio_offload_disabled bluetooth_le_audio_bypass_allowlist; do
  echo -n "global $k = "; settings get global "$k"
done
echo "===== connected devices ====="
dumpsys bluetooth_manager | grep -E "DUAL|Connected|Active" | head -10
echo "===== audio route ====="
dumpsys audio | grep "Current device:"
echo "===== done ====="
