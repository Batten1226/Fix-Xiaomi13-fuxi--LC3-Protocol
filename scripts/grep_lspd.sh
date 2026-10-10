#!/system/bin/sh
grep "com.android.settings" /data/adb/lspd/log/verbose_2026-10-10T12:16:53.223659.log | grep "LeAudioUnlock" | tail -15
