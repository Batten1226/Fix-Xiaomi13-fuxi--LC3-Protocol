# ============================================================
# LE Audio 一键诊断（Windows 端，连上小米 13 后运行）
# 用法:  powershell -ExecutionPolicy Bypass -File diag.ps1
# ============================================================

$ErrorActionPreference = "Continue"
$ADB = "D:\AndroidSdk\platform-tools\adb.exe"
if (-not (Test-Path $ADB)) { $ADB = "adb" }

Write-Host "== 检查设备 ==" -ForegroundColor Cyan
& $ADB devices -l
$dev = & $ADB shell "getprop ro.product.device" 2>&1
$dev = "$dev".Trim()
Write-Host "ro.product.device = $dev"
if ($dev -ne "fuxi") {
    Write-Host "!! 当前设备不是 fuxi（小米 13）。请先连接小米 13 并开启 USB 调试。" -ForegroundColor Red
    Write-Host "   如果你确认设备已连接，请检查 USB 线 / 驱动 / 授权弹窗。" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n== 推送并执行诊断脚本 ==" -ForegroundColor Cyan
& $ADB push "$PSScriptRoot\diag_leaudio.sh" /data/local/tmp/diag_leaudio.sh
& $ADB shell "su -c 'sh /data/local/tmp/diag_leaudio.sh'"
& $ADB pull /data/local/tmp/leaudio_diag.txt "$PSScriptRoot\..\leaudio_diag.txt"
Write-Host "`n诊断结果已保存到 leaudio_diag.txt" -ForegroundColor Green

Write-Host "`n== 抓取 logcat 片段 ==" -ForegroundColor Cyan
& $ADB shell "logcat -d" | Select-String -Pattern "LeAudio|le_audio|LC3|btaudio_offload|AdapterServiceConfig|CloudControl|audio_stream|session" | Select-Object -Last 80
