# Fix Xiaomi 13 (fuxi) LE Audio / LC3 Protocol

解锁小米 13（fuxi，HyperOS 3.0 / Android 16）上的 **LE Audio / LC3 / 硬件分流（ADSP offload）**。

## 背景

小米 13 硬件支持 LE Audio（`ro.bluetooth.leaudio_offload.supported=true`），但 MIUI/HyperOS 的云控白名单未包含 fuxi，导致：

- 开发者选项「停用蓝牙LE音频」开关灰色不可编辑
- LE Audio profile 不启动（`AdapterServiceConfig: LE_AUDIO, enabled=false`）
- REDMI Buds 8 Pro 等 LE Audio 耳机只能走 A2DP/LHDC，无法协商 LC3

## 根因

**4 层问题叠加：**

| 层级 | 根因 | 修复 |
|---|---|---|
| 1. 属性缺失 | `bluetooth.profile.bap.unicast.client.enabled` 不存在 → `LeAudioService.isEnabled()` 返回 false | `resetprop` 设置该属性 |
| 2. CSIS 未发现 | 耳机配对时 LE Audio 未启用，CSIP 组无法建立 | 重新配对耳机触发 GATT 服务发现 |
| 3. AOSP 助听设备页锁定 | 系统原生助听设备管理页强制锁定 LC3 | 关闭该功能 |
| 4. 连接策略被系统重置 | `le_audio_connection_policy` 被系统覆盖为 0 | 通过 Bluetooth API 异步设策略后再重启蓝牙 |

## 组成

| 组件 | 说明 |
|---|---|
| **LSPosed 模块** `app/` | Hook 蓝牙栈和开发者选项，强制启用 LE Audio profile |
| **Magisk 模块** `magisk-module/` | 开机自动设置必要的系统属性 |
| **诊断脚本** `scripts/` | 收集设备状态、修复连接策略 |

## LSPosed Hook 详情

| 作用域 | Hook 目标 | 作用 |
|---|---|---|
| `com.android.bluetooth` | `CsipSetCoordinatorService.isEnabled()` | 强制启用 CSIP 组协调 |
| `com.android.bluetooth` | `VolumeControlService.isEnabled()` | 强制启用音量控制 |
| `com.android.bluetooth` | `LeAudioService.isEnabled()` | 确保 LE Audio profile 启动 |
| `com.android.settings` | `BluetoothAdapter.isLeAudioSupported()` | 返回 10，解锁开发者选项开关 |
| `com.android.settings` | `BluetoothLeAudio*PreferenceController` | `setEnabled(true)` 去灰 |
| `com.android.settings` | `setLeAudioStatus()` | 实际执行 LE Audio 开关操作（原始是空方法） |
| `com.android.settings` | `getLeAudioStatus()` | 返回正确状态（原始 stub 恒返回 0） |
| `com.android.settings` | `isTbsProfileEnabled()` / `isDualModeDevice()` | 让 LE Audio 开关出现在设备设置页 |
| `com.android.settings` | `BluetoothRebootDialog.show()` | 拦截重启框，改用重启蓝牙服务 |

## 使用方法

### 前置条件
- Bootloader 已解锁，Magisk Root
- LSPosed 已安装（Zygisk 版本）
- USB 调试已开启

### 安装

1. **安装 Magisk 模块**（刷入 `release/leaudio-props.zip`）
2. **安装 LSPosed 模块**（`adb install app/build/outputs/apk/debug/app-debug.apk`）
3. 在 **LSPosed 管理器** 中启用 `LE Audio Unlock`，作用域勾选：
   - `com.android.bluetooth`
   - `com.android.settings`
   - `system`
4. **重新配对耳机**（触发 CSIS UUID 发现）
5. 在开发者选项或耳机设置页开启 LE Audio

### 构建

```bash
./gradlew :app:assembleDebug
```

## 验证

```bash
# 查看 LE Audio 状态
adb shell dumpsys bluetooth_manager | grep -A 20 "LeAudio Manager"

# 期望输出：
#   Active group: 1
#   Configuration: MEDIA (0x0004)
#   Codec location: ADSP

# 查看 Hook 日志
adb logcat -s LeAudioUnlock
```

## 兼容性

| 项目 | 版本 |
|---|---|
| 机型 | Xiaomi 13 (fuxi) |
| 系统 | HyperOS 3.0.309 (Android 16) |
| SoC | Snapdragon 8 Gen 2 |
| 测试耳机 | REDMI Buds 8 Pro (LC3/LHDC) |

## 已知问题

- 音质切换页面（`MiuiHeadsetAudioSoundQualityFragment`）是动态加载类，静态 dex 中无法反编译，LC3 选项显示需要额外处理
- 每次 OTA 后可能需要重新配对耳机

## License

GPL-3.0
