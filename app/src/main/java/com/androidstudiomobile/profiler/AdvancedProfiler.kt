package com.androidstudiomobile.profiler

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MemorySnapshot(
    val timestamp: Long,
    val totalMemory: Long,
    val nativeHeap: Long,
    val javaHeap: Long,
    val gcCount: Long
)

data class CpuSnapshot(
    val timestamp: Long,
    val usage: Float,
    val userTime: Long,
    val systemTime: Long
)

class AdvancedProfiler(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    private val _memorySnapshots = MutableStateFlow<List<MemorySnapshot>>(emptyList())
    val memorySnapshots: StateFlow<List<MemorySnapshot>> = _memorySnapshots.asStateFlow()

    private val _cpuSnapshots = MutableStateFlow<List<CpuSnapshot>>(emptyList())
    val cpuSnapshots: StateFlow<List<CpuSnapshot>> = _cpuSnapshots.asStateFlow()

    fun captureMemory(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val snapshot = MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            totalMemory = runtime.totalMemory(),
            nativeHeap = getNativeHeapSize(),
            javaHeap = runtime.totalMemory() - runtime.freeMemory(),
            gcCount = getGcCount()
        )
        
        _memorySnapshots.value = _memorySnapshots.value + snapshot
        return snapshot
    }

    fun captureCpu(): CpuSnapshot {
        val stat = readProcStat()
        return CpuSnapshot(
            timestamp = System.currentTimeMillis(),
            usage = calculateCpuUsage(stat),
            userTime = stat[0],
            systemTime = stat[1]
        )
    }

    private fun getNativeHeapSize(): Long {
        return try {
            Debug.getNativeHeap().sumOf { it.size }
        } catch (_: Exception) {
            0L
        }
    }

    private fun getGcCount(): Long {
        return try {
            Debug.getGlobalGcInvocationCount().toLong()
        } catch (_: Exception) {
            0L
        }
    }

    private fun readProcStat(): LongArray {
        return try {
            val stat = File("/proc/self/stat").readText().split(" ")
            longArrayOf(
                stat.getOrNull(13)?.toLong() ?: 0L,
                stat.getOrNull(14)?.toLong() ?: 0L
            )
        } catch (_: Exception) {
            longArrayOf(0L, 0L)
        }
    }

    private fun calculateCpuUsage(stat: LongArray): Float {
        return if (stat[0] > 0 && stat[1] > 0) {
            ((stat[0] + stat[1]).toFloat() / 100) * 10
        } else {
            0f
        }
    }

    fun clearSnapshots() {
        _memorySnapshots.value = emptyList()
        _cpuSnapshots.value = emptyList()
    }
}
