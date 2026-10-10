#!/system/bin/sh
echo "=== modules ==="
sqlite3 /data/adb/lspd/config/modules_config.db "SELECT * FROM modules"
echo "=== modules_state ==="
sqlite3 /data/adb/lspd/config/modules_config.db "SELECT * FROM modules_state"
echo "=== scope ==="
sqlite3 /data/adb/lspd/config/modules_config.db "SELECT * FROM scope"
