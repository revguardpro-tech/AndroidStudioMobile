package com.androidstudiomobile.xmlcompose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

class XmlToComposeViewModel : ViewModel() {
    private val converter = XmlToComposeConverter()
    var xmlInput    by mutableStateOf(SAMPLE_XML)
    var result      by mutableStateOf<ConversionResult?>(null)
    var tab         by mutableStateOf(0)
    var isConverting by mutableStateOf(false)

    fun convert() {
        isConverting = true
        result = converter.convert(xmlInput)
        isConverting = false
        tab = 1
    }

    companion object {
        val SAMPLE_XML = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Hello World"
        android:textSize="20sp"/>

    <EditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Enter text here"/>

    <Button
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Click Me"/>

    <ImageView
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:src="@drawable/ic_launcher_foreground"/>

    <RecyclerView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"/>

</LinearLayout>"""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XmlToComposeScreen(navController: NavController) {
    val vm: XmlToComposeViewModel = viewModel()
    val clipboard: ClipboardManager = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        // Toolbar
        Surface(color = Color(0xFF2D2D2D)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Text("XML → Compose", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { vm.convert() },
                    enabled = !vm.isConverting && vm.xmlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))
                ) {
                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Convert")
                }
            }
        }

        TabRow(vm.tab, containerColor = Color(0xFF252526), contentColor = Color(0xFF007ACC)) {
            listOf("XML Input", "Compose Output", "Warnings").forEachIndexed { i, t ->
                Tab(vm.tab == i, { vm.tab = i }, text = {
                    Text(t, fontSize = 11.sp, color = if (vm.tab == i) Color(0xFF007ACC) else Color.Gray)
                })
            }
        }

        when (vm.tab) {
            0 -> {
                OutlinedTextField(
                    value = vm.xmlInput,
                    onValueChange = { vm.xmlInput = it },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    label = { Text("Android XML Layout", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                )
            }
            1 -> {
                val code = vm.result?.code ?: "Press Convert to generate Compose code."
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(code, color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(code)) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray)
                    }
                }
            }
            2 -> {
                val result = vm.result
                if (result == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Convert first.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (result.unmapped.isNotEmpty()) {
                            item { SectionLabel("Unmapped Views (${result.unmapped.size})") }
                            items(result.unmapped) { u ->
                                MsgRow(u, Color(0xFFFF6B6B), "❌")
                            }
                        }
                        if (result.warnings.isNotEmpty()) {
                            item { SectionLabel("Warnings (${result.warnings.size})") }
                            items(result.warnings) { w ->
                                MsgRow(w, Color(0xFFFF9800), "⚠")
                            }
                        }
                        if (result.warnings.isEmpty() && result.unmapped.isEmpty()) {
                            item {
                                Surface(color = Color(0xFF1A3A1A), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("✓", color = Color(0xFF4CAF50), fontSize = 16.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("No warnings — clean conversion!", color = Color(0xFF4CAF50), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                        item {
                            Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Conversion Notes", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(6.dp))
                                    listOf(
                                        "ConstraintLayout → Box (manual constraints needed)",
                                        "RecyclerView/ListView → LazyColumn (add data model)",
                                        "WebView → AndroidView (requires ViewInterop)",
                                        "Custom views → AndroidView or custom Composables",
                                        "Animations → Compose animation APIs"
                                    ).forEach { note ->
                                        MsgRow(note, Color.Gray, "•")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun MsgRow(msg: String, color: Color, icon: String) {
    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Text(icon, color = color, fontSize = 11.sp, modifier = Modifier.width(18.dp))
            Text(msg, color = color.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}
