package com.androidstudiomobile.testrunner

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TestRunnerViewModel : ViewModel() {
    private val runner = InstrumentedTestRunner()
    private val _cases = MutableStateFlow<List<TestCase>>(emptyList())
    val cases = _cases.asStateFlow()
    var pkg         by mutableStateOf("")
    var runner2     by mutableStateOf("androidx.test.runner.AndroidJUnitRunner")
    var classFilter by mutableStateOf("")
    var isRunning   by mutableStateOf(false)
    var rawOutput   by mutableStateOf("")
    var result      by mutableStateOf<TestRunResult?>(null)
    var selectedTab by mutableStateOf(0)
    val shizuku get() = runner.isShizukuAvailable

    fun run() = viewModelScope.launch {
        if (pkg.isBlank()) return@launch
        isRunning = true; _cases.value = emptyList(); result = null
        runner.runTests(pkg, runner2, classFilter.takeIf { it.isNotBlank() }).collect { _cases.value = _cases.value + it }
        val r = runner.runAndWait(pkg, runner2, classFilter.takeIf { it.isNotBlank() })
        result = r; rawOutput = r.rawOutput; isRunning = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestRunnerScreen(navController: NavController) {
    val vm: TestRunnerViewModel = viewModel()
    val cases by vm.cases.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("Test Runner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    if (vm.shizuku) ShizukuBadge()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(vm.pkg, { vm.pkg = it }, Modifier.weight(1f), label = { Text("Package", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
                    OutlinedTextField(vm.classFilter, { vm.classFilter = it }, Modifier.weight(1f), label = { Text("Class filter", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
                }
                Spacer(Modifier.height(4.dp))
                Button({ vm.run(); vm.selectedTab = 0 }, enabled = !vm.isRunning, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (vm.isRunning) Color.Gray else Color(0xFF4CAF50))) {
                    if (vm.isRunning) { CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Text(if (vm.isRunning) "Running…" else "▶  Run Tests")
                }
            }
        }

        vm.result?.let { r ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryChip("${r.passed}", "Passed", Color(0xFF4CAF50))
                SummaryChip("${r.failed}", "Failed", Color(0xFFFF5252))
                SummaryChip("${r.skipped}", "Skipped", Color(0xFFFF9800))
                SummaryChip("${r.errors}", "Errors", Color(0xFFFF6B6B))
                Spacer(Modifier.weight(1f))
                Text("${r.total} total", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        TabRow(vm.selectedTab, containerColor = Color(0xFF252526), contentColor = Color(0xFF4CAF50)) {
            listOf("Results (${cases.size})", "Raw Output").forEachIndexed { i, t ->
                Tab(vm.selectedTab == i, { vm.selectedTab = i }, text = { Text(t, fontSize = 11.sp, color = if (vm.selectedTab == i) Color(0xFF4CAF50) else Color.Gray) })
            }
        }

        when (vm.selectedTab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                val grouped = cases.groupBy { it.className }
                grouped.forEach { (cls, cs) ->
                    item {
                        Text(cls.substringAfterLast('.'), color = Color(0xFF9CDCFE), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(horizontal = 14.dp, vertical = 5.dp))
                    }
                    items(cs) { tc -> TestCaseRow(tc) }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(10.dp)) {
                item { Text(vm.rawOutput.ifBlank { "No output yet." }, color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            }
        }
    }
}

@Composable fun TestCaseRow(tc: TestCase) {
    var expanded by remember { mutableStateOf(tc.status == TestStatus.FAILED || tc.status == TestStatus.ERROR) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TestIcon(tc.status); Spacer(Modifier.width(8.dp))
            Text(tc.methodName, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (tc.durationMs > 0) Text("${tc.durationMs}ms", color = Color.Gray, fontSize = 10.sp)
            if (tc.failureMessage != null) IconButton({ expanded = !expanded }, Modifier.size(24.dp)) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray, modifier = Modifier.size(14.dp)) }
        }
        if (expanded && tc.failureMessage != null) {
            Surface(color = Color(0xFF2A1A1A), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp), shape = RoundedCornerShape(4.dp)) {
                Text(tc.failureMessage, color = Color(0xFFFF6B6B), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
            }
        }
        Divider(color = Color(0xFF252526), thickness = 0.5.dp)
    }
}

@Composable fun TestIcon(s: TestStatus) {
    val (icon, color) = when (s) {
        TestStatus.PASSED  -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        TestStatus.FAILED  -> Icons.Default.Cancel      to Color(0xFFFF5252)
        TestStatus.SKIPPED -> Icons.Default.SkipNext    to Color(0xFFFF9800)
        TestStatus.ERROR   -> Icons.Default.Error       to Color(0xFFFF6B6B)
        else               -> Icons.Default.Schedule    to Color.Gray
    }
    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
}

@Composable fun SummaryChip(count: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(count, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.width(3.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable fun ShizukuBadge() {
    Surface(color = Color(0xFF1A3A1A), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), RoundedCornerShape(3.dp))); Spacer(Modifier.width(4.dp))
            Text("Shizuku", color = Color(0xFF4CAF50), fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun studioColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
