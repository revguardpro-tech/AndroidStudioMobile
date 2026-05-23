package com.androidstudiomobile.profiler

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilerScreen(
    navController: NavController,
    profiler: BuildProfiler
) {
    val currentProfile by profiler.currentProfile.collectAsState()
    val history by profiler.history.collectAsState()
    val isRunning by profiler.isRunning.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build Profiler") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ── Cabeçalho com métricas ao vivo ──────────────────────────────
            if (isRunning) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Build em progresso…", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        val mem = profiler.currentMemoryKb() / 1024
                        Text("RAM: ${mem}MB", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Atual") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Histórico") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("APK") })
            }

            when (selectedTab) {
                0 -> CurrentBuildTab(currentProfile)
                1 -> HistoryTab(history, profiler)
                2 -> ApkTab(currentProfile)
            }
        }
    }
}

// ── Aba Build Atual ───────────────────────────────────────────────────────────

@Composable
private fun CurrentBuildTab(profile: BuildProfiler.BuildProfile?) {
    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Speed, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Nenhum build registrado ainda")
                Text("Faça um build para ver as métricas", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Resumo
        item {
            SummaryCard(profile)
        }
        // Fases
        item {
            Text("Fases do Build", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            PhaseTimeline(profile)
        }
        items(profile.phases) { phase ->
            PhaseCard(phase, profile.totalDurationMs)
        }
    }
}

@Composable
private fun SummaryCard(profile: BuildProfiler.BuildProfile) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (profile.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    tint = if (profile.success) Color(0xFF43A047) else Color(0xFFE53935)
                )
                Text(if (profile.success) "Build concluído" else "Build falhou",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricBox("Tempo total", "%.1fs".format(profile.totalDurationSec), Icons.Default.Timer)
                MetricBox("Pico de RAM", "${profile.peakMemoryKb / 1024}MB", Icons.Default.Memory)
                MetricBox("Tamanho APK", "%.1fMB".format(profile.apkSizeMb), Icons.Default.Android)
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseTimeline(profile: BuildProfiler.BuildProfile) {
    if (profile.phases.isEmpty()) return
    val total = profile.totalDurationMs.coerceAtLeast(1)
    val phaseColors = listOf(Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00), Color(0xFF7B1FA2), Color(0xFFD32F2F))

    Canvas(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        var x = 0f
        profile.phases.forEachIndexed { i, phase ->
            val w = (phase.durationMs.toFloat() / total) * size.width
            drawRect(phaseColors[i % phaseColors.size], Offset(x, 0f), Size(w.coerceAtLeast(2f), size.height))
            x += w
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        profile.phases.forEachIndexed { i, phase ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(8.dp).background(phaseColors[i % phaseColors.size], RoundedCornerShape(2.dp)))
                Text(phase.name, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PhaseCard(phase: BuildProfiler.PhaseResult, totalMs: Long) {
    val fraction = if (totalMs > 0) phase.durationMs.toFloat() / totalMs else 0f
    val animFraction by animateFloatAsState(fraction, tween(600))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (phase.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null, Modifier.size(16.dp),
                    tint = if (phase.success) Color(0xFF43A047) else Color(0xFFE53935)
                )
                Spacer(Modifier.width(6.dp))
                Text(phase.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${phase.durationMs}ms", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            LinearProgressIndicator(progress = { animFraction }, modifier = Modifier.fillMaxWidth())
            if (phase.memoryDeltaKb != 0L) {
                val sign = if (phase.memoryDeltaKb > 0) "+" else ""
                Text("Δ RAM: ${sign}${phase.memoryDeltaKb / 1024}MB",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Aba Histórico ─────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(history: List<BuildProfiler.BuildProfile>, profiler: BuildProfiler) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum build no histórico")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(history.reversed()) { profile ->
            val regression = profiler.compareWithPrevious(profile)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (profile.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        null, tint = if (profile.success) Color(0xFF43A047) else Color(0xFFE53935)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Build #${profile.buildId.takeLast(6)}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${profile.phases.size} fases · %.1fs".format(profile.totalDurationSec),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    regression?.let { r ->
                        val color = if (r.hasRegression) Color(0xFFE53935) else Color(0xFF43A047)
                        Text("${r.sign}%.1fs".format(r.buildTimeDeltaSec), color = color, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    Text("${profile.peakMemoryKb / 1024}MB", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ── Aba APK ───────────────────────────────────────────────────────────────────

@Composable
private fun ApkTab(profile: BuildProfiler.BuildProfile?) {
    if (profile == null || profile.apkBreakdown.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Faça um build com APK para ver a análise")
        }
        return
    }

    val colors = listOf(Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFF7B1FA2), Color(0xFFD32F2F), Color(0xFF0097A7))
    val total = profile.apkBreakdown.values.sum().coerceAtLeast(1)
    val entries = profile.apkBreakdown.entries.sortedByDescending { it.value }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Composição do APK (%.1fMB)".format(profile.apkSizeMb),
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        // Gráfico de barras horizontal
        entries.forEachIndexed { i, (cat, size) ->
            val fraction = size.toFloat() / total
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat, fontSize = 12.sp)
                    Text("%.1fKB".format(size / 1024f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = colors[i % colors.size],
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
