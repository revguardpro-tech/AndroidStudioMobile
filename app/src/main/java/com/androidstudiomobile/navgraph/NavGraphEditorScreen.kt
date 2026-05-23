package com.androidstudiomobile.navgraph

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// NavGraphEditorScreen.kt
//
// Tela interativa que exibe destinos do nav graph como nós arrastáveis,
// conectados por setas com cabeças.
// Modo "Conectar": toque em origem → destino → cria <action>.
// Adiciona destinos via diálogo. Salva XML a cada mudança.
// ─────────────────────────────────────────────────────────────────────────────

// ── ViewModel ─────────────────────────────────────────────────────────────────

class NavGraphViewModel : ViewModel() {

    private val _graph      = MutableStateFlow<NavGraphParser.NavGraph?>(null)
    val graph: StateFlow<NavGraphParser.NavGraph?> = _graph

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    private var file: File? = null

    fun load(path: String) {
        file = File(path)
        if (!file!!.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            val g = NavGraphParser.parse(file!!)
            if (g != null) NavGraphParser.computeLayout(g)
            _graph.value = g
        }
    }

    fun move(id: String, dx: Float, dy: Float) {
        val g = _graph.value ?: return
        g.destinations.find { it.id == id }?.let { d ->
            d.x = (d.x + dx).coerceIn(10f, 750f)
            d.y = (d.y + dy).coerceIn(10f, 510f)
        }
        _graph.value = g.copy()
    }

    fun select(id: String?) { _selectedId.value = id }

    fun addDest(name: String, type: NavGraphParser.DestType) {
        val g = _graph.value ?: NavGraphParser.NavGraph("nav_graph", "").also { _graph.value = it }
        NavGraphParser.addDestination(g, name, type)
        if (g.startDestination.isEmpty() && g.destinations.isNotEmpty())
            _graph.value = g.copy(startDestination = g.destinations.first().id)
        else _graph.value = g.copy()
        save()
    }

    fun addAction(fromId: String, toId: String) {
        val g   = _graph.value ?: return
        val src = g.destinations.find { it.id == fromId } ?: return
        val aid = "action_${fromId}_to_$toId"
        if (src.actions.none { it.id == aid }) {
            src.actions.add(NavGraphParser.NavAction(aid, toId))
            _graph.value = g.copy()
            save()
        }
    }

    fun remove(id: String) {
        val g = _graph.value ?: return
        g.destinations.removeAll { it.id == id }
        g.destinations.forEach { d -> d.actions.removeAll { it.destination == id } }
        if (_selectedId.value == id) _selectedId.value = null
        _graph.value = g.copy()
        save()
    }

