package com.androidstudiomobile.semantic

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

class AdvancedRefactorViewModel : ViewModel() {
    private val analyzer = SemanticAnalyzer()
    private val _ci = MutableStateFlow<ClassInfo?>(null)
    val classInfo = _ci.asStateFlow()
    private val _preview = MutableStateFlow<RefactorPreview?>(null)
    val preview = _preview.asStateFlow()

    var src         by mutableStateOf("")
    var fileName    by mutableStateOf("MyClass.kt")
    var targetName  by mutableStateOf("")
    var newPkg      by mutableStateOf("")
    var selectedMethods by mutableStateOf<Set<String>>(emptySet())
    var status      by mutableStateOf("Paste Kotlin source to begin")
    var isAnalyzing by mutableStateOf(false)

    fun analyze() = viewModelScope.launch {
        isAnalyzing = true; status = "Analyzing…"
        _ci.value = analyzer.analyze(src, fileName)
        status = _ci.value?.let { "✓ ${it.name} — ${it.methods.size} methods, ${it.properties.size} props" } ?: "No class found"
        isAnalyzing = false
    }

    fun previewInline(m: MethodInfo)       { _preview.value = analyzer.inlineMethod(src, m) }
    fun previewChangeSig(m: MethodInfo)    { _preview.value = analyzer.changeSignature(src, m, m.parameters) }
    fun previewExtractInterface()           { _ci.value?.let { _preview.value = analyzer.extractInterface(it, selectedMethods.toList()) } }
    fun previewSafeDelete()                { _preview.value = analyzer.safeDelete(src, targetName) }
    fun previewMoveClass()                 { _ci.value?.let { _preview.value = analyzer.moveClass(src, it, newPkg) } }
    fun apply()                            { _preview.value?.refactoredCode?.let { src = it }; _preview.value = null }
    fun dismissPreview()                   { _preview.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedRefactorScreen(navController: NavController, projectPath: String) {
    val vm: AdvancedRefactorViewModel = viewModel()
    val ci by vm.classInfo.collectAsState()
    val preview by vm.preview.collectAsState()

    if (preview != null) {
        RefactorDiff(preview!!, onApply = { vm.apply() }, onCancel = { vm.dismissPreview() })
        return
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        // Editor pane
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Surface(color = Color(0xFF2D2D2D)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                        Text("Advanced Refactoring", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(vm.fileName, { vm.fileName = it }, Modifier.weight(1f), label = { Text("File name", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
                        Spacer(Modifier.width(6.dp))
                        Button({ vm.analyze() }, enabled = !vm.isAnalyzing, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Analyze") }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(vm.status, color = Color.Gray, fontSize = 11.sp)
                }
            }
            OutlinedTextField(vm.src, { vm.src = it }, Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                label = { Text("Kotlin source", color = Color.Gray) }, colors = studioColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
        }

        // Operations pane
        Surface(color = Color(0xFF252526), modifier = Modifier.width(260.dp).fillMaxHeight()) {
            if (ci == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Analyze code first", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ClassSummary(ci!!) }
                    item {
                        OpCard("Move Class", "→", Color(0xFF9C27B0)) {
                            OutlinedTextField(vm.newPkg, { vm.newPkg = it }, Modifier.fillMaxWidth(), label = { Text("New package", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
                            Spacer(Modifier.height(5.dp))
                            Button({ vm.previewMoveClass() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))) { Text("Preview", fontSize = 11.sp) }
                        }
                    }
                    item {
                        OpCard("Extract Interface", "⊂", Color(0xFF2196F3)) {
                            ci!!.methods.forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(m.name in vm.selectedMethods, { checked -> vm.selectedMethods = if (checked) vm.selectedMethods + m.name else vm.selectedMethods - m.name }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2196F3)))
                                    Text(m.name, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Button({ vm.previewExtractInterface() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) { Text("Preview", fontSize = 11.sp) }
                        }
                    }
                    item {
                        OpCard("Safe Delete", "✕", Color(0xFFFF5252)) {
                            OutlinedTextField(vm.targetName, { vm.targetName = it }, Modifier.fillMaxWidth(), label = { Text("Name", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = studioColors())
                            Spacer(Modifier.height(5.dp))
                            Button({ vm.previewSafeDelete() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) { Text("Check & Preview", fontSize = 11.sp) }
                        }
                    }
                    items(ci!!.methods) { m ->
                        Surface(color = Color(0xFF2D2D2D), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Row { Text("fun ", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontSize = 11.sp); Text(m.name, color = Color(0xFFDCDCAA), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                                Spacer(Modifier.height(5.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    OutlinedButton({ vm.previewInline(m) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.weight(1f)) { Text("Inline", fontSize = 10.sp, color = Color(0xFFFF9800)) }
                                    OutlinedButton({ vm.previewChangeSig(m) }, contentPadding = PaddingValues(6.dp), modifier = Modifier.weight(1f)) { Text("Sig", fontSize = 10.sp, color = Color(0xFF4CAF50)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun ClassSummary(ci: ClassInfo) {
    Surface(color = Color(0xFF2A3A4A), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(ci.name, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(ci.packageName, color = Color.Gray, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("${ci.methods.size} methods", "${ci.properties.size} props", "${ci.interfaces.size} ifaces").forEach { t ->
                    Surface(color = Color(0xFF1A2A3A), shape = RoundedCornerShape(4.dp)) { Text(t, color = Color(0xFF9CDCFE), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                }
            }
        }
    }
}

@Composable fun OpCard(title: String, icon: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF2D2D2D), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(5.dp)); Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(7.dp)); content()
        }
    }
}

@Composable fun RefactorDiff(p: RefactorPreview, onApply: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(12.dp)) {
        Text("Refactoring Preview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(p.description, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        if (!p.safe) Surface(color = Color(0xFF4A2A2A), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) { Text("⚠ Cannot apply — see description", color = Color(0xFFFF6B6B), fontSize = 11.sp, modifier = Modifier.padding(8.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiffPane("Before", p.originalCode, Color(0xFF2A1A1A), Color(0xFFFF6B6B), Modifier.weight(1f))
            DiffPane("After",  p.refactoredCode, Color(0xFF1A2A1A), Color(0xFF90EE90), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button(onApply, Modifier.weight(1f), enabled = p.safe, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Apply") }
        }
    }
}

@Composable fun DiffPane(label: String, code: String, bg: Color, accent: Color, modifier: Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
            Text(code, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun studioColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
