package com.androidstudiomobile.profiler

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class BuildProfiler(private val ctx: Context) {

    data class PhaseResult(
        val name: String,
        val durationMs: Long,
        val memoryDeltaKb: Long,
        val success: Boolean,
        val output: String = ""
    )

    data class BuildProfile(
        val buildId: String              = System.currentTimeMillis().toString(),
        val startMs: Long                = System.currentTimeMillis(),
        var endMs: Long                  = 0L,
        val phases: MutableList<PhaseResult> = mutableListOf(),
        var peakMemoryKb: Long           = 0L,
        var apkBytes: Long               = 0L,
        var apkBreakdown: Map<String, Long> = emptyMap(),
        var success: Boolean             = false
    ) {
        val totalDurationMs: Long   get() = endMs - startMs
        val totalDurationSec: Float get() = totalDurationMs / 1000f
        val apkSizeMb: Float        get() = apkBytes / 1024f / 1024f
    }

    data class RegressionResult(
        val buildTimeDeltaSec: Float,
        val apkDeltaBytes: Long,
        val hasRegression: Boolean
    ) {
        val sign: String get() = if (buildTimeDeltaSec > 0) "+" else ""
    }

    private val _currentProfile = MutableStateFlow<BuildProfile?>(null)
    val currentProfile: StateFlow<BuildProfile?> = _currentProfile

    private val _history = MutableStateFlow<List<BuildProfile>>(emptyList())
    val history: StateFlow<List<BuildProfile>> = _history

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var memJob: Job? = null
    private var peakMem = 0L

    fun begin(): BuildProfile {
        val p = BuildProfile()
        _currentProfile.value = p
        _isRunning.value = true
        peakMem = 0L
        startMemSampler()
        return p
    }

    suspend fun phase(p: BuildProfile, name: String, block: suspend () -> Pair<Boolean, String>): PhaseResult {
        val memBefore = currentMemoryKb()
        val t0 = System.currentTimeMillis()
        val (ok, out) = block()
        val elapsed = System.currentTimeMillis() - t0
        val result = PhaseResult(name, elapsed, currentMemoryKb() - memBefore, ok, out.take(1000))
        p.phases.add(result)
        _currentProfile.value = p.copy()
        return result
    }

    fun finish(p: BuildProfile, apkPath: String? = null) {
        p.endMs        = System.currentTimeMillis()
        p.success      = p.phases.all { it.success }
        p.peakMemoryKb = peakMem
        apkPath?.let { analyzeApk(it, p) }
        stopMemSampler()
        _currentProfile.value = p
        _history.value = (_history.value + p).takeLast(30)
        _isRunning.value = false
    }

    fun compareWithPrevious(current: BuildProfile): RegressionResult? {
        val prev = _history.value.dropLast(1).lastOrNull() ?: return null
        val deltaMs    = current.totalDurationMs - prev.totalDurationMs
        val deltaBytes = current.apkBytes - prev.apkBytes
        return RegressionResult(
            buildTimeDeltaSec = deltaMs / 1000f,
            apkDeltaBytes     = deltaBytes,
            hasRegression     = current.totalDurationMs > prev.totalDurationMs * 1.15 ||
                                current.apkBytes > prev.apkBytes * 1.10
        )
    }

    fun currentMemoryKb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024
    }

    fun availableMb(): Long {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.availMem / 1024 / 1024
    }

    private fun startMemSampler() {
        memJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val m = currentMemoryKb(); if (m > peakMem) peakMem = m
                delay(500)
            }
        }
    }

    private fun stopMemSampler() { memJob?.cancel() }

    private fun analyzeApk(path: String, p: BuildProfile) {
        val f = File(path)
        if (!f.exists()) return
        p.apkBytes = f.length()
        val map = mutableMapOf<String, Long>()
        runCatching {
            java.util.zip.ZipFile(f).use { zf ->
                zf.entries().asSequence().forEach { e ->
                    val cat = when {
                        e.name.endsWith(".dex")        -> "DEX (code)"
                        e.name.startsWith("res/")      -> "Resources"
                        e.name == "resources.arsc"     -> "Resource table"
                        e.name.startsWith("lib/")      -> "Native libs"
                        e.name.startsWith("assets/")   -> "Assets"
                        e.name.startsWith("META-INF/") -> "Signature"
                        else                           -> "Other"
                    }
                    map[cat] = (map[cat] ?: 0L) + e.compressedSize
                }
            }
        }
        p.apkBreakdown = map
    }
}
