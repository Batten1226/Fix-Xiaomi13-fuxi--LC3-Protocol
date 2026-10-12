package dev.leaudio.unlock.xposed

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val TAG = "LeAudioUnlock"

private const val PKG_BT = "com.android.bluetooth"
private const val PKG_SETTINGS = "com.android.settings"
private const val PKG_MIUI_BT = "com.xiaomi.bluetooth"
private const val PKG_SYSTEM = "android"

/**
 * leaudio-unlock：解锁 HyperOS 的 LE Audio / LC3 / 硬件分流。
 *
 * 背景（实测）：
 *  - 硬件与属性都已支持 LE Audio，LeAudio Manager 也能起来、LC3 已协商
 *  - 但开发者选项开关被 MIUI 云控/白名单锁死（灰的），offload 会话建立后立刻 end
 *  - fuxi 不在云控白名单（白名单里只有 zizhan）
 *
 * 本模块在 4 个进程里挂钩：
 *  1. com.android.bluetooth — AdapterServiceConfig：强制 LE_AUDIO profile enabled
 *  2. com.android.bluetooth / com.xiaomi.bluetooth — 云控：强制返回“支持”
 *  3. system_server — BluetoothAdapter.isLeAudioSupported() 恒 true
 *  4. com.android.settings — 开发者选项开关去灰
 */
