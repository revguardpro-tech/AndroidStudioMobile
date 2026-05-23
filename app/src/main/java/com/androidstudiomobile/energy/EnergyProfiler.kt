package com.androidstudiomobile.energy

import android.content.Context
import android.os.Build
import android.os.health.SystemHealthManager
import android.os.health.UidHealthStats
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class EnergySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val cpuMah: Double = 0.0,
    val networkMah: Double = 0.0,
    val gpsMah: Double = 0.0,
    val wifiMah: Double = 0.0,
    val bluetoothMah: Double = 0.0,
    val totalMah: Double = 0.0,
    val wakelocks: List<WakelockEntry> = emptyList()
)

data class WakelockEntry(val name: String, val durationMs: Long, val count: Int)

class EnergyProfiler(private val context: Context) {

    companion object { private const val TAG = "EnergyProfiler" }

    fun profileStream(pkg: String, intervalMs: Long = 2000L): Flow<EnergySnapshot> = flow {
        while (true) { emit(snapshot(pkg)); delay(intervalMs) }
    }

    suspend fun snapshot(pkg: String): EnergySnapshot = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fromSystemHealth(pkg) ?: fromDumpsys(pkg)
        } else fromDumpsys(pkg)
    }

    private fun fromSystemHealth(pkg: String): EnergySnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            val shm = context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as? SystemHealthManager ?: return null
            val uid = context.packageManager.getApplicationInfo(pkg, 0).uid
            val s = shm.takeUidSnapshot(uid)
            val cpu  = if (s.hasMeasurement(UidHealthStats.MEASUREMENT_CPU_POWER_MAMS)) s.getMeasurement(UidHealthStats.MEASUREMENT_CPU_POWER_MAMS) / 3_600_000.0 else 0.0
            val wifi = if (s.hasMeasurement(UidHealthStats.MEASUREMENT_WIFI_POWER_MAMS)) s.getMeasurement(UidHealthStats.MEASUREMENT_WIFI_POWER_MAMS) / 3_600_000.0 else 0.0
            val bt   = if (s.hasMeasurement(UidHealthStats.MEASUREMENT_BLUETOOTH_POWER_MAMS)) s.getMeasurement(UidHealthStats.MEASUREMENT_BLUETOOTH_POWER_MAMS) / 3_600_000.0 else 0.0
            EnergySnapshot(cpuMah = cpu, wifiMah = wifi, bluetoothMah = bt, totalMah = cpu + wifi + bt)
        } catch (e: Exception) { Log.w(TAG, "SystemHealth: ${e.message}"); null }
    }

    private fun fromDumpsys(pkg: String): EnergySnapshot {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys batterystats --charged $pkg"))
            val raw = p.inputStream.bufferedReader().readText(); p.waitFor()
            parseDumpsys(raw)
        } catch (e: Exception) { Log.e(TAG, "dumpsys failed", e); EnergySnapshot() }
    }

    private fun parseDumpsys(raw: String): EnergySnapshot {
        var cpu = 0.0; var net = 0.0; var gps = 0.0; var wifi = 0.0
        val wakelocks = mutableListOf<WakelockEntry>()
        for (line in raw.lines()) {
            val t = line.trim()
            val mahRx = Regex("""([\d.]+)\s*mah""", RegexOption.IGNORE_CASE)
            if (t.startsWith("Computed drain:") && cpu == 0.0)  cpu  = mahRx.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (t.contains("Mobile network", true) || t.contains("Mobile data", true)) net += mahRx.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (t.startsWith("GPS") && t.contains("mah", true)) gps += mahRx.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (t.startsWith("Wifi") && t.contains("mah", true)) wifi += mahRx.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            Regex("""Wake lock (.+?):\s+(\d+)ms.*?\((\d+) times\)""").find(t)?.let { m ->
                wakelocks.add(WakelockEntry(m.groupValues[1], m.groupValues[2].toLong(), m.groupValues[3].toInt()))
            }
        }
        return EnergySnapshot(cpuMah = cpu, networkMah = net, gpsMah = gps, wifiMah = wifi,
            totalMah = cpu + net + gps + wifi, wakelocks = wakelocks.sortedByDescending { it.durationMs })
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        runCatching { Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys batterystats --reset")).waitFor() }
    }
}
