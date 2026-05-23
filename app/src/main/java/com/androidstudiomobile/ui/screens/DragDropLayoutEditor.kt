package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// DragDropLayoutEditor.kt
//
// Editor visual de layouts Android com:
//  • Paleta lateral (WidgetPalette) com 10 widgets
//  • Canvas drag-drop usando detectDragGestures do Compose
//  • Geração em tempo real do XML FrameLayout correspondente
//  • Painel de propriedades do widget selecionado
//  • Toggle XML / Visual
// ─────────────────────────────────────────────────────────────────────────────

// ── modelo ────────────────────────────────────────────────────────────────────

data class WidgetSpec(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val label: String,
    val icon: ImageVector,
    val defaultXml: String
)

data class PlacedWidget(
    val id: String = UUID.randomUUID().toString(),
    val spec: WidgetSpec,
    var xDp: Float   = 16f,
    var yDp: Float   = 16f,
    var widthDp: Float  = 160f,
    var heightDp: Float = 48f,
    var text: String = spec.label
)

object WidgetCatalog {
    val BUTTON   = widget("Button",   "Button",          Icons.Default.SmartButton,
        """<Button android:id="@+id/btn" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Button"/>""")
    val TEXT     = widget("TextView", "TextView",        Icons.Default.TextFields,
        """<TextView android:id="@+id/tv" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Text"/>""")
    val IMAGE    = widget("ImageView","ImageView",       Icons.Default.Image,
        """<ImageView android:id="@+id/img" android:layout_width="80dp" android:layout_height="80dp"/>""")
    val EDIT     = widget("EditText", "EditText",        Icons.Default.Edit,
        """<EditText android:id="@+id/et" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Enter text"/>""")
    val CHECK    = widget("CheckBox", "CheckBox",        Icons.Default.CheckBox,
        """<CheckBox android:id="@+id/cb" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Check"/>""")
    val SWITCH   = widget("Switch",   "Switch",          Icons.Default.ToggleOn,
        """<Switch android:id="@+id/sw" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Switch"/>""")
    val PROGRESS = widget("ProgressBar","Progress",      Icons.Default.Downloading,
        """<ProgressBar android:id="@+id/pb" android:layout_width="match_parent" android:layout_height="wrap_content" style="?progressBarStyleHorizontal" android:progress="50"/>""")
    val RECYCLER = widget("RecyclerView","RecyclerView", Icons.Default.ViewList,
        """<androidx.recyclerview.widget.RecyclerView android:id="@+id/rv" android:layout_width="match_parent" android:layout_height="match_parent"/>""")
    val FAB      = widget("FloatingActionButton","FAB",  Icons.Default.Add,
        """<com.google.android.material.floatingactionbutton.FloatingActionButton android:id="@+id/fab" android:layout_width="wrap_content" android:layout_height="wrap_content"/>""")
    val CARD     = widget("MaterialCardView","CardView", Icons.Default.CreditCard,
        """<com.google.android.material.card.MaterialCardView android:id="@+id/card" android:layout_width="match_parent" android:layout_height="wrap_content" app:cardCornerRadius="8dp"/>""")

    val all = listOf(BUTTON, TEXT, IMAGE, EDIT, CHECK, SWITCH, PROGRESS, RECYCLER, FAB, CARD)

