package com.androidstudiomobile.preview

import android.os.FileObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

// ── ViewModel ────────────────────────────────────────────────────────────────

class PreviewPaneViewModel : ViewModel() {

    private val _previews = MutableStateFlow<List<ComposePreviewRenderer.PreviewResult>>(emptyList())
    val previews: StateFlow<List<ComposePreviewRenderer.PreviewResult>> = _previews

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentFile = MutableStateFlow<String>("")
    val currentFile: StateFlow<String> = _currentFile

    private var fileObserver: FileObserver? = null

    fun loadFile(path: String) {
        _currentFile.value = path
        parseAndUpdate(path)
        watchFile(path)
    }

    private fun parseAndUpdate(path: String) {
        val file = File(path)
        if (!file.exists()) return
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { ComposePreviewRenderer.parseFile(file) }
            _previews.value = result.getOrElse { emptyList() }
            _isLoading.value = false
        }
    }

    /** FileObserver detecta save (CLOSE_WRITE) e re-parseia em <100ms */
    @Suppress("DEPRECATION")
    private fun watchFile(path: String) {
        fileObserver?.stopWatching()
        fileObserver = object : FileObserver(path, CLOSE_WRITE) {
            override fun onEvent(event: Int, p: String?) {
                parseAndUpdate(path)
            }
        }
        fileObserver?.startWatching()
    }

    fun refresh() { parseAndUpdate(_currentFile.value) }

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }
}

// ── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewPane(
    filePath: String,
    viewModel: PreviewPaneViewModel = remember { PreviewPaneViewModel() }
) {
    var darkMode by remember { mutableStateOf(false) }
    val previews by viewModel.previews.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(filePath) { viewModel.loadFile(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Preview", fontSize = 14.sp) },
                actions = {
                    IconButton(onClick = { darkMode = !darkMode }) {
                        Icon(
                            if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                previews.isEmpty() -> Text(
                    "Nenhum @Preview encontrado no arquivo.",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyRow(
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(previews) { result ->
                        PreviewCard(result, darkMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(result: ComposePreviewRenderer.PreviewResult, darkMode: Boolean) {
    val bgColor = if (darkMode) Color(0xFF121212) else Color(0xFFFAFAFA)
    val fgColor = if (darkMode) Color(0xFFEEEEEE) else Color(0xFF212121)
    val accentColor = if (darkMode) Color(0xFF90CAF9) else Color(0xFF1976D2)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            result.meta.name,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (result.meta.group.isNotEmpty()) {
            Text(result.meta.group, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }

        Box(
            Modifier
                .width(result.meta.widthDp.coerceIn(200, 420).dp * 0.6f)
                .height(result.meta.heightDp.coerceIn(300, 800).dp * 0.6f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(bgColor)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                result.tree.forEach { node ->
                    SemanticNodeRenderer(node, fgColor, accentColor, darkMode)
                }
            }
        }

        if (result.warnings.isNotEmpty()) {
            result.warnings.forEach { w ->
                Text("⚠ $w", fontSize = 9.sp, color = Color(0xFFFFA000))
            }
        }
    }
}

@Composable
private fun SemanticNodeRenderer(
    node: ComposePreviewRenderer.SemanticNode,
    fg: Color,
    accent: Color,
    dark: Boolean
) {
    when (node) {
        is ComposePreviewRenderer.SemanticNode.TextNode -> {
            val (size, weight) = when (node.style) {
                "headline" -> 18.sp to FontWeight.Bold
                "label" -> 11.sp to FontWeight.Medium
                else -> 13.sp to FontWeight.Normal
            }
            Text(node.text.ifEmpty { "Text" }, color = fg, fontSize = size, fontWeight = weight)
        }
        is ComposePreviewRenderer.SemanticNode.ButtonNode -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(node.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        is ComposePreviewRenderer.SemanticNode.TextFieldNode -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, if (dark) Color(0xFF555555) else Color(0xFFBBBBBB), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(node.label, color = fg.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        is ComposePreviewRenderer.SemanticNode.CardNode -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.children.forEach { SemanticNodeRenderer(it, fg, accent, dark) }
                }
            }
        }
        is ComposePreviewRenderer.SemanticNode.SpacerNode ->
            Spacer(Modifier.height(node.dpHeight.dp * 0.5f))
        is ComposePreviewRenderer.SemanticNode.SwitchNode ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(node.label, color = fg, fontSize = 12.sp)
                Box(Modifier.width(36.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(accent))
            }
        is ComposePreviewRenderer.SemanticNode.SliderNode ->
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        is ComposePreviewRenderer.SemanticNode.ChipNode ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, accent, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(node.label, color = accent, fontSize = 11.sp) }
        is ComposePreviewRenderer.SemanticNode.CheckboxNode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).border(2.dp, accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(node.label, color = fg, fontSize = 12.sp)
            }
        is ComposePreviewRenderer.SemanticNode.ImageNode ->
            Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(4.dp)).background(
                if (dark) Color(0xFF333333) else Color(0xFFDDDDDD)), contentAlignment = Alignment.Center) {
                Text("🖼 ${node.contentDesc}", color = fg.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        is ComposePreviewRenderer.SemanticNode.RowNode ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children.forEach { SemanticNodeRenderer(it, fg, accent, dark) }
            }
        is ComposePreviewRenderer.SemanticNode.ColumnNode ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children.forEach { SemanticNodeRenderer(it, fg, accent, dark) }
            }
        is ComposePreviewRenderer.SemanticNode.UnknownNode ->
            Text("[ ${node.composable} ]", color = fg.copy(alpha = 0.4f), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
    }
}
