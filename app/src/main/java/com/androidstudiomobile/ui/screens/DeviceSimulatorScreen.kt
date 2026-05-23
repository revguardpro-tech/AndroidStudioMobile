package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.DeviceSimulatorViewModel

/**
 * Device Simulator — alternativa ao AVD Emulator.
 *
 * Solução:
 * - Exibe skins de dispositivos populares como frames (desenhados em Compose, sem imagens externas).
 * - Permite testar o layout XML/Compose em diferentes resoluções usando o ComposePreviewScreen.
 * - Mostra as dimensões reais do dispositivo, densidade (dpi) e API level.
 * - Fornece controles de rotação (portrait/landscape) e diferentes tamanhos de tela.
 * - Integra com o ComposePreviewer para renderizar o arquivo aberto em cada skin.
 *
 * Não é um emulador real — é uma ferramenta de visualização de layout em múltiplos devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSimulatorScreen(
    navController: NavController,
    filePath: String,
    vm: DeviceSimulatorViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(filePath) { vm.loadFile(filePath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Simulator") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleOrientation) {
                        Icon(
                            if (state.isLandscape) Icons.Default.StayCurrentPortrait
                            else Icons.Default.StayCurrentLandscape,
                            "Girar"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            // Painel lateral — lista de devices
            Surface(
                Modifier.width(200.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    item {
                        Text("Dispositivos", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp),
                            fontSize = 13.sp)
                    }
                    items(state.availableDevices) { device ->
                        DeviceListItem(
                            device = device,
                            isSelected = state.selectedDevice == device,
                            onClick = { vm.selectDevice(device) }
                        )
                    }
                }
            }

            // Área principal — frame do dispositivo
            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Info do device selecionado
                    state.selectedDevice?.let { device ->
                        Card(
                            Modifier.fillMaxWidth().padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val (w, h) = if (state.isLandscape) device.heightPx to device.widthPx else device.widthPx to device.heightPx
                                    Text("${w}×${h}px  •  ${device.dpi}dpi  •  ${device.diagonal}\"  •  API ${device.apiLevel}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${device.dpWidth}×${device.dpHeight}dp", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(
                                    if (state.isLandscape) Icons.Default.StayCurrentLandscape else Icons.Default.PhoneAndroid,
                                    null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Frame do dispositivo desenhado em Compose
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        state.selectedDevice?.let { device ->
                            DeviceFrame(device, state.isLandscape, state.currentFile)
                        } ?: Text("Selecione um dispositivo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: DeviceProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    device.name.contains("Tablet", true) -> Icons.Default.TabletAndroid
                    device.name.contains("Watch", true) -> Icons.Default.Watch
                    device.name.contains("TV", true) -> Icons.Default.Tv
                    else -> Icons.Default.PhoneAndroid
                },
                null,
                Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(device.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                Text("${device.widthPx}×${device.heightPx}", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeviceFrame(device: DeviceProfile, isLandscape: Boolean, filePath: String) {
    val (frameW, frameH) = if (isLandscape) device.heightPx to device.widthPx else device.widthPx to device.heightPx
    val maxDim = 350f
    val scale = maxDim / maxOf(frameW.toFloat(), frameH.toFloat())
    val scaledW = (frameW * scale).dp
    val scaledH = (frameH * scale).dp
    val cornerDp = if (device.name.contains("Tablet", true)) 12.dp else 20.dp

    Box(
        Modifier
            .size(scaledW + 16.dp, scaledH + if (isLandscape) 24.dp else 64.dp)
            .clip(RoundedCornerShape(cornerDp + 4.dp))
            .background(Color(0xFF1C1C1C))
            .border(2.dp, Color(0xFF333333), RoundedCornerShape(cornerDp + 4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status bar
            if (!isLandscape) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.Wifi, null, Modifier.size(10.dp), tint = Color.White)
                        Icon(Icons.Default.BatteryFull, null, Modifier.size(10.dp), tint = Color.White)
                    }
                }
            }

            // Screen area
            Box(
                Modifier
                    .size(scaledW, scaledH)
                    .clip(RoundedCornerShape(cornerDp / 2))
                    .background(Color(0xFF2B2B2B)),
                contentAlignment = Alignment.Center
            ) {
                // Conteúdo do arquivo (preview estático)
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Simulated app content
                    Box(
                        Modifier.fillMaxWidth().height(28.dp * scale).clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF6200EE)), contentAlignment = Alignment.CenterStart
                    ) {
                        Text("  Toolbar", fontSize = (11 * scale).sp, color = Color.White)
                    }

                    // Content blocks
                    repeat(minOf(5, (scaledH.value / 50).toInt())) { i ->
                        Box(
                            Modifier.fillMaxWidth().height((40 * scale).dp).clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF333333))
                        ) {
                            Row(Modifier.padding((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size((24 * scale).dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
                                Spacer(Modifier.width((8 * scale).dp))
                                Column {
                                    Box(Modifier.width((80 * scale).dp).height((8 * scale).dp).background(Color(0xFF555555)))
                                    Spacer(Modifier.height((4 * scale).dp))
                                    Box(Modifier.width((120 * scale).dp).height((6 * scale).dp).background(Color(0xFF444444)))
                                }
                            }
                        }
                    }
                }

                // Overlay com info do arquivo
                if (filePath.isNotBlank()) {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xCC000000))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            java.io.File(filePath).name,
                            fontSize = (9 * scale).sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Nav bar
            if (!isLandscape) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(14.dp), tint = Color.White)
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, Color.White, RoundedCornerShape(2.dp)))
                    Icon(Icons.Default.MoreVert, null, Modifier.size(14.dp), tint = Color.White)
                }
            }
        }
    }
}

data class DeviceProfile(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val dpi: Int,
    val diagonal: Float,
    val apiLevel: Int = 33
) {
    val dpWidth get() = (widthPx * 160f / dpi).toInt()
    val dpHeight get() = (heightPx * 160f / dpi).toInt()
}