    private fun save() {
        val g = _graph.value ?: return
        val f = file ?: return
        viewModelScope.launch(Dispatchers.IO) { f.writeText(NavGraphParser.toXml(g)) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraphEditorScreen(
    filePath: String,
    navController: NavController,
    viewModel: NavGraphViewModel = remember { NavGraphViewModel() }
) {
    val graph       by viewModel.graph.collectAsState()
    val selectedId  by viewModel.selectedId.collectAsState()
    var addDialog   by remember { mutableStateOf(false) }
    var connectMode by remember { mutableStateOf(false) }
    var connectFrom by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) { viewModel.load(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Graph Editor") },
                navigationIcon = { IconButton({ navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton({ connectMode = !connectMode; connectFrom = null }) {
                        Icon(Icons.Default.Share, null,
                            tint = if (connectMode) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton({ addDialog = true }) { Icon(Icons.Default.Add, null) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {

            if (graph == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhum nav graph carregado")
                    Text("Abra um arquivo nav_graph.xml", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button({ addDialog = true }) { Text("Novo destino") }
                }
            } else {
                graph?.let { g ->
                    GraphCanvas(g, selectedId, connectMode, connectFrom,
                        onNodeTap = { id ->
                            when {
                                connectMode && connectFrom == null -> connectFrom = id
                                connectMode && connectFrom != null && connectFrom != id -> {
                                    viewModel.addAction(connectFrom!!, id)
                                    connectFrom = null; connectMode = false
                                }
                                else -> viewModel.select(id)
                            }
                        },
                        onMove = { id, dx, dy -> viewModel.move(id, dx, dy) }
                    )
                }

                // Hint bar em modo connect
                if (connectMode) {
                    Surface(Modifier.align(Alignment.TopCenter).padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(if (connectFrom == null) "Toque na ORIGEM da ação"
                             else "Toque no DESTINO da ação",
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                }

                // Painel do nó selecionado
                selectedId?.let { sid ->
                    graph?.destinations?.find { it.id == sid }?.let { dest ->
                        DestPanel(dest, dest.id == graph?.startDestination,
                            onDelete = { viewModel.remove(sid) },
                            modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    if (addDialog) {
        AddDestDialog(
            onDismiss = { addDialog = false },
            onAdd = { name, type -> viewModel.addDest(name, type); addDialog = false }
        )
    }
}

// ── Graph canvas ──────────────────────────────────────────────────────────────

@Composable
private fun GraphCanvas(
    graph: NavGraphParser.NavGraph,
    selectedId: String?,
    connectMode: Boolean,
    connectFrom: String?,
    onNodeTap: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val nodeW = 140.dp; val nodeH = 58.dp
    val nodeWPx = with(density) { nodeW.toPx() }
    val nodeHPx = with(density) { nodeH.toPx() }

    Box(Modifier.fillMaxSize().background(Color(0xFF121224))) {

        // Arrows
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            graph.destinations.forEach { src ->
                src.actions.forEach { act ->
                    val dst = graph.destinations.find { it.id == act.destination } ?: return@forEach
                    arrow(
                        Offset(src.x.dp.toPx() + nodeWPx / 2, src.y.dp.toPx() + nodeHPx / 2),
                        Offset(dst.x.dp.toPx() + nodeWPx / 2, dst.y.dp.toPx() + nodeHPx / 2),
                        Color(0xFF64B5F6)
                    )
                }
            }
        }

        // Nodes
        graph.destinations.forEach { dest ->
            val sel  = dest.id == selectedId
            val cfrom = dest.id == connectFrom
            val start = dest.id == graph.startDestination

            Box(
                Modifier
                    .offset(dest.x.dp, dest.y.dp)
                    .width(nodeW).height(nodeH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(nodeColor(dest.type, start).copy(alpha = if (sel || cfrom) 1f else 0.88f))
                    .border(
                        if (sel || cfrom) 2.dp else 0.dp,
                        if (cfrom) Color.Yellow else Color.White,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onNodeTap(dest.id) }
                    .pointerInput(dest.id) {
                        detectDragGestures { c, amt ->
                            c.consume()
                            with(density) { onMove(dest.id, amt.x.toDp().value, amt.y.toDp().value) }
                        }
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (start) Icon(Icons.Default.Home, null, Modifier.size(12.dp), tint = Color.White)
                        Text(dest.name.take(18), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(dest.type.name.lowercase(), color = Color.White.copy(0.6f), fontSize = 9.sp)
                    if (dest.actions.isNotEmpty())
                        Text("→ ${dest.actions.size}", fontSize = 8.sp, color = Color.White.copy(0.5f))
                }
            }
        }
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color) {
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = Math.sqrt((dx*dx+dy*dy).toDouble()).toFloat().coerceAtLeast(1f)
    val ux = dx/len; val uy = dy/len
    val s = Offset(from.x + ux*72f, from.y + uy*72f)
    val e = Offset(to.x   - ux*72f, to.y   - uy*72f)
    drawLine(color, s, e, strokeWidth = 1.8f)
    val ang = Math.atan2(dy.toDouble(), dx.toDouble()); val al = 10f; val aa = 0.4f
    drawPath(Path().apply {
        moveTo(e.x, e.y)
        lineTo((e.x - al*Math.cos(ang-aa)).toFloat(), (e.y - al*Math.sin(ang-aa)).toFloat())
        lineTo((e.x - al*Math.cos(ang+aa)).toFloat(), (e.y - al*Math.sin(ang+aa)).toFloat())
        close()
    }, color)
}

@Composable
private fun DestPanel(
    dest: NavGraphParser.NavDestination,
    isStart: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), tonalElevation = 8.dp) {
        Row(Modifier.padding(12.dp), Alignment.CenterVertically, Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isStart) Icon(Icons.Default.Home, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(dest.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("${dest.type.name} · id: ${dest.id}", fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dest.arguments.isNotEmpty())
                    Text("Args: ${dest.arguments.joinToString { "${it.name}:${it.type}" }}", fontSize = 10.sp)
            }
            IconButton(onDelete) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350)) }
        }
    }
}

@Composable
private fun AddDestDialog(
    onDismiss: () -> Unit,
    onAdd: (String, NavGraphParser.DestType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(NavGraphParser.DestType.FRAGMENT) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Novo Destino", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                listOf(NavGraphParser.DestType.FRAGMENT, NavGraphParser.DestType.ACTIVITY,
                    NavGraphParser.DestType.DIALOG).forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { type = t }, Alignment.CenterVertically) {
                        RadioButton(type == t, { type = t })
                        Text(t.name, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button({ if (name.isNotBlank()) onAdd(name.trim(), type) }, enabled = name.isNotBlank()) {
                        Text("Adicionar")
                    }
                }
            }
        }
    }
}

private fun nodeColor(type: NavGraphParser.DestType, isStart: Boolean): Color = when {
    isStart                            -> Color(0xFF1565C0)
    type == NavGraphParser.DestType.FRAGMENT   -> Color(0xFF2E7D32)
    type == NavGraphParser.DestType.ACTIVITY   -> Color(0xFFE65100)
    type == NavGraphParser.DestType.DIALOG     -> Color(0xFF6A1B9A)
    else                               -> Color(0xFF37474F)
}
