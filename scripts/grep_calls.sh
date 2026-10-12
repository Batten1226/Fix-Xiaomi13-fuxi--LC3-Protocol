#!/system/bin/sh
grep "setLeAudioStatus" /data/adb/lspd/log/verbose_2026-10-10T12:16:53.223659.log | grep -v "挂钩" | tail -20
echo "==== onPreferenceChange ===="
grep "onPreferenceChange" /data/adb/lspd/log/verbose_2026-10-10T12:16:53.223659.log | tail -15
echo "==== LeAudio disable ===="
grep "LeAudio disable" /data/adb/lspd/log/verbose_2026-10-10T12:16:53.223659.log | tail -15