    private fun widget(type: String, label: String, icon: ImageVector, xml: String) =
        WidgetSpec(type = type, label = label, icon = icon, defaultXml = xml)
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DragDropLayoutViewModel : ViewModel() {

    private val _widgets    = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val widgets: StateFlow<List<PlacedWidget>> = _widgets

    private val _xml        = MutableStateFlow("")
    val xml: StateFlow<String> = _xml

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    fun addWidget(spec: WidgetSpec, xDp: Float, yDp: Float) {
        val w = PlacedWidget(spec = spec, xDp = xDp.coerceAtLeast(0f), yDp = yDp.coerceAtLeast(0f))
        _widgets.value = _widgets.value + w
        _selectedId.value = w.id
        rebuild()
    }

    fun move(id: String, dx: Float, dy: Float) {
        _widgets.value = _widgets.value.map { w ->
            if (w.id == id) w.copy(
                xDp = (w.xDp + dx).coerceAtLeast(0f),
                yDp = (w.yDp + dy).coerceAtLeast(0f)
            ) else w
        }
        rebuild()
    }

    fun select(id: String?) { _selectedId.value = id }

    fun remove(id: String) {
        _widgets.value = _widgets.value.filter { it.id != id }
        if (_selectedId.value == id) _selectedId.value = null
        rebuild()
    }

    fun updateText(id: String, text: String) {
        _widgets.value = _widgets.value.map { if (it.id == id) it.copy(text = text) else it }
        rebuild()
    }

    fun saveToFile(path: String) = viewModelScope.launch { File(path).writeText(_xml.value) }

    private fun rebuild() {
        _xml.value = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<FrameLayout""")
            appendLine("""    xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    xmlns:app="http://schemas.android.com/apk/res-auto"""")
            appendLine("""    android:layout_width="match_parent"""")
            appendLine("""    android:layout_height="match_parent">""")
            _widgets.value.forEach { w ->
                appendLine("""    <${w.spec.type}""")
                appendLine("""        android:id="@+id/w_${w.id.take(6)}"""")
                appendLine("""        android:layout_width="${w.widthDp.toInt()}dp"""")
                appendLine("""        android:layout_height="${w.heightDp.toInt()}dp"""")
                appendLine("""        android:layout_marginStart="${w.xDp.toInt()}dp"""")
                appendLine("""        android:layout_marginTop="${w.yDp.toInt()}dp"""")
                if (w.spec.type in listOf("Button","TextView","EditText","CheckBox","Switch"))
                    appendLine("""        android:text="${w.text}"""")
                appendLine("""    />""")
            }
            appendLine("</FrameLayout>")
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DragDropLayoutEditorScreen(
    outputXmlPath: String = "",
    viewModel: DragDropLayoutViewModel = remember { DragDropLayoutViewModel() }
) {
    val widgets    by viewModel.widgets.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val xml        by viewModel.xml.collectAsState()
    var showXml    by remember { mutableStateOf(false) }
    var dragSpec   by remember { mutableStateOf<WidgetSpec?>(null) }
    val density    = LocalDensity.current

    Row(Modifier.fillMaxSize()) {

        // ── Paleta ───────────────────────────────────────────────────────────
        WidgetPalette(Modifier.width(80.dp).fillMaxHeight()) { dragSpec = it }

        VerticalDivider()

        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopAppBar(
                title = { Text("Layout Editor", fontSize = 14.sp) },
                actions = {
                    IconButton(onClick = { showXml = !showXml }) {
                        Icon(if (showXml) Icons.Default.Widgets else Icons.Default.Code, null)
                    }
                    if (outputXmlPath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.saveToFile(outputXmlPath) }) {
                            Icon(Icons.Default.Save, null)
                        }
                    }
                }
            )

            if (showXml) {
                Text(xml, Modifier.weight(1f).fillMaxWidth()
                    .background(Color(0xFF1E1E1E)).padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFD4D4D4))
            } else {
                // ── Canvas drop target ──────────────────────────────────────
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(Color(0xFF1E1E2E))
                        .pointerInput(dragSpec) {
                            if (dragSpec == null) return@pointerInput
                            detectDragGestures(
                                onDragEnd   = {},
                                onDragCancel = { dragSpec = null }
                            ) { change, _ ->
                                with(density) {
                                    viewModel.addWidget(
                                        dragSpec!!,
                                        change.position.x.toDp().value - 80f,
                                        change.position.y.toDp().value - 24f
                                    )
                                }
                                dragSpec = null
                            }
                        }
                        .clickable { viewModel.select(null) }
                ) {
                    // Grid
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val step = 16.dp.toPx(); val gc = Color(0xFF2A2A3A)
                        var x = 0f; while (x < size.width) { drawLine(gc, Offset(x,0f), Offset(x,size.height), 0.5f); x += step }
                        var y = 0f; while (y < size.height) { drawLine(gc, Offset(0f,y), Offset(size.width,y), 0.5f); y += step }
                    }

                    widgets.forEach { w ->
                        CanvasWidget(w, w.id == selectedId,
                            onSelect = { viewModel.select(w.id) },
                            onMove   = { dx, dy -> viewModel.move(w.id, dx, dy) },
                            onRemove = { viewModel.remove(w.id) }
                        )
                    }

                    if (widgets.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DragIndicator, null, Modifier.size(48.dp), tint = Color(0xFF444466))
                            Text("Arraste widgets da paleta aqui", color = Color(0xFF555577), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Propriedades
            selectedId?.let { id -> widgets.find { it.id == id }?.let { w ->
                PropertiesPanel(w) { viewModel.updateText(id, it) }
            }}
        }
    }
}

@Composable
private fun CanvasWidget(
    w: PlacedWidget, isSelected: Boolean,
    onSelect: () -> Unit, onMove: (Float, Float) -> Unit, onRemove: () -> Unit
) {
    val density = LocalDensity.current
    Box(
        Modifier.offset(w.xDp.dp, w.yDp.dp).width(w.widthDp.dp).height(w.heightDp.dp)
            .border(if (isSelected) 2.dp else 1.dp,
                if (isSelected) Color(0xFF2196F3) else Color(0xFF444466), RoundedCornerShape(4.dp))
            .background(Color(0xFF252535).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .clickable { onSelect() }
            .pointerInput(Unit) {
                detectDragGestures { c, amt ->
                    c.consume()
                    with(density) { onMove(amt.x.toDp().value, amt.y.toDp().value) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(w.spec.icon, null, Modifier.size(16.dp), tint = Color(0xFF90CAF9))
            Text(w.text.take(14), color = Color.White, fontSize = 10.sp)
        }
        if (isSelected) {
            IconButton(onRemove, Modifier.align(Alignment.TopEnd).size(20.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = Color(0xFFEF5350))
            }
        }
    }
}

@Composable
private fun PropertiesPanel(w: PlacedWidget, onTextChange: (String) -> Unit) {
    var text by remember(w.id) { mutableStateOf(w.text) }
    Surface(tonalElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            Alignment.CenterVertically, Arrangement.spacedBy(8.dp)) {
            Icon(w.spec.icon, null, Modifier.size(20.dp))
            Text(w.spec.type, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(90.dp))
            if (w.spec.type in listOf("Button","TextView","EditText","CheckBox","Switch")) {
                OutlinedTextField(text, { text = it; onTextChange(it) },
                    label = { Text("Texto") }, modifier = Modifier.weight(1f),
                    singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            } else Spacer(Modifier.weight(1f))
            Text("(${w.xDp.toInt()},${w.yDp.toInt()})", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun WidgetPalette(modifier: Modifier = Modifier, onDragStart: (WidgetSpec) -> Unit) {
    LazyColumn(modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("Widgets", fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(WidgetCatalog.all) { spec ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(spec) { detectDragGestures(onDragStart = { onDragStart(spec) }) { _, _ -> } }
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(spec.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(spec.label.take(10), fontSize = 8.sp)
            }
        }
    }
}