class LeAudioUnlockModule : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            PKG_BT -> hookBluetooth(lpparam.classLoader ?: return)
            PKG_MIUI_BT -> hookMiuiBluetooth(lpparam.classLoader ?: return)
            PKG_SYSTEM -> hookSystem(lpparam.classLoader ?: return)
            PKG_SETTINGS -> hookSettings(lpparam.classLoader ?: return)
        }
    }

    // ============================================================
    // com.android.bluetooth
    // ============================================================

    private fun hookBluetooth(cl: ClassLoader) {
        log("==== hookBluetooth ($PKG_BT) ====")

        // 如果用户在开发者选项里关了 LE Audio，就不强制启用
        val leAudioDisabled = runCatching {
            val sysPropCls = Class.forName("android.os.SystemProperties")
            val getBool = sysPropCls.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            getBool.invoke(null, "persist.bluetooth.leaudio_switcher.disabled", false) as Boolean
        }.getOrDefault(false)
        log("leaudio_switcher.disabled = $leAudioDisabled")
        if (leAudioDisabled) {
            log("LE Audio 已被用户禁用，hook connect() 拒绝 LE Audio 连接")
            // 禁用时：阻止 LE Audio 和 CSIP 连接，强制走 A2DP
            val leAudioCls = loadClass("com.android.bluetooth.le_audio.LeAudioService", cl)
            if (leAudioCls != null) {
                runCatching {
                    XposedBridge.hookAllMethods(leAudioCls, "connect", object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                            logOnce("LeAudioService.connect() -> false（已禁用 LE Audio）")
                        }
                    })
                }
            }
            val csipCls = loadClass("com.android.bluetooth.csip.CsipSetCoordinatorService", cl)
            if (csipCls != null) {
                runCatching {
                    XposedBridge.hookAllMethods(csipCls, "connect", object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                            logOnce("CsipSetCoordinatorService.connect() -> false（已禁用 LE Audio）")
                        }
                    })
                }
            }
            return
        }

        // 1) AdapterServiceConfig：把 LE_AUDIO 的 enabled 强制成 true
        //    日志证据：AdapterServiceConfig: init: profile= LE_AUDIO, enabled=false
        hookAdapterServiceConfig(cl)

        // 1.5) 强制 CSIP / VolumeControl / BassClient 的 isEnabled 返回 true
        //     反编译确认这些类的 isEnabled() 读 BluetoothProperties，属性不生效
        //     CSIP profile ID=25, VolumeControl ID=23, BassClient ID=29
        for (clsName in listOf(
            "com.android.bluetooth.csip.CsipSetCoordinatorService",
            "com.android.bluetooth.vc.VolumeControlService",
            "com.android.bluetooth.bass_client.BassClientService",
            "com.android.bluetooth.hap.HapClientService",
        )) {
            val c = loadClass(clsName, cl) ?: continue
            runCatching {
                XposedBridge.hookAllMethods(c, "isEnabled", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                        logOnce("$clsName.isEnabled() -> true")
                    }
                })
            }
            runCatching {
                XposedBridge.hookAllMethods(c, "isBroadcastEnabled", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                        logOnce("$clsName.isBroadcastEnabled() -> true")
                    }
                })
            }
        }

        // 2) 各种 isLeAudio* / isProfileEnabled* 判定，一律放行
        forceTrueOnClasses(
            cl,
            listOf(
                "com.android.bluetooth.btservice.AdapterServiceConfig",
                "com.android.bluetooth.btservice.AdapterService",
                "com.android.bluetooth.btservice.ProfileService",
                "com.android.bluetooth.le_audio.LeAudioService",
                "com.android.bluetooth.le_audio.LeAudioNativeInterface",
            ),
            listOf(
                "isLeAudioEnabled",
                "isLeAudioSupported",
                "isLeAudioOffloadSupported",
                "isLeAudioBroadcastEnabled",
                "isProfileEnabled",
                "isProfileSupported",
                "isUnicastEnabled",
                "isBcaEnabled",
                "isBcsEnabled",
                "isOffloadEnabled",
                "isOffloadSupported",
            ),
        )

        // 2.5) Hook ProfileService.getConnectionPolicy（父类方法）+ CSIP/LeAudio 的 connect
        //     connect() 第一行 `getConnectionPolicy(device) == 0` 就 return false
        //     getConnectionPolicy 是 ProfileService 的方法，hookAllMethods 找不到继承方法
        val profileSvcCls = loadClass("com.android.bluetooth.btservice.ProfileService", cl)
        if (profileSvcCls != null) {
            log("★ 命中 ProfileService，hook getConnectionPolicy")
            runCatching {
                XposedBridge.hookAllMethods(profileSvcCls, "getConnectionPolicy", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == 0) {
                            param.result = 100
                            logOnce("ProfileService.getConnectionPolicy() 0 -> 100")
                        }
                    }
                })
            }
        }

        for (svcName in listOf(
            "com.android.bluetooth.csip.CsipSetCoordinatorService",
            "com.android.bluetooth.le_audio.LeAudioService",
        )) {
            val svcCls = loadClass(svcName, cl) ?: continue
            log("★ 命中 $svcName")
            runCatching {
                XposedBridge.hookAllMethods(svcCls, "connect", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        logOnce("$svcName.connect() 被调用")
                    }
                })
            }
        }

        // 2.6) 让 Utils.arrayContains 对 COORDINATED_SET UUID 恒返回 true
        //     这样 CSIP connect() 的 UUID 检查就不会挡住
        val utilsCls = loadClass("com.android.bluetooth.Utils", cl)
        if (utilsCls != null) {
            runCatching {
                XposedBridge.hookAllMethods(utilsCls, "arrayContains", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 如果 UUID 数组参数看起来像在查 COORDINATED_SET，直接放行
                        val args = param.args
                        if (args.size >= 2 && args[1] != null) {
                            val uuid = args[1].toString().lowercase()
                            if (uuid.contains("1844") || uuid.contains("coordinated")) {
                                param.result = true
                                logOnce("Utils.arrayContains(CSIS) -> true")
                            }
                        }
                    }
                })
            }
        }

        // 3) getPropertyValue / getBoolean 一类读 prop 的口子：把 LE Audio 相关键翻成 true
        hookPropReaders(cl)
    }

    /**
     * AdapterServiceConfig 的 init / getProfileEnabled 一族。
     * 类名可能随版本变动，所以多试几个候选。
     */
    private fun hookAdapterServiceConfig(cl: ClassLoader) {
        // 反编译确认：类名是 com.android.bluetooth.btservice.Config
        val candidates = listOf(
            "com.android.bluetooth.btservice.Config",
            "com.android.bluetooth.btservice.AdapterServiceConfig",
            "com.android.bluetooth.btservice.AdapterConfig",
        )
        for (name in candidates) {
            val c = loadClass(name, cl) ?: continue
            log("★ 命中 AdapterServiceConfig 候选: $name")

            // init() 之后把 LE_AUDIO 的 enabled 写成 true
            hookAll(c, "init") { param ->
                runCatching {
                    forceLeAudioFlag(c, param.thisObject)
                }.onFailure { log("forceLeAudioFlag 失败: $it") }
            }

            // 读 enabled / isProfileEnabled 时对 LE_AUDIO 直接 true
            for (m in listOf("isProfileEnabled", "getProfileEnabled", "isEnabled", "isProfileSupported")) {
                hookAll(c, m) { param ->
                    if (isLeAudioArg(param.args)) {
                        param.result = true
                        logOnce("AdapterServiceConfig.$m(LE_AUDIO) -> true")
                    }
                }
            }

            // 字段兜底：扫一遍 mProfiles / sProfiles 之类的 map/list
            runCatching { forceLeAudioFlag(c, null) }
        }
    }

    /** 通过反射把 LE_AUDIO 相关的 enabled 字段/表项改成 true */
    private fun forceLeAudioFlag(c: Class<*>, instance: Any?) {
        val fieldNames = listOf(
            "mProfiles", "sProfiles", "mProfileEnabled", "mEnabledProfiles",
            "LE_AUDIO", "PROFILE_LE_AUDIO", "mLeAudioEnabled",
        )
        for (fname in fieldNames) {
            val f = runCatching { c.getDeclaredField(fname) }.getOrNull() ?: continue
            f.isAccessible = true
            when (val v = f.get(instance)) {
                is Boolean -> {
                    if (!v) {
                        f.set(instance, true)
                        log("★ 字段 $fname = true")
                    }
                }
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = v as MutableMap<Any?, Any?>
                    for (k in map.keys.toList()) {
                        if (isLeAudioName(k.toString())) {
                            val old = map[k]
                            if (old is Boolean && !old) {
                                map[k] = true
                                log("★ map[$k] = true")
                            }
                        }
                    }
                }
                is MutableCollection<*> -> {
                    // 只记录，不改集合类型
                    log("字段 $fname 是集合，size=${v.size}")
                }
            }
        }
    }

    // ============================================================
    // com.xiaomi.bluetooth —— 云控 / 白名单
    // ============================================================

    private fun hookMiuiBluetooth(cl: ClassLoader) {
        log("==== hookMiuiBluetooth ($PKG_MIUI_BT) ====")

        // CloudControlUtil：只 hook 精确匹配"是否支持/启用"的方法
        val cloudClasses = listOf(
            "com.android.bluetooth.util.CloudControlUtil",
            "com.xiaomi.bluetooth.cloud.CloudControlUtil",
            "com.xiaomi.bluetooth.cloudcontrol.CloudControlUtil",
            "com.xiaomi.bluetooth.MiuiCloudControl_Plugin",
        )
        for (name in cloudClasses) {
            val c = loadClass(name, cl) ?: continue
            log("★ 命中云控类: $name")
            for (m in listOf("isLeAudioSupported", "isSupportLeAudio", "isLeAudioEnabled",
                    "isUnicastEnabled", "isBcaEnabled", "isBcsEnabled", "isLeAudioAvailable")) {
                runCatching {
                    XposedBridge.hookAllMethods(c, m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                            logOnce("云控 $name.$m() -> true")
                        }
                    })
                }
            }
        }

        // 白名单类
        val allowClasses = listOf(
            "com.xiaomi.bluetooth.cloud.DeviceAllowList",
            "com.xiaomi.bluetooth.cloud.AllowListUtil",
        )
        for (name in allowClasses) {
            val c = loadClass(name, cl) ?: continue
            log("★ 命中白名单类: $name")
            forceTrueOnClasses(cl, listOf(name), listOf(
                "isInAllowList", "isAllowList", "isDeviceSupported", "isModelSupported",
            ))
        }
    }

    // ============================================================
    // system_server
    // ============================================================

    private fun hookSystem(cl: ClassLoader) {
        log("==== hookSystem (system_server) ====")

        // BluetoothAdapter.isLeAudioSupported() —— 返回 FEATURE_SUPPORTED (1) 或 true
        val adapterCls = loadClass("android.bluetooth.BluetoothAdapter", cl)
        if (adapterCls != null) {
            for (m in listOf(
                "isLeAudioSupported", "isLeAudioBroadcastSupported",
                "isLeAudioBroadcastAssistantSupported", "isLeAudioEnabled",
                "isLeAudioAllowed",
            )) {
                runCatching {
                    XposedBridge.hookAllMethods(adapterCls, m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 常量：BluetoothAdapter.FEATURE_SUPPORTED = 1，FEATURE_UNSUPPORTED = 0
                            val orig = param.result
                            if (orig is Int) {
                                if (orig == 0) {
                                    param.result = 1
                                    logOnce("BluetoothAdapter.$m() $orig -> 1")
                                }
                            } else {
                                param.result = true
                                logOnce("BluetoothAdapter.$m() -> true")
                            }
                        }
                    })
                }
                log("已挂钩 BluetoothAdapter.$m")
            }
        } else {
            log("!! 找不到 android.bluetooth.BluetoothAdapter")
        }

        // 部分 MIUI 逻辑跑在 system_server 的 com.android.server.bluetooth
        forceTrueOnClasses(
            cl,
            listOf(
                "com.android.server.bluetooth.BluetoothManagerService",
                "com.android.server.bluetooth.LeAudioManagerService",
                "com.android.server.MiuiBluetoothService",
            ),
            listOf(
                "isLeAudioSupported", "isLeAudioEnabled", "isLeAudioAllowed",
                "isUnicastSupported", "isBcaSupported", "isBcsSupported",
                "isOffloadSupported", "isOffloadEnabled",
            ),
        )
    }

    // ============================================================
    // com.android.settings —— 开发者选项 UI 去灰
    // ============================================================

    private fun hookSettings(cl: ClassLoader) {
        log("==== hookSettings ($PKG_SETTINGS) ====")

        // 反编译确认的精确类名（Settings classes3.dex）
        val uiClasses = listOf(
            "com.android.settings.development.BluetoothLeAudioHwOffloadPreferenceController",
            "com.android.settings.development.BluetoothLeAudioPreferenceController",
            "com.android.settings.development.BluetoothLeAudioAllowListPreferenceController",
            "com.android.settings.development.BluetoothLeAudioModePreferenceController",
            "com.android.settings.development.BluetoothLeAudioUiPreferenceController",
            "com.android.settings.development.BluetoothLeAudioDeviceDetailsPreferenceController",
        )

        for (name in uiClasses) {
            val c = loadClass(name, cl) ?: continue
            log("★ 命中 Settings Controller: $name")

            // isAvailable：Controller 自己的可用性判定，强制 true
            runCatching {
                XposedBridge.hookAllMethods(c, "isAvailable", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = true
                        logOnce("Settings.$name.isAvailable() -> true")
                    }
                })
            }

            // updateState：这是锁开关的地方（setEnabled(false)）。
            // 只强制 setEnabled(true)，不改 setChecked（让用户自由切换）
            runCatching {
                XposedBridge.hookAllMethods(c, "updateState", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pref = param.args.getOrNull(0) ?: return
                        runCatching {
                            val setEnabled = pref.javaClass.methods.firstOrNull {
                                it.name == "setEnabled" && it.parameterCount == 1
                            }
                            setEnabled?.invoke(pref, true)
                        }.onFailure { log("updateState 补救失败: $it") }
                        logOnce("Settings.$name.updateState() -> setEnabled(true)")
                    }
                })
            }

            // onPreferenceChange：原始返回 false（不应用），等重启后才写属性
            // 我们直接写属性并返回 true，让开关立即生效
            runCatching {
                XposedBridge.hookAllMethods(c, "onPreferenceChange", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val newValue = param.args.getOrNull(1)
                        logOnce("Settings.$name.onPreferenceChange newValue=$newValue")
                        // 根据 Controller 类型写对应属性
                        when {
                            name.contains("HwOffload") -> {
                                // "停用硬件分流" 勾选=true 表示禁用 offload
                                val disable = newValue == true
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio_offload.disabled",
                                        if (disable) "true" else "false"
                                    )
                                }
                                logOnce("HwOffload -> offload.disabled=$disable")
                            }
                            name.contains("LeAudioPreference") && !name.contains("Allow") -> {
                                // "停用蓝牙LE音频" 勾选=true 表示禁用 LE Audio
                                val disable = newValue == true
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio_switcher.disabled",
                                        if (disable) "true" else "false"
                                    )
                                    // 同时控制 BAP profile 属性，让 BT 栈真正启/停 LE Audio
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "bluetooth.profile.bap.unicast.client.enabled",
                                        if (disable) "false" else "true"
                                    )
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "bluetooth.profile.csip.set.coordinator.enabled",
                                        if (disable) "false" else "true"
                                    )
                                }
                                logOnce("LeAudio disable=$disable -> profile props updated")
                                // 重启蓝牙生效
                                runCatching {
                                    Runtime.getRuntime().exec(arrayOf(
                                        "su", "-c",
                                        "svc bluetooth disable && sleep 2 && svc bluetooth enable"))
                                }
                            }
                            name.contains("AllowList") -> {
                                val bypass = newValue == true
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio.bypass_allow_list",
                                        if (bypass) "true" else "false"
                                    )
                                }
                                logOnce("AllowList -> bypass_allow_list=$bypass")
                            }
                        }
                        param.result = true  // 应用变更，开关立即切换
                    }
                })
            }
        }

        // 兜底：BluetoothAdapter.isLeAudioSupported() 必须返回 10 (FEATURE_SUPPORTED)
        // 所有 Controller 的 updateState 都靠这个判定
        val adapterCls = loadClass("android.bluetooth.BluetoothAdapter", cl)
        if (adapterCls != null) {
            hookAll(adapterCls, "isLeAudioSupported") { param ->
                param.result = 10
                logOnce("BluetoothAdapter.isLeAudioSupported() -> 10")
            }
        }

        // ===== 让 LE Audio 开关出现在音质页 =====
        // MiuiHeadsetFragment.isLeAudioBrDevice() 依赖：
        //   LocalBluetoothProfileManager.isTbsProfileEnabled() -> isDualModeDevice()
        // 两个都返回 true 才会 addPreferencesForLeAudio() 显示 LE Audio 开关
        val profileMgrCls = loadClass("com.android.settingslib.bluetooth.LocalBluetoothProfileManager", cl)
        if (profileMgrCls != null) {
            log("★ 命中 LocalBluetoothProfileManager")
            hookAll(profileMgrCls, "isTbsProfileEnabled") { param ->
                param.result = true
                logOnce("isTbsProfileEnabled() -> true")
            }
        }
        val cachedDeviceCls2 = loadClass("com.android.settingslib.bluetooth.CachedBluetoothDevice", cl)
            ?: loadClass("com.android.settings.bluetooth.CachedBluetoothDevice", cl)
        if (cachedDeviceCls2 != null) {
            hookAll(cachedDeviceCls2, "isDualModeDevice") { param ->
                param.result = true
                logOnce("isDualModeDevice() -> true")
            }
            // getLeAudioStatus 是 settingslib stub（恒返回 0），导致 LC3 开关显示灰色关闭
            // 根据实际 LE Audio 状态返回 1（启用）
            hookAll(cachedDeviceCls2, "getLeAudioStatus") { param ->
                val leAudioEnabled = runCatching {
                    val sysPropCls = Class.forName("android.os.SystemProperties")
                    val getBool = sysPropCls.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                    getBool.invoke(null, "bluetooth.profile.bap.unicast.client.enabled", false) as Boolean
                }.getOrDefault(false)
                param.result = if (leAudioEnabled) 1 else 0
                logOnce("getLeAudioStatus() -> ${param.result} (leAudioEnabled=$leAudioEnabled)")
            }
            // setLeAudioStatus 是空方法（settingslib stub），我们来真正执行
            hookAll(cachedDeviceCls2, "setLeAudioStatus") { param ->
                val status = param.args.getOrNull(0) as? Int ?: return@hookAll
                logOnce("setLeAudioStatus($status) 被调用")
                runCatching {
                    val cmd = if (status == 1) {
                        "resetprop persist.bluetooth.leaudio_switcher.disabled false; " +
                        "resetprop bluetooth.profile.bap.unicast.client.enabled true; " +
                        "resetprop bluetooth.profile.csip.set.coordinator.enabled true; " +
                        "resetprop bluetooth.profile.vc.controller.enabled true; " +
                        "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                    } else {
                        "resetprop persist.bluetooth.leaudio_switcher.disabled true; " +
                        "resetprop bluetooth.profile.bap.unicast.client.enabled false; " +
                        "svc bluetooth disable && sleep 2 && svc bluetooth enable"
                    }
                    Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    logOnce("setLeAudioStatus($status) -> 属性+重启蓝牙")

                    // 启用时：等蓝牙启动完成后设连接策略（避免策略被系统重置）
                    if (status == 1) {
                        Thread {
                            Thread.sleep(8000)  // 等蓝牙完全启动
                            updateConnectionPolicy(cl, "LE_AUDIO", 100)
                            updateConnectionPolicy(cl, "CSIP_SET_COORDINATOR", 100)
                            updateConnectionPolicy(cl, "VOLUME_CONTROL", 100)
                            updateConnectionPolicy(cl, "LE_CALL_CONTROL", 100)
                            logOnce("setLeAudioStatus(1) -> 连接策略已设置")
                        }.start()
                    }
                }.onFailure { log("setLeAudioStatus 失败: $it") }
            }
            // findLeAddress 返回 null 导致 LE 绑定被跳过，返回耳机的 LE 地址
            hookAll(cachedDeviceCls2, "findLeAddress") { param ->
                if (param.result == null) {
                    // REDMI Buds 8 Pro 的 LE 地址（从日志获取）
                    param.result = "E4:B2:31:B3:C6:AF;E4:B2:31:B3:C6:B0"
                    logOnce("findLeAddress() -> 返回耳机 LE 地址")
                }
            }
        }
        // 音质切换页读这个接口拿 codec 列表，A2DP 被禁时返回 null 导致列表为空
        val a2dpCls = loadClass("android.bluetooth.BluetoothA2dp", cl)
        if (a2dpCls != null) {
            log("★ 命中 BluetoothA2dp，hook getCodecStatus")
            hookAll(a2dpCls, "getCodecStatus") { param ->
                val result = param.result
                if (result == null) {
                    logOnce("BluetoothA2dp.getCodecStatus() 返回 null，构造 fallback")
                    // 构造一个包含耳机支持的 codec 的 BluetoothCodecStatus
                    runCatching {
                        val codecConfigCls = Class.forName("android.bluetooth.BluetoothCodecConfig")
                        // codecType: 0=SBC, 1=AAC, 4=LDAC, 13=LHDC_V3, 19=LHDC_V5
                        val makeConfig = { type: Int, prio: Int ->
                            codecConfigCls.getConstructor(
                                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
                            ).newInstance(type, prio, 0x2b, 0x3, 0x2, 0L, 0L, 0L, 0L)
                        }
                        // 当前配置：LHDC_V5
                        val current = makeConfig(19, 8004)
                        // 可选能力
                        val selectable = arrayOf(
                            makeConfig(19, 8004),  // LHDC_V5
                            makeConfig(13, 8003),  // LHDC_V3
                            makeConfig(12, 8002),  // LHDC_V2
                            makeConfig(4, 6001),   // LDAC
                            makeConfig(1, 2001),   // AAC
                            makeConfig(0, 1001),   // SBC
                        )
                        val statusCls = Class.forName("android.bluetooth.BluetoothCodecStatus")
                        val status = statusCls.getConstructor(
                            codecConfigCls,
                            codecConfigCls.arrayType(),
                            codecConfigCls.arrayType()
                        ).newInstance(current, selectable, selectable)
                        param.result = status
                        logOnce("getCodecStatus() -> 构造了 fallback codec status")
                    }.onFailure {
                        log("构造 codec status 失败: $it")
                    }
                }
            }
        }

        // ===== Hook BluetoothRebootDialog：不重启手机，改重启蓝牙 =====
        // onRebootDialogConfirmed 的逻辑是取反当前状态，会把 LE Audio 关掉
        // 所以我们：1) 拦截 show() 不弹框  2) 拦截 onRebootDialogConfirmed 把值写对
        val rebootDlg = loadClass("com.android.settings.development.BluetoothRebootDialog", cl)
        if (rebootDlg != null) {
            log("★ 命中 BluetoothRebootDialog")
            // show() 不弹框，直接返回 null
            runCatching {
                XposedBridge.hookAllMethods(rebootDlg, "show", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                        logOnce("BluetoothRebootDialog.show() -> 拦截，不弹重启框")
                    }
                })
            }
        }

        // 拦截各 Controller 的 onRebootDialogConfirmed，把属性写成正确值
        // 关键：「停用蓝牙LE音频」开关是"停用"语义，关掉=启用 LE Audio
        // 不能用 isLeAudioSupported() 取反（我们的 hook 让它恒返回 10，取反必错）
        for (name in uiClasses) {
            val c = loadClass(name, cl) ?: continue
            runCatching {
                XposedBridge.hookAllMethods(c, "onRebootDialogConfirmed", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        when {
                            name.contains("HwOffload") -> {
                                // 「停用硬件分流」关掉 = 不禁用 offload
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio_offload.disabled", "false"
                                    )
                                }
                                logOnce("HwOffload -> offload.disabled=false（启用 offload）")
                            }
                            name.contains("LeAudioPreference") && !name.contains("Allow") -> {
                                // 「停用蓝牙LE音频」关掉 = 启用 LE Audio
                                // 写 leaudio_switcher.disabled=false
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio_switcher.disabled", "false"
                                    )
                                }
                                logOnce("LeAudio -> leaudio_switcher.disabled=false（启用 LE Audio）")
                            }
                            name.contains("AllowList") -> {
                                runCatching {
                                    XposedHelpers.callStaticMethod(
                                        Class.forName("android.os.SystemProperties"),
                                        "set", "persist.bluetooth.leaudio.bypass_allow_list", "true"
                                    )
                                }
                                logOnce("AllowList -> bypass_allow_list=true")
                            }
                        }
                        param.result = null  // 跳过原始取反逻辑
                        // 重启蓝牙让值生效
                        runCatching {
                            val rt = Runtime.getRuntime()
                            rt.exec(arrayOf("su", "-c", "svc bluetooth disable && sleep 2 && svc bluetooth enable"))
                        }
                        logOnce("已触发蓝牙重启")
                    }
                })
            }
        }
    }

    // ============================================================
    // 通用工具
    // ============================================================

    /** 属性读取口子：把 persist.bluetooth.leaudio.* / ro.bluetooth.leaudio* 翻成 true */
    private fun hookPropReaders(cl: ClassLoader) {
        val propClasses = listOf(
            "com.android.bluetooth.btservice.AdapterServiceConfig",
            "android.sysprop.BluetoothProperties",
            "com.android.bluetooth.Utils",
        )
        for (name in propClasses) {
            val c = loadClass(name, cl) ?: continue
            for (m in c.declaredMethods) {
                val n = m.name
                if (!(n.contains("get") || n.contains("is") || n.contains("read"))) continue
                val retType = m.returnType
                val isBool = retType == Boolean::class.javaPrimitiveType || retType == Boolean::class.java
                if (!isBool) continue
                runCatching {
                    XposedBridge.hookAllMethods(c, n, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 只处理方法名或参数里带 leaudio 的
                            val blob = n + param.args.joinToString { it?.toString() ?: "" }
                            if (isLeAudioName(blob) && param.result == false) {
                                param.result = true
                                logOnce("prop $name.$n() -> true")
                            }
                        }
                    })
                }
            }
        }
    }

    private fun forceTrueOnClasses(cl: ClassLoader, classNames: List<String>, methodNames: List<String>) {
        for (name in classNames) {
            val c = loadClass(name, cl) ?: continue
            for (m in methodNames) {
                runCatching {
                    XposedBridge.hookAllMethods(c, m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val retType = (param.method as? java.lang.reflect.Method)?.returnType
                            when (retType) {
                                Boolean::class.javaPrimitiveType, Boolean::class.java -> {
                                    param.result = true
                                    logOnce("$name.$m() -> true")
                                }
                                Int::class.javaPrimitiveType, Int::class.java -> {
                                    if (m.contains("Availability", ignoreCase = true)) {
                                        param.result = 0
                                        logOnce("$name.$m() -> 0")
                                    }
                                }
                                else -> {}
                            }
                        }
                    })
                    log("挂钩 $name.$m")
                }
            }
        }
    }

    private fun isLeAudioArg(args: Array<Any?>?): Boolean {
        if (args == null) return false
        return args.any { a ->
            val s = a?.toString() ?: return@any false
            isLeAudioName(s) || s.contains("LE_AUDIO") || s.equals("leaudio", true)
        }
    }

    private fun isLeAudioName(s: String): Boolean {
        val t = s.lowercase()
        return t.contains("leaudio") || t.contains("le_audio") || t.contains("lc3") ||
            t.contains("unicast") || t.contains("bca") || t.contains("bcs")
    }

    private fun loadClass(name: String, cl: ClassLoader): Class<*>? =
        runCatching { XposedHelpers.findClass(name, cl) }.getOrNull()

    private fun hookAll(c: Class<*>, methodName: String, body: (XC_MethodHook.MethodHookParam) -> Unit) {
        runCatching {
            XposedBridge.hookAllMethods(c, methodName, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = body(param)
            })
            log("挂钩 ${c.name}.$methodName")
        }
    }

    private val once = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun logOnce(msg: String) {
        if (once.add(msg)) log(msg)
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        XposedBridge.log("[$TAG] $msg")
    }

    /** 通过 Bluetooth API 更新指定 profile 的连接策略 */
    private fun updateConnectionPolicy(cl: ClassLoader, profile: String, policy: Int) {
        runCatching {
            val adapterCls = Class.forName("android.bluetooth.BluetoothAdapter")
            val getDefaultAdapter = adapterCls.getMethod("getDefaultAdapter")
            val adapter = getDefaultAdapter.invoke(null) ?: return
            val getRemoteDevice = adapterCls.getMethod("getRemoteDevice", String::class.java)
            // REDMI Buds 8 Pro 主地址
            val device = getRemoteDevice.invoke(adapter, "24:B2:31:B3:C6:AE") ?: return

            // profile ID: A2DP=2, LE_AUDIO=22
            val profileId = when (profile) {
                "A2DP" -> 2
                "LE_AUDIO" -> 22
                "CSIP_SET_COORDINATOR" -> 25
                "VOLUME_CONTROL" -> 23
                "LE_CALL_CONTROL" -> 27
                else -> return
            }

            // 获取 profile proxy
            val getProfileProxy = adapterCls.getMethod(
                "getProfileProxy", android.content.Context::class.java,
                Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener"),
                Int::class.javaPrimitiveType
            )
            // 用反射构造 ServiceListener
            val listenerCls = Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                cl, arrayOf(listenerCls),
                { _, method, args ->
                    if (method.name == "onServiceConnected") {
                        val proxy = args[1]
                        // setConnectionPolicy(device, policy)
                        val setPolicy = proxy.javaClass.getMethod("setConnectionPolicy",
                            Class.forName("android.bluetooth.BluetoothDevice"), Int::class.javaPrimitiveType)
                        setPolicy.invoke(proxy, device, policy)
                        logOnce("updateConnectionPolicy($profile) -> $policy")
                    }
                    null
                }
            )
            getProfileProxy.invoke(adapter,
                Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null),
                listener, profileId)
        }.onFailure { log("updateConnectionPolicy($profile) 失败: $it") }
    }
}
