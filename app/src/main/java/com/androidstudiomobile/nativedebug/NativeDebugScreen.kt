package com.androidstudiomobile.nativedebug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch

class NativeDebugViewModel : ViewModel() {
    private lateinit var engine: NativeDebugEngine
    val stackFrames get() = engine.stackFrames
    val variables   get() = engine.variables
    val breakpoints get() = engine.breakpoints
    val events      get() = engine.events

    var targetPkg   by mutableStateOf("")
    var isConnected by mutableStateOf(false)
    var status      by mutableStateOf("Disconnected")
    var evalInput   by mutableStateOf("")
    var evalResult  by mutableStateOf("")
    var bpFile      by mutableStateOf("")
    var bpLine      by mutableStateOf("")
    var selectedTab by mutableStateOf(0)

    fun init(ctx: android.content.Context) { engine = NativeDebugEngine(ctx) }

    fun connect() = viewModelScope.launch {
        status = "Connecting…"
        isConnected = engine.startServer(targetPkg)
        status = if (isConnected) "Connected — $targetPkg" else "Failed to connect"
    }

    fun addBp()      = viewModelScope.launch { bpLine.toIntOrNull()?.let { engine.addBreakpoint(bpFile, it) }; bpFile = ""; bpLine = "" }
    fun removeBp(id: Int)  = viewModelScope.launch { engine.removeBreakpoint(id) }
    fun continueExec()     = viewModelScope.launch { engine.continueExec() }
    fun stepOver()         = viewModelScope.launch { engine.stepOver() }
    fun stepInto()         = viewModelScope.launch { engine.stepInto() }
    fun stepOut()          = viewModelScope.launch { engine.stepOut() }
    fun evaluate()         = viewModelScope.launch { evalResult = engine.evaluate(evalInput) }
    fun disconnect()       { engine.stop(); isConnected = false; status = "Disconnected" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDebugScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: NativeDebugViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init(ctx) }

    val frames by vm.stackFrames.collectAsState(initial = emptyList())
    val vars   by vm.variables.collectAsState(initial = emptyList())
    val bps    by vm.breakpoints.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        // Toolbar
        Surface(color = Color(0xFF2D2D2D), tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("LLDB Debugger", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    StatusDot(vm.isConnected, vm.status)
                }
                if (!vm.isConnected) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(vm.targetPkg, { vm.targetPkg = it }, Modifier.weight(1f), label = { Text("Target package", color = Color.Gray, fontSize = 11.sp) }, singleLine = true, colors = studioColors())
                        Spacer(Modifier.width(6.dp))
                        Button({ vm.connect() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Connect") }
                    }
                } else {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("▶" to { vm.continueExec() }, "↷" to { vm.stepOver() }, "↓" to { vm.stepInto() }, "↑" to { vm.stepOut() }).forEach { (sym, action) ->
                            Surface(Modifier.size(34.dp).clickable { action() }, color = Color(0xFF3C3C3C), shape = RoundedCornerShape(4.dp)) { Box(contentAlignment = Alignment.Center) { Text(sym, color = Color.White, fontSize = 16.sp) } }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton({ vm.disconnect() }) { Text("Disconnect", color = Color(0xFFFF5252), fontSize = 11.sp) }
                    }
                }
            }
        }

        TabRow(vm.selectedTab, containerColor = Color(0xFF252526), contentColor = Color(0xFF007ACC)) {
            listOf("Variables", "Stack", "Breakpoints", "Eval").forEachIndexed { i, t ->
                Tab(vm.selectedTab == i, { vm.selectedTab = i }, text = { Text(t, fontSize = 11.sp, color = if (vm.selectedTab == i) Color(0xFF007ACC) else Color.Gray) })
            }
        }

        when (vm.selectedTab) {
            0 -> VariablesTab(vars)
            1 -> StackTab(frames)
            2 -> BreakpointsTab(bps, vm)
            3 -> EvalTab(vm)
        }
    }
}

@Composable
fun VariablesTab(vars: List<DebugVariable>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (vars.isEmpty()) item { Text("No variables in current frame", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(vars) { v ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF252526), RoundedCornerShape(4.dp)).padding(8.dp)) {
                Text(v.name, color = Color(0xFF9CDCFE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                Text("(${v.type})", color = Color(0xFF4EC9B0), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                Text("= ${v.value}", color = Color(0xFFCE9178), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StackTab(frames: List<StackFrame>) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (frames.isEmpty()) item { Text("No stack frames", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(frames) { f ->
            Row(Modifier.fillMaxWidth().background(if (f.index == 0) Color(0xFF2A3A2A) else Color(0xFF252526), RoundedCornerShape(4.dp)).padding(8.dp)) {
                Text("#${f.index}", color = Color(0xFFDCDCAA), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                Column {
                    Text(f.function, color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("${f.file}:${f.line}", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun BreakpointsTab(bps: List<Breakpoint>, vm: NativeDebugViewModel) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(vm.bpFile, { vm.bpFile = it }, Modifier.weight(1f), label = { Text("File", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(vm.bpLine, { vm.bpLine = it }, Modifier.width(70.dp), label = { Text("Line", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
            IconButton({ vm.addBp() }) { Icon(Icons.Default.Add, null, tint = Color(0xFF4CAF50)) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(bps) { bp ->
                Row(Modifier.fillMaxWidth().background(Color(0xFF252526), RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("${bp.file}:${bp.line}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton({ vm.removeBp(bp.id) }, Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun EvalTab(vm: NativeDebugViewModel) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(vm.evalInput, { vm.evalInput = it }, Modifier.weight(1f), label = { Text("Expression", color = Color.Gray) }, singleLine = true, colors = studioColors())
            Spacer(Modifier.width(6.dp))
            Button({ vm.evaluate() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Eval") }
        }
        if (vm.evalResult.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(color = Color(0xFF252526), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(vm.evalResult, color = Color(0xFFCE9178), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun StatusDot(ok: Boolean, msg: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (ok) Color(0xFF4CAF50) else Color(0xFFFF5252), RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(msg, color = Color.Gray, fontSize = 10.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun studioColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
