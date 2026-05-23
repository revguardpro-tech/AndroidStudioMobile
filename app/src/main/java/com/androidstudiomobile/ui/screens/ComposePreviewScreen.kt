package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.preview.ComposePreviewer
import com.androidstudiomobile.ui.viewmodel.ComposePreviewViewModel
import java.io.File

/**
 * Tela de Preview estático para Compose e XML layouts.
 *
 * Solução: renderização canvas-based que aproxima o layout real.
 * - Para @Preview: exibe metadados + simulação de dispositivo com frame.
 * - Para XML: desenha a árvore de views como boxes/text no Canvas.
 * - Para Compose (sem @Preview): mostra estrutura inferida do código.
 *
 * Não é execução real do código Compose, mas uma visualização suficiente
 * para validar o layout antes de compilar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePreviewScreen(
    navController: NavController,
    filePath: String,
    vm: ComposePreviewViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedPreviewIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(filePath) { vm.loadFile(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Layout Preview", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(File(filePath).name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadFile(filePath) }) { Icon(Icons.Default.Refresh, "Recarregar") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Analisando arquivo...")
                        }
                    }
                }
                state.previewType == ComposePreviewer.PreviewType.NONE -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Preview, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Nenhum preview detectado", style = MaterialTheme.typography.titleMedium)
                            Text("Adicione @Preview ao arquivo .kt ou abra um layout .xml",
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.previewType == ComposePreviewer.PreviewType.XML_LAYOUT -> {
                    XmlLayoutPreview(state.xmlRoot)
                }
                else -> {
                    // Compose preview(s)
                    if (state.previews.size > 1) {
                        LazyRow(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.previews.indices.toList()) { idx ->
                                FilterChip(
                                    selected = selectedPreviewIdx == idx,
                                    onClick = { selectedPreviewIdx = idx },
                                    label = { Text(state.previews[idx].name.ifBlank { state.previews[idx].functionName }, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    val preview = state.previews.getOrNull(selectedPreviewIdx)
                    if (preview != null) {
                        ComposePreviewCanvas(preview, state.functionBodies[preview.functionName] ?: "")
                    }
                }
            }
        }
    }
}

/** Renderiza um preview de Compose no Canvas com frame de dispositivo. */
@Composable
private fun ComposePreviewCanvas(
    preview: ComposePreviewer.PreviewAnnotation,
    body: String
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Informações do preview
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("@Preview: ${preview.functionName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${preview.widthDp}x${preview.heightDp} dp  •  ${preview.device}  •  API ${preview.apiLevel}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (preview.group.isNotBlank()) Text("Grupo: ${preview.group}", fontSize = 10.sp)
                }
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Frame do dispositivo com conteúdo simulado
        val scale = minOf(1f, 300f / preview.widthDp.toFloat())
        val scaledW = (preview.widthDp * scale).dp
        val scaledH = (preview.heightDp * scale).dp

        Box(
            Modifier
                .size(scaledW + 24.dp, scaledH + 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1C))
                .border(2.dp, Color(0xFF3C3C3C), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Barra de status do dispositivo
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("9:41", fontSize = 8.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(8.dp), tint = Color.White)
                    Icon(Icons.Default.Wifi, null, Modifier.size(8.dp), tint = Color.White)
                    Icon(Icons.Default.BatteryFull, null, Modifier.size(8.dp), tint = Color.White)
                }
            }

            // Área de conteúdo
            Box(
                Modifier
                    .size(scaledW, scaledH)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (preview.showBackground)
                            Color(preview.backgroundColor.toInt())
                        else Color(0xFF2B2B2B)
                    )
                    .align(Alignment.Center)
                    .offset(y = 14.dp)
            ) {
                SimulatedComposeContent(body, preview, scale)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Código da função (preview do fonte)
        if (body.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Fonte: @Composable ${preview.functionName}()", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        body.lines().take(20).joinToString("\n"),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (body.lines().size > 20) {
                        Text("... (${body.lines().size - 20} linhas)", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Renderiza conteúdo simulado baseado no corpo da função Compose. */
@Composable
private fun SimulatedComposeContent(body: String, preview: ComposePreviewer.PreviewAnnotation, scale: Float) {
    val elements = inferComposeElements(body)

    if (elements.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                preview.functionName,
                color = Color(0xFF606060),
                fontSize = (14 * scale).sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().padding((8 * scale).dp), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
        elements.take(12).forEach { elem ->
            SimulatedElement(elem, scale)
        }
    }
}

@Composable
private fun SimulatedElement(elem: ComposeElement, scale: Float) {
    val fontSize = (elem.fontSize * scale).sp
    when (elem.type) {
        "Text" -> {
            Box(
                Modifier
                    .fillMaxWidth(elem.widthFraction)
                    .height((elem.heightDp * scale).dp)
                    .background(Color(0x22888888), RoundedCornerShape(2.dp))
                    .padding(horizontal = (4 * scale).dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(elem.content.take(40), fontSize = fontSize, color = Color(0xFFD4D4D4), maxLines = 1)
            }
        }
        "Button" -> {
            Box(
                Modifier
                    .fillMaxWidth(elem.widthFraction)
                    .height((32 * scale).dp)
                    .background(Color(0xFF6200EE), RoundedCornerShape((4 * scale).dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(elem.content.take(20), fontSize = fontSize, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        "Image", "AsyncImage" -> {
            Box(
                Modifier
                    .fillMaxWidth(elem.widthFraction)
                    .height((80 * scale).dp)
                    .background(Color(0xFF333333), RoundedCornerShape((4 * scale).dp))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape((4 * scale).dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, null, Modifier.size((24 * scale).dp), tint = Color(0xFF666666))
            }
        }
        "Icon" -> {
            Box(
                Modifier.size((24 * scale).dp).background(Color(0x22888888), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, null, Modifier.size((16 * scale).dp), tint = Color(0xFF888888))
            }
        }
        "Card" -> {
            Box(
                Modifier
                    .fillMaxWidth(elem.widthFraction)
                    .height((elem.heightDp * scale).dp)
                    .background(Color(0xFF2D2D2D), RoundedCornerShape((8 * scale).dp))
                    .border(1.dp, Color(0xFF404040), RoundedCornerShape((8 * scale).dp))
            )
        }
        "Divider", "HorizontalDivider" -> {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF404040)))
        }
        "Row" -> {
            Box(
                Modifier.fillMaxWidth().height((elem.heightDp * scale).dp)
                    .background(Color(0x11FFFFFF), RoundedCornerShape((4 * scale).dp))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape((4 * scale).dp))
            )
        }
        "OutlinedTextField", "TextField" -> {
            Box(
                Modifier.fillMaxWidth(elem.widthFraction).height((48 * scale).dp)
                    .border(1.dp, Color(0xFF888888), RoundedCornerShape((4 * scale).dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(elem.content.take(30).ifBlank { "Campo de texto" },
                    Modifier.padding(horizontal = (8 * scale).dp),
                    fontSize = fontSize, color = Color(0xFF888888))
            }
        }
        else -> {
            Box(
                Modifier.fillMaxWidth(elem.widthFraction).height((elem.heightDp * scale).dp)
                    .background(Color(0x11FFFFFF), RoundedCornerShape((2 * scale).dp))
            )
        }
    }
}

data class ComposeElement(
    val type: String,
    val content: String = "",
    val widthFraction: Float = 1f,
    val heightDp: Float = 20f,
    val fontSize: Float = 12f
)

private fun inferComposeElements(body: String): List<ComposeElement> {
    val elements = mutableListOf<ComposeElement>()
    val lines = body.lines()

    lines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Text(") -> {
                val content = Regex("""Text\s*\(\s*["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1)
                    ?: Regex("""Text\s*\(\s*(\w+)""").find(trimmed)?.groupValues?.get(1) ?: "Text"
                val sizePx = Regex("""fontSize\s*=\s*(\d+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull() ?: 14f
                elements += ComposeElement("Text", content, 0.8f, sizePx * 1.5f, sizePx)
            }
            trimmed.startsWith("Button(") || trimmed.startsWith("ElevatedButton(") ||
            trimmed.startsWith("OutlinedButton(") || trimmed.startsWith("TextButton(") -> {
                val label = Regex("""Text\s*\(\s*["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: "Button"
                elements += ComposeElement("Button", label, 0.5f, 32f)
            }
            trimmed.startsWith("Image(") || trimmed.startsWith("AsyncImage(") -> {
                elements += ComposeElement("Image", "", 1f, 80f)
            }
            trimmed.startsWith("Icon(") -> {
                elements += ComposeElement("Icon", "", 0.1f, 24f)
            }
            trimmed.startsWith("Card(") || trimmed.startsWith("ElevatedCard(") -> {
                elements += ComposeElement("Card", "", 1f, 64f)
            }
            trimmed.startsWith("HorizontalDivider(") || trimmed.startsWith("Divider(") -> {
                elements += ComposeElement("Divider", "", 1f, 1f)
            }
            trimmed.startsWith("Row(") -> {
                elements += ComposeElement("Row", "", 1f, 48f)
            }
            trimmed.startsWith("OutlinedTextField(") || trimmed.startsWith("TextField(") -> {
                val label = Regex("""label\s*=\s*\{[^}]*Text\s*\(\s*["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                elements += ComposeElement("OutlinedTextField", label, 1f, 48f)
            }
            trimmed.startsWith("LinearProgressIndicator(") || trimmed.startsWith("CircularProgressIndicator(") -> {
                elements += ComposeElement("ProgressIndicator", "", 0.6f, 24f)
            }
            trimmed.startsWith("Spacer(") -> {
                val height = Regex("""height\s*=\s*(\d+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull() ?: 8f
                elements += ComposeElement("Spacer", "", 1f, height)
            }
            trimmed.startsWith("Switch(") || trimmed.startsWith("Checkbox(") -> {
                elements += ComposeElement("Toggle", "", 0.15f, 24f)
            }
        }
    }
    return elements
}

/** Renderiza o preview de XML layout. */
@Composable
private fun XmlLayoutPreview(root: ComposePreviewer.XmlViewNode?) {
    if (root == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Erro ao parsear o XML layout", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("XML Layout Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Representação estática offline do layout", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF888888), RoundedCornerShape(8.dp))
        ) {
            XmlNodeRenderer(root, depth = 0)
        }
    }
}

@Composable
private fun XmlNodeRenderer(node: ComposePreviewer.XmlViewNode, depth: Int) {
    val padding = (depth * 8).dp
    val bgColor = when (node.tag) {
        "LinearLayout", "FrameLayout", "RelativeLayout",
        "ConstraintLayout", "CoordinatorLayout" -> Color(0x08000000)
        "CardView", "MaterialCardView" -> Color(0x11000000)
        "RecyclerView" -> Color(0x0A000000)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = padding)
            .background(bgColor)
            .let {
                if (node.tag in listOf("LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout"))
                    it.border(1.dp, Color(0x22000000), RoundedCornerShape(4.dp))
                else it
            }
            .padding(4.dp)
    ) {
        Column {
            // Header do nó
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(nodeColor(node.tag), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${node.tag}${if (node.id.isNotBlank()) " (${node.id})" else ""}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = nodeColor(node.tag)
                )
                if (node.text.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(": \"${node.text}\"", fontSize = 10.sp, color = Color(0xFF666666))
                }
            }
            // Dimensões
            Row(Modifier.padding(start = 12.dp)) {
                Text("${node.widthSpec} × ${node.heightSpec}", fontSize = 9.sp, color = Color(0xFF999999))
            }
            // Filhos
            if (node.children.isNotEmpty() && depth < 6) {
                Spacer(Modifier.height(2.dp))
                node.children.forEach { child ->
                    XmlNodeRenderer(child, depth + 1)
                }
            } else if (node.children.isNotEmpty()) {
                Text("   (+ ${node.children.size} filhos)", fontSize = 9.sp, color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

private fun nodeColor(tag: String): Color = when {
    tag.endsWith("Layout") || tag == "ConstraintLayout" -> Color(0xFF6200EE)
    tag == "TextView" -> Color(0xFF007AFF)
    tag == "Button" || tag == "MaterialButton" -> Color(0xFF4CAF50)
    tag == "ImageView" || tag == "ShapeableImageView" -> Color(0xFFFF9800)
    tag == "RecyclerView" -> Color(0xFF009688)
    tag == "EditText" || tag == "TextInputLayout" -> Color(0xFFF44336)
    tag == "CardView" || tag == "MaterialCardView" -> Color(0xFF795548)
    else -> Color(0xFF607D8B)
}
