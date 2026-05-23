package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.ThemeEditorViewModel

/**
 * Theme Editor visual — editor de temas com preview em tempo real.
 *
 * Solução:
 * - Parseia o arquivo colors.xml e themes.xml do projeto.
 * - Exibe um painel de cores editável com color picker inline.
 * - À direita, renderiza um "Painel de Widgets" que usa as cores selecionadas
 *   em tempo real — sem compilar nada, usando apenas Compose.
 * - Permite salvar as alterações de volta nos arquivos XML.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    navController: NavController,
    projectPath: String,
    vm: ThemeEditorViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(projectPath) { vm.loadTheme(projectPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Editor") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (state.isDirty) {
                        IconButton(onClick = { vm.saveTheme() }) {
                            Icon(Icons.Default.Save, "Salvar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { vm.loadTheme(projectPath) }) {
                        Icon(Icons.Default.Refresh, "Recarregar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Cores") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Preview") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Temas") })
            }

            when (selectedTab) {
                0 -> ColorEditorTab(vm, state)
                1 -> WidgetPreviewTab(state)
                2 -> ThemeListTab(vm, state)
            }
        }
    }
}

@Composable
private fun ColorEditorTab(vm: ThemeEditorViewModel, state: com.androidstudiomobile.ui.viewmodel.ThemeEditorState) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var showPicker by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (state.colors.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhum colors.xml encontrado")
                    Text("Certifique-se de abrir um projeto Android", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        // Snackbar de salvo
        if (state.savedMessage.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(state.savedMessage, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            items(state.colors) { colorEntry ->
                ColorEntryRow(
                    name = colorEntry.first,
                    hexValue = colorEntry.second,
                    onEdit = {
                        editingColor = colorEntry.first
                        showPicker = true
                    },
                    onValueChange = { vm.updateColor(colorEntry.first, it) }
                )
            }
        }

        // Color picker inline
        if (showPicker && editingColor != null) {
            val currentHex = state.colors.find { it.first == editingColor }?.second ?: "#000000"
            ColorPickerPanel(
                colorName = editingColor!!,
                currentHex = currentHex,
                onColorSelected = { hex ->
                    vm.updateColor(editingColor!!, hex)
                    showPicker = false
                    editingColor = null
                },
                onDismiss = { showPicker = false; editingColor = null }
            )
        }
    }
}

@Composable
private fun ColorEntryRow(
    name: String,
    hexValue: String,
    onEdit: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val color = remember(hexValue) { parseHexColor(hexValue) }
    var editMode by remember { mutableStateOf(false) }
    var textValue by remember(hexValue) { mutableStateOf(hexValue) }

    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Swatch de cor
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .border(1.dp, Color(0x22000000), RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
            ) {
                if (color.luminance() > 0.5f)
                    Icon(Icons.Default.Edit, null, Modifier.align(Alignment.Center).size(16.dp), tint = Color(0x88000000))
            }

            // Nome da cor
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                if (editMode) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        placeholder = { Text("#RRGGBB") },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { onValueChange(textValue); editMode = false }) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { textValue = hexValue; editMode = false }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                } else {
                    Text(hexValue, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                }
            }

            // Botão editar texto
            if (!editMode) {
                IconButton(onClick = { editMode = true }) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun ColorPickerPanel(
    colorName: String,
    currentHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#000000",
        "#FFFFFF", "#F5F5F5", "#212121", "#1976D2", "#388E3C",
        "#D32F2F", "#7B1FA2", "#C2185B", "#0097A7", "#F57C00",
        // Material 3 paleta
        "#6750A4", "#625B71", "#7D5260", "#EADDFF", "#E8DEF8",
        "#FFD8E4", "#1C1B1F", "#49454F", "#CAC4D0", "#E6E0EC"
    )

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Editar: $colorName", fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }

            Text("Cores rápidas:", style = MaterialTheme.typography.titleSmall)
            val chunked = presetColors.chunked(8)
            chunked.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { hex ->
                        val c = parseHexColor(hex)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (hex.equals(currentHex, ignoreCase = true)) 3.dp else 1.dp,
                                    if (hex.equals(currentHex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color(0x22000000),
                                    CircleShape
                                )
                                .clickable { onColorSelected(hex) }
                        )
                    }
                }
            }

            var manualHex by remember { mutableStateOf(currentHex) }
            OutlinedTextField(
                value = manualHex,
                onValueChange = { manualHex = it },
                label = { Text("Hex manual (#RRGGBB ou #AARRGGBB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Button(onClick = { onColorSelected(manualHex) }, Modifier.padding(end = 4.dp)) {
                        Text("OK", fontSize = 12.sp)
                    }
                }
            )

            if (manualHex.length >= 7) {
                val preview = parseHexColor(manualHex)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(preview)
                        .border(1.dp, Color(0x22000000), RoundedCornerShape(8.dp)))
                    Text("Preview: $manualHex", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewTab(state: com.androidstudiomobile.ui.viewmodel.ThemeEditorState) {
    val primary = remember(state.colors) { parseHexColor(state.colors.find { it.first == "colorPrimary" }?.second ?: "#6200EE") }
    val secondary = remember(state.colors) { parseHexColor(state.colors.find { it.first == "colorSecondary" }?.second ?: "#03DAC5") }
    val background = remember(state.colors) { parseHexColor(state.colors.find { it.first.contains("background", true) }?.second ?: "#FFFFFF") }
    val surface = remember(state.colors) { parseHexColor(state.colors.find { it.first.contains("surface", true) }?.second ?: "#FFFFFF") }
    val error = remember(state.colors) { parseHexColor(state.colors.find { it.first == "colorError" }?.second ?: "#B00020") }

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Preview ao Vivo", fontWeight = FontWeight.Bold,
                color = if (background.luminance() > 0.5f) Color.Black else Color.White)

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(primary).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("PRIMARY", color = if (primary.luminance() > 0.5f) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(secondary).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("SECONDARY", color = if (secondary.luminance() > 0.5f) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, primary, RoundedCornerShape(4.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("OUTLINED", color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Card
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(surface)
                .border(1.dp, Color(0x22000000), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MaterialCard", fontWeight = FontWeight.Bold,
                        color = if (surface.luminance() > 0.5f) Color.Black else Color.White)
                    Text("Subtítulo do card", fontSize = 12.sp,
                        color = (if (surface.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.6f))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(primary.copy(alpha = 0.3f)))
                    Text("Conteúdo do card com as cores do tema aplicadas dinamicamente.",
                        fontSize = 12.sp,
                        color = if (surface.luminance() > 0.5f) Color.Black else Color.White)
                }
            }

            // FAB
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(secondary), contentAlignment = Alignment.Center) {
                    Text("+", color = if (secondary.luminance() > 0.5f) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            // TextField simulado
            Box(Modifier.fillMaxWidth().border(2.dp, primary, RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text("Campo de texto (focused)", fontSize = 12.sp,
                    color = if (background.luminance() > 0.5f) Color.Black else Color.White)
            }

            // Chip
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.15f))
                    .border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Chip 1", fontSize = 11.sp, color = primary)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(secondary.copy(alpha = 0.15f))
                    .border(1.dp, secondary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Chip 2", fontSize = 11.sp, color = secondary)
                }
            }

            // Error state
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(error.copy(alpha = 0.1f))
                .border(1.dp, error, RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text("Estado de erro / colorError", fontSize = 12.sp, color = error)
            }

            // Progress
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(primary.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight().background(primary))
            }

            Text("Paleta atual:", fontWeight = FontWeight.Bold,
                color = if (background.luminance() > 0.5f) Color.Black else Color.White)
            state.colors.take(8).forEach { (name, hex) ->
                val c = parseHexColor(hex)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c).border(1.dp, Color(0x22000000), CircleShape))
                    Text("$name: $hex", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = if (background.luminance() > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
private fun ThemeListTab(vm: ThemeEditorViewModel, state: com.androidstudiomobile.ui.viewmodel.ThemeEditorState) {
    if (state.themes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Style, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Nenhum tema encontrado")
                Text("themes.xml não encontrado no projeto", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        items(state.themes) { theme ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(theme.name, fontWeight = FontWeight.Bold)
                    if (theme.parent.isNotBlank()) {
                        Text("extends: ${theme.parent}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (theme.attributes.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        theme.attributes.take(5).forEach { (k, v) ->
                            Text("  $k = $v", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (theme.attributes.size > 5) {
                            Text("  ... +${theme.attributes.size - 5} atributos", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            else -> Color.Gray
        }
    } catch (_: Exception) { Color.Gray }
}
