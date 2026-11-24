// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.*
import android.text.format.Formatter
import com.slate.android.storage.storageVolumes
import java.io.File
import java.util.Locale

internal const val UNKNOWN = "Unavailable"

// Net power at the battery, not USB input or charger-rated power.
internal fun batteryWatts(currentUa:Int?, voltageMv:Int?):Double? =
    if(currentUa==null || currentUa==Int.MIN_VALUE || voltageMv==null || voltageMv<=0) null
    else kotlin.math.abs(currentUa.toDouble())*voltageMv/1_000_000_000.0
internal data class Info(val label: String, val value: String)
internal data class Section(val title: String, val rows: List<Info>)
internal data class Meter(val title: String, val value: String, val subtitle: String, val fraction: Float?)
internal data class LiveInfo(val meters: List<Meter>, val cpu: Section, val memory: Section, val battery: Section, val uptime: Section, val gpu: Section, val thermal: Section, val connection: Section, val display: Section, val cores: List<CoreReading>)
internal data class StaticInfo(val hardware: List<Section>, val system: List<Section>, val sensors: List<Section>, val cameras: List<Section>)
internal data class CoreReading(val id: Int, val current: Long?, val maximum: Long?, val online: Boolean?)
internal data class GpuInfo(val renderer: String = UNKNOWN, val vendor: String = UNKNOWN, val version: String = UNKNOWN, val extensions: String = UNKNOWN)

