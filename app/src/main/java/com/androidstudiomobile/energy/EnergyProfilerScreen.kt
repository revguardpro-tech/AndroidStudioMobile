package com.androidstudiomobile.energy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EnergyViewModel : ViewModel() {
    private lateinit var profiler: EnergyProfiler
    private val _snaps = MutableStateFlow<List<EnergySnapshot>>(emptyList())
    val snaps = _snaps.asStateFlow()
    private val _current = MutableStateFlow(EnergySnapshot())
    val current = _current.asStateFlow()
    var pkg by mutableStateOf(""); var isRunning by mutableStateOf(false)
    private var job: Job? = null

    fun init(ctx: android.content.Context) { profiler = EnergyProfiler(ctx) }

    fun start() {
        if (isRunning || pkg.isBlank()) return; isRunning = true
        job = viewModelScope.launch {
            profiler.profileStream(pkg).collect { s -> _current.value = s; _snaps.value = (_snaps.value + s).takeLast(60) }
        }
    }
    fun stop() { job?.cancel(); isRunning = false }
    fun reset() = viewModelScope.launch { profiler.reset(); _snaps.value = emptyList(); _current.value = EnergySnapshot() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyProfilerScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: EnergyViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init(ctx) }
    val cur by vm.current.collectAsState()
    val snaps by vm.snaps.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("Energy Profiler", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(vm.pkg, { vm.pkg = it }, Modifier.weight(1f), label = { Text("Package", color = Color.Gray, fontSize = 11.sp) }, singleLine = true, colors = studioColors())
                    Spacer(Modifier.width(6.dp))
                    if (vm.isRunning)
                        Button({ vm.stop() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) { Text("Stop") }
                    else
                        Button({ vm.start() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Start") }
                    Spacer(Modifier.width(4.dp))
                    IconButton({ vm.reset() }) { Icon(Icons.Default.Refresh, null, tint = Color.Gray) }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                TotalCard(cur.totalMah)
                Spacer(Modifier.height(8.dp))
                Text("By Component", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                val mx = maxOf(cur.cpuMah, cur.networkMah, cur.gpsMah, cur.wifiMah, cur.bluetoothMah, 0.001)
                ComponentBar("CPU",       cur.cpuMah,       mx, Color(0xFFFF9800))
                ComponentBar("Network",   cur.networkMah,   mx, Color(0xFF2196F3))
                ComponentBar("GPS",       cur.gpsMah,       mx, Color(0xFF4CAF50))
                ComponentBar("Wi-Fi",     cur.wifiMah,      mx, Color(0xFF9C27B0))
                ComponentBar("Bluetooth", cur.bluetoothMah, mx, Color(0xFF00BCD4))
            }
            item { MiniChart(snaps) }
            if (cur.wakelocks.isNotEmpty()) {
                item { Text("Wakelocks (top ${minOf(cur.wakelocks.size, 10)})", color = Color.Gray, fontSize = 11.sp) }
                items(cur.wakelocks.take(10)) { wl ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF252526), RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Color(0xFFFFEB3B), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(wl.name, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("${wl.durationMs}ms ×${wl.count}", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable fun TotalCard(mah: Double) {
    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color(0xFF007ACC), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Total Estimated", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("%.4f mAh".format(mah), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable fun ComponentBar(label: String, v: Double, max: Double, color: Color) {
    val frac by animateFloatAsState((v / max).toFloat().coerceIn(0f, 1f), label = "")
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3C3C3C))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color.copy(alpha = 0.8f)))
        }
        Text("%.4f".format(v), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(76.dp).padding(start = 6.dp))
    }
}

@Composable fun MiniChart(snaps: List<EnergySnapshot>) {
    if (snaps.isEmpty()) return
    val mx = snaps.maxOf { it.totalMah }.coerceAtLeast(0.001)
    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("mAh — last ${snaps.size} samples", color = Color.Gray, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.Bottom) {
                snaps.forEach { s ->
                    val h = ((s.totalMah / mx) * 50).dp
                    Box(Modifier.weight(1f).height(h).padding(horizontal = 1.dp).background(Color(0xFF007ACC).copy(alpha = 0.7f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun studioColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
