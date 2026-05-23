package com.androidstudiomobile.devicefarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val apiLevel: Int = 33,
    val width: Int = 1080, val height: Int = 2340,
    val dpi: Int = 420, val locale: String = "en_US",
    val container: ContainerType = ContainerType.BLACKBOX,
    val isRunning: Boolean = false,
    val screenshot: Bitmap? = null,
    val lastLog: String = "",
    val installedApk: String? = null
)

enum class ContainerType(val pkg: String, val label: String) {
    BLACKBOX("com.blackbox.android", "BlackBox"),
    VMOS("com.vmos.pro", "VMOS Pro"),
    ISLAND("com.oasisfeng.island", "Island"),
    SHELTER("net.typeblog.shelter", "Shelter")
}

data class FarmResult(val deviceId: String, val screenshot: Bitmap?, val logs: String, val ok: Boolean)

class VirtualDeviceManager(private val context: Context) {

    companion object { private const val TAG = "VirtualDeviceManager"; const val MAX = 3 }

    private val _devices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val devices = _devices.asStateFlow()

    fun add(p: DeviceProfile): Boolean {
        if (_devices.value.size >= MAX) return false
        _devices.value = _devices.value + p; return true
    }

    fun remove(id: String) { stop(id); _devices.value = _devices.value.filter { it.id != id } }

    fun start(id: String) {
        update(id) { it.copy(isRunning = true) }
        val d = _devices.value.find { it.id == id } ?: return
        runCatching {
            Runtime.getRuntime().exec(arrayOf("am","start","-n","${d.container.pkg}/.MainActivity",
                "--es","device_id",id,"--ei","width","${d.width}","--ei","height","${d.height}","--es","locale",d.locale)).waitFor()
        }
    }

    fun stop(id: String) = update(id) { it.copy(isRunning = false) }

    suspend fun install(id: String, apkPath: String): Boolean = withContext(Dispatchers.IO) {
        val d = _devices.value.find { it.id == id } ?: return@withContext false
        val ok = runCatching {
            Runtime.getRuntime().exec(arrayOf("am","broadcast","-a","com.androidstudiomobile.INSTALL_APK",
                "--es","pkg",d.container.pkg,"--es","apk",apkPath,"--es","device",id)).waitFor() == 0
        }.getOrDefault(false)
        if (ok) update(id) { it.copy(installedApk = apkPath) }
        ok
    }

    suspend fun installAll(apkPath: String): List<Pair<String,Boolean>> = coroutineScope {
        _devices.value.filter { it.isRunning }.map { d -> async { d.id to install(d.id, apkPath) } }.awaitAll()
    }

    suspend fun screenshot(id: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val path = "/sdcard/asmfarm_$id.png"
            Runtime.getRuntime().exec(arrayOf("sh","-c","screencap -p $path")).waitFor()
            val f = File(path); if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(path).also { bmp -> update(id) { it.copy(screenshot = bmp) }; f.delete() }
        } catch (e: Exception) { Log.w(TAG, "screenshot: ${e.message}"); null }
    }

    suspend fun screenshotAll(): List<FarmResult> = coroutineScope {
        _devices.value.filter { it.isRunning }.map { d -> async {
            val bmp = screenshot(d.id); val logs = logs(d.id)
            FarmResult(d.id, bmp, logs, bmp != null)
        }}.awaitAll()
    }

    suspend fun logs(id: String): String = withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("logcat","-d","-v","brief","--pid","-1"))
            p.inputStream.bufferedReader().readLines().takeLast(100).joinToString("\n")
                .also { update(id) { d -> d.copy(lastLog = it.takeLast(200)) } }
        } catch (_: Exception) { "" }
    }

    fun detectContainers(): List<Pair<ContainerType,Boolean>> = ContainerType.values().map { t ->
        t to runCatching { context.packageManager.getPackageInfo(t.pkg, 0); true }.getOrDefault(false)
    }

    private fun update(id: String, t: (DeviceProfile) -> DeviceProfile) {
        _devices.value = _devices.value.map { if (it.id == id) t(it) else it }
    }
}
