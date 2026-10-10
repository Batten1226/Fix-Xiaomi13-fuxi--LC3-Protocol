#!/system/bin/sh
settings put global three_mac_for_ble_f "24:B2:31:B3:C6:AE"
settings put global lc3Enable true
echo "three_mac_for_ble_f=$(settings get global three_mac_for_ble_f)"
echo "lc3Enable=$(settings get global lc3Enable)"
am force-stop com.android.settings
echo done
