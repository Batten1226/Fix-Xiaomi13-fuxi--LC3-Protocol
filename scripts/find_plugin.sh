#!/system/bin/sh
echo "==== plugin apks ===="
find /data/app /system_ext -name "*.apk" 2>/dev/null | grep -iE "plugin|headset|bluetooth|settings" | head -15
echo "==== dex in settings ===="
find /data/app -path "*com.android.settings*" -name "*.apk" 2>/dev/null | head -5
echo "==== plugin dir ===="
ls /data/user/0/com.android.settings/files/ 2>/dev/null | head -10
ls /data/data/com.android.settings/files/ 2>/dev/null | head -10