internal class DeviceReader(private val context: Context) {
    private fun fmt(bytes: Long) = Formatter.formatFileSize(context, bytes)
    private fun info(label: String, value: Any?) = Info(label, value?.toString()?.takeUnless { it.isBlank() || it == "unknown" } ?: UNKNOWN)
    private fun read(path: String): String? = runCatching { File(path).bufferedReader().use { it.readLine() } }.getOrNull()
    private fun number(path: String): Long? = read(path)?.trim()?.toLongOrNull()
    private fun mhz(path: String): String = number(path)?.takeIf { it > 0 }?.let { "${it / 1000} MHz" } ?: UNKNOWN
    private val coreIds by lazy { cpuIds() }
    private val maxCore by lazy { coreIds.associateWith { id -> number("/sys/devices/system/cpu/cpu$id/cpufreq/cpuinfo_max_freq")?.takeIf { it > 0 } } }
    private fun coreReadings() = coreIds.map { id ->
        val base = "/sys/devices/system/cpu/cpu$id"
        val online = number("$base/online")?.let { it == 1L } ?: if (id == 0) true else null
        CoreReading(id, number("$base/cpufreq/scaling_cur_freq")?.takeIf { it > 0 && online != false }, maxCore[id], online)
    }
    private fun gpuLive(): Section {
        val base = "/sys/class/kgsl/kgsl-3d0"
        val devfreq = runCatching { File("/sys/class/devfreq").listFiles()?.firstOrNull {
            val name = it.name.lowercase(Locale.ROOT)
            name.contains("gpu") || name.contains("mali") || name.contains("kgsl")
        }?.path }.getOrNull()
        val hz = number("$base/gpuclk") ?: devfreq?.let { number("$it/cur_freq") }
        val max = number("$base/max_gpuclk") ?: devfreq?.let { number("$it/max_freq") }
        val busy = read("$base/gpu_busy_percentage")?.trim()?.removeSuffix("%")?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        return Section("GPU activity", listOf(info("Frequency", hz?.takeIf { it > 0 }?.let { "${it / 1000000} MHz" }),
            info("Maximum frequency", max?.takeIf { it > 0 }?.let { "${it / 1000000} MHz" }),
            info("Utilization", busy?.let { decimal(it, "%") })))
    }
    private fun thermalLive(): Section {
        val status = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java)?.currentThermalStatus else null
        val statusLabel = when(status) { 0 -> "None"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"; 5 -> "Emergency"; 6 -> "Shutdown"; else -> UNKNOWN }
        val rows = mutableListOf(info("Thermal throttling status", statusLabel))
        val zones = runCatching { File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") } }.getOrNull().orEmpty()
        zones.forEach { zone ->
            val type = read("${zone.path}/type")?.trim() ?: return@forEach
            val temp = number("${zone.path}/temp") ?: return@forEach
            // Linux thermal zone ABI reports millidegrees Celsius.
            if (temp in -40000L..150000L) rows.add(info(type, decimal(temp / 1000.0, "°C")))
        }
        return Section("Thermals", rows)
    }
    private fun displayLive(): Section {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        return Section("Display", listOf(info("Resolution", mode?.let { "${it.physicalWidth} × ${it.physicalHeight}" }),
            info("Refresh rate", mode?.let { decimal(it.refreshRate.toDouble(), "Hz") })))
    }
    private fun ratio(used: Long, total: Long) = if (total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    private fun decimal(n: Double, unit: String) = String.format(Locale.getDefault(), "%.1f %s", n, unit)
    private fun duration(ms: Long): String {
        val minutes = ms.coerceAtLeast(0) / 60000
        return "${minutes / 1440}d ${minutes / 60 % 24}h ${minutes % 60}m"
    }
    private fun cpuIds(): List<Int> {
        val found = runCatching { File("/sys/devices/system/cpu").list()?.mapNotNull {
            if (it.matches(Regex("cpu[0-9]+"))) it.removePrefix("cpu").toIntOrNull() else null
        }?.sorted() }.getOrNull().orEmpty()
        return found.ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).toList() }
    }
    private fun batteryProperty(manager: BatteryManager?, property: Int): Int? = runCatching {
        manager?.getIntProperty(property)?.takeUnless { it == Int.MIN_VALUE }
    }.getOrNull()
    private fun status(value: Int?) = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        else -> UNKNOWN
    }
    private fun health(value: Int?) = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> UNKNOWN
    }
    private fun Intent?.int(key: String): Int? = this?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }

    fun live(): LiveInfo {
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(memory)
        val used = (memory.totalMem - memory.availMem).coerceAtLeast(0)
        val root = Environment.getExternalStorageDirectory()
        val total = root.totalSpace
        val storageUsed = (total - root.usableSpace).coerceAtLeast(0)
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = intent.int(BatteryManager.EXTRA_LEVEL)
        val scale = intent.int(BatteryManager.EXTRA_SCALE)
        val percent = if (level != null && scale != null && scale > 0 && level in 0..scale) (100.0 * level / scale).toInt() else null
        val temp = intent.int(BatteryManager.EXTRA_TEMPERATURE)?.let { decimal(it / 10.0, "°C") }
        val voltageMv = intent.int(BatteryManager.EXTRA_VOLTAGE)?.takeIf { it > 0 }
        val voltage = voltageMv?.let { "$it mV" }
        val batteryStatus = intent.int(BatteryManager.EXTRA_STATUS)
        val currentUa = batteryProperty(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val power = batteryWatts(currentUa, voltageMv)
        val powerLabel = when(batteryStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging power"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharge power"
            else -> "Battery power"
        }
        val current = currentUa?.let { decimal(it / 1000.0, "mA") }
        val average = batteryProperty(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.let { decimal(it / 1000.0, "mA") }
        val chargeUaH = batteryProperty(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }
        val charge = chargeUaH?.let { decimal(it / 1000.0, "mAh") }
        val capacity = estimatedCapacityMah(chargeUaH,percent)
        val remaining = if(Build.VERSION.SDK_INT>=28 && batteryStatus==BatteryManager.BATTERY_STATUS_CHARGING)
            runCatching{bm?.computeChargeTimeRemaining()?.takeIf{it>0}}.getOrNull() else null
        val design = number("/sys/class/power_supply/battery/charge_full_design")?.takeIf{it in 100_000L..30_000_000L}
        val full = number("/sys/class/power_supply/battery/charge_full")?.takeIf{it in 100_000L..30_000_000L}
        val capacityHealth = if(design!=null && full!=null) full.toDouble()*100/design else null
        val plugged = when (intent.int(BatteryManager.EXTRA_PLUGGED)) {
            0 -> "Unplugged"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 -> "Dock"
            else -> UNKNOWN
        }
        val elapsed = SystemClock.elapsedRealtime()
        val awake = SystemClock.uptimeMillis()
        val cores = coreReadings()
        val cpu = cores.map { core -> info("CPU ${core.id}", if (core.online == false) "Offline" else core.current?.let { "${it / 1000} MHz" }) }
        val batteryRows = mutableListOf(info("Level", percent?.let { "$it%" }), info("Status", status(intent.int(BatteryManager.EXTRA_STATUS))),
            info("Health (system status)", health(intent.int(BatteryManager.EXTRA_HEALTH))), info("Power source", plugged),
            info("Technology", intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)), info("Temperature", temp), info("Voltage", voltage),
            info(powerLabel, power?.let { decimal(it, "W") }), info("Instant current (+ charging)", current), info("Average current (+ charging)", average), info("Remaining charge", charge))
        batteryRows.addAll(listOf(info("Time to full (system estimate)",remaining?.let{duration(it)}),
            info("Estimated full capacity",capacity?.let{decimal(it,"mAh")}),
            info("Design capacity (reported)",design?.let{decimal(it/1000.0,"mAh")}),
            info("Full capacity (reported)",full?.let{decimal(it/1000.0,"mAh")}),
            info("Capacity health (reported)",capacityHealth?.let{decimal(it,"%")})))
        if (Build.VERSION.SDK_INT >= 34) batteryRows.add(info("Charge cycles", intent.int(BatteryManager.EXTRA_CYCLE_COUNT)?.takeIf { it >= 0 }))
        return LiveInfo(listOf(
            Meter("Memory", if (memory.totalMem > 0) fmt(used) else UNKNOWN, "of " + fmt(memory.totalMem), ratio(used, memory.totalMem)),
            Meter("Storage", if (total > 0) fmt(storageUsed) else UNKNOWN, "of " + fmt(total), ratio(storageUsed, total)),
            Meter("Battery", percent?.let { "$it%" } ?: UNKNOWN, listOfNotNull(temp, status(intent.int(BatteryManager.EXTRA_STATUS))).joinToString(" · "), percent?.div(100f)),
            Meter("Uptime", duration(elapsed), "Deep sleep " + duration(elapsed - awake), ratio((elapsed - awake).coerceAtLeast(0), elapsed))
        ), Section("CPU frequencies", cpu), Section("Memory", listOf(info("Total RAM", fmt(memory.totalMem)), info("Available RAM", fmt(memory.availMem)), info("Used RAM (estimate)", fmt(used)), info("Low memory", memory.lowMemory))),
            Section("Battery", batteryRows), Section("Uptime", listOf(info("Since boot", duration(elapsed)), info("Awake", duration(awake)), info("Deep sleep (estimate)", duration(elapsed - awake)))), gpuLive(), thermalLive(), network(), displayLive(), cores)
    }

    fun static(): StaticInfo {
        val gpu = gpuInfo()
        val ids = coreIds
        val cpu = mutableListOf(info("SoC", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null),
            info("SoC manufacturer", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null),
            info("Hardware", Build.HARDWARE), info("Detected cores", ids.size), info("Supported ABIs", Build.SUPPORTED_ABIS.joinToString()))
        ids.forEach { id ->
            val path = "/sys/devices/system/cpu/cpu$id/cpufreq"
            cpu.add(info("CPU $id range", listOf(mhz("$path/cpuinfo_min_freq"), mhz("$path/cpuinfo_max_freq")).joinToString(" – ")))
        }
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        val displays = listOf(info("Physical mode", mode?.let { "${it.physicalWidth} × ${it.physicalHeight}" }), info("Refresh rate", mode?.let { decimal(it.refreshRate.toDouble(), "Hz") }),
            info("Supported refresh rates", display?.supportedModes?.map { it.refreshRate }?.distinct()?.sorted()?.joinToString { decimal(it.toDouble(), "Hz") }),
            info("Density", metrics.densityDpi.takeIf { it > 0 }?.let { "$it dpi" }), info("HDR types (Android IDs)", display?.hdrCapabilities?.supportedHdrTypes?.joinToString()))
        val hardware = listOf(Section("Processor", cpu), Section("Graphics", listOf(info("GPU renderer", gpu.renderer), info("Vendor", gpu.vendor),
            info("OpenGL ES", gpu.version), info("GL extensions (current context)", gpu.extensions),
            info("Vulkan support", context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)),
            info("Vulkan API (reported)", context.packageManager.systemAvailableFeatures.firstOrNull { it.name == android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version?.takeIf { it > 0 }?.let { "${it ushr 22}.${(it ushr 12) and 1023}.${it and 4095}" }))),
            Section("Display", displays))
        val system = listOf(Section("Device", listOf(info("Manufacturer", Build.MANUFACTURER), info("Brand", Build.BRAND), info("Model", Build.MODEL), info("Device", Build.DEVICE), info("Product", Build.PRODUCT), info("Board", Build.BOARD))),
            Section("Android", listOf(info("Android version", Build.VERSION.RELEASE), info("API level", Build.VERSION.SDK_INT), info("Security patch", Build.VERSION.SECURITY_PATCH),
                info("Build ID", Build.ID), info("Build number", Build.DISPLAY), info("Build type", Build.TYPE), info("Kernel release", System.getProperty("os.version")), info("Baseband", Build.getRadioVersion()),
                info("Bootloader", Build.BOOTLOADER))))
        val sensors = context.getSystemService(SensorManager::class.java)?.getSensorList(Sensor.TYPE_ALL).orEmpty().map {
            Section(it.name, listOf(info("Vendor", it.vendor), info("Type", it.stringType), info("Version", it.version), info("Maximum range", it.maximumRange),
                info("Resolution", it.resolution), info("Power", "${it.power} mA"), info("Minimum delay", "${it.minDelay} µs"), info("Wake-up", it.isWakeUpSensor)))
        }
        val cameras = runCatching {
            val manager = context.getSystemService(CameraManager::class.java) ?: return@runCatching emptyList<Section>()
            manager.cameraIdList.map { id ->
                val c = manager.getCameraCharacteristics(id)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> UNKNOWN
                }
                val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                Section("$facing camera ($id)", listOf(info("Sensor resolution", pixels?.let { "${it.width} × ${it.height}" }),
                    info("Megapixels", pixels?.let { decimal(it.width.toDouble() * it.height / 1000000, "MP") }),
                    info("Sensor size", size?.let { "${it.width} × ${it.height} mm" }),
                    info("Apertures", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.joinToString { "f/$it" }),
                    info("Focal lengths", c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString { "$it mm" }),
                    info("ISO range", c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)),
                    info("Flash", c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))))
            }
        }.getOrElse { listOf(Section("Cameras", listOf(info("Metadata", null)))) }
        return StaticInfo(hardware, system, sensors, cameras)
    }

    fun storage(): List<Section> = storageVolumes(context).map { volume ->
        val total = volume.root.totalSpace
        val available = volume.root.usableSpace
        Section(volume.label, listOf(info("Capacity", if (total > 0) fmt(total) else null), info("Used", if (total > 0) fmt((total - available).coerceAtLeast(0)) else null),
            info("Available", if (total > 0) fmt(available) else null), info("Mount path", volume.root.path)))
    }

    fun network(): Section {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val net = manager?.activeNetwork
        val cap = net?.let { manager?.getNetworkCapabilities(it) }
        val link = net?.let { manager?.getLinkProperties(it) }
        val transports = listOf(NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi", NetworkCapabilities.TRANSPORT_CELLULAR to "Mobile",
            NetworkCapabilities.TRANSPORT_ETHERNET to "Ethernet", NetworkCapabilities.TRANSPORT_VPN to "VPN", NetworkCapabilities.TRANSPORT_BLUETOOTH to "Bluetooth")
        return Section("Active connection", listOf(info("Connection", if (net == null) "Disconnected" else transports.filter { cap?.hasTransport(it.first) == true }.joinToString { it.second }),
            info("Internet validated", cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)),
            info("Metered", manager?.isActiveNetworkMetered), info("Interface", link?.interfaceName),
            info("Addresses", link?.linkAddresses?.joinToString()), info("DNS", link?.dnsServers?.joinToString { it.hostAddress.orEmpty() }),
            info("Estimated downlink", cap?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.let { "$it kbps" }),
            info("Estimated uplink", cap?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }?.let { "$it kbps" })))
    }

    private fun gpuInfo(): GpuInfo {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return GpuInfo()
        var ctx = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        var initialized = false
        try {
            initialized = EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)
            if (!initialized) return GpuInfo()
            val config = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            val attrs = intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            if (!EGL14.eglChooseConfig(display, attrs, 0, config, 0, 1, count, 0) || count[0] == 0) return GpuInfo()
            ctx = EGL14.eglCreateContext(display, config[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) ctx = EGL14.eglCreateContext(display, config[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) return GpuInfo()
            surface = EGL14.eglCreatePbufferSurface(display, config[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, ctx)) return GpuInfo()
            return GpuInfo(GLES20.glGetString(GLES20.GL_RENDERER) ?: UNKNOWN, GLES20.glGetString(GLES20.GL_VENDOR) ?: UNKNOWN,
                GLES20.glGetString(GLES20.GL_VERSION) ?: UNKNOWN, GLES20.glGetString(GLES20.GL_EXTENSIONS)?.split(' ')?.filter { it.isNotBlank() }?.sorted()?.joinToString("\n") ?: UNKNOWN)
        } catch (_: Exception) { return GpuInfo() }
        finally {
            if (initialized) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, ctx)
            }
            EGL14.eglReleaseThread()
        }
    }
}

// Charge-counter extrapolation is approximate; do not infer wear from it.
internal fun estimatedCapacityMah(chargeUaH:Int?,percent:Int?):Double? {
    if(chargeUaH==null || chargeUaH<=0 || percent==null || percent !in 20..100)return null
    return (chargeUaH.toDouble()/1000*100/percent).takeIf{it in 100.0..30000.0}
}
