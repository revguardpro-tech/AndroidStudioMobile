package com.androidstudiomobile.devicefarm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

class DeviceFarmViewModel : ViewModel() {
    private lateinit var mgr: VirtualDeviceManager
    val devices get() = mgr.devices
    var showDialog   by mutableStateOf(false)
    var newName      by mutableStateOf("Device")
    var newApi       by mutableStateOf("33")
    var newW         by mutableStateOf("1080")
    var newH         by mutableStateOf("2340")
    var newContainer by mutableStateOf(ContainerType.BLACKBOX)
    var apkPath      by mutableStateOf("")
    var status       by mutableStateOf("Ready")
    var isCapturing  by mutableStateOf(false)
    var isInstalling by mutableStateOf(false)
    var view         by mutableStateOf(0)
    val containers   get() = mgr.detectContainers()

    fun init(ctx: android.content.Context) { mgr = VirtualDeviceManager(ctx) }

    fun create() {
        mgr.add(DeviceProfile(name = newName, apiLevel = newApi.toIntOrNull() ?: 33,
            width = newW.toIntOrNull() ?: 1080, height = newH.toIntOrNull() ?: 2340,
            container = newContainer))
        showDialog = false
    }

    fun start(id: String)  = mgr.start(id)
    fun stop(id: String)   = mgr.stop(id)
    fun remove(id: String) = mgr.remove(id)

    fun captureAll() = viewModelScope.launch {
        isCapturing = true; status = "Capturing all…"
        mgr.screenshotAll(); status = "Screenshots done"; isCapturing = false
    }
    fun capture(id: String)  = viewModelScope.launch { mgr.screenshot(id) }
    fun installAll() = viewModelScope.launch {
        if (apkPath.isBlank()) { status = "Set APK path first"; return@launch }
        isInstalling = true
        val r = mgr.installAll(apkPath)
        status = "Installed on ${r.count { it.second }}/${r.size} devices"
        isInstalling = false
    }
    fun getLogs(id: String) = viewModelScope.launch { mgr.logs(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFarmScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: DeviceFarmViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init(ctx) }
    val devices by vm.devices.collectAsState(initial = emptyList())

    if (vm.showDialog) NewDeviceDialog(vm)

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("Device Farm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${devices.count { it.isRunning }}/${devices.size} running", color = Color.Gray, fontSize = 11.sp)
                    IconButton({ vm.showDialog = true }, enabled = devices.size < VirtualDeviceManager.MAX) {
                        Icon(Icons.Default.AddCircle, null, tint = if (devices.size < VirtualDeviceManager.MAX) Color(0xFF4CAF50) else Color.Gray)
                    }
                }
                OutlinedTextField(vm.apkPath, { vm.apkPath = it }, Modifier.fillMaxWidth(),
                    label = { Text("APK / AAB path", color = Color.Gray, fontSize = 10.sp) }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FarmBtn("Install All", Color(0xFF007ACC), vm.isInstalling) { vm.installAll() }
                    FarmBtn("📷 All", Color(0xFFFF9800), vm.isCapturing) { vm.captureAll() }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(5.dp))) {
                        IconButton({ vm.view = 0 }, Modifier.size(32.dp)) { Icon(Icons.Default.GridView, null, tint = if (vm.view == 0) Color(0xFF007ACC) else Color.Gray, modifier = Modifier.size(15.dp)) }
                        IconButton({ vm.view = 1 }, Modifier.size(32.dp)) { Icon(Icons.Default.List, null, tint = if (vm.view == 1) Color(0xFF007ACC) else Color.Gray, modifier = Modifier.size(15.dp)) }
                    }
                }
                Text(vm.status, color = Color.Gray, fontSize = 10.sp)
            }
        }

        LazyRow(Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.containers) { (t, ok) ->
                Surface(color = if (ok) Color(0xFF1A3A1A) else Color(0xFF2D2D2D), shape = RoundedCornerShape(5.dp)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if (ok) Color(0xFF4CAF50) else Color.Gray, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(t.label, color = if (ok) Color(0xFF4CAF50) else Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }

        when (vm.view) {
            0 -> if (devices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No devices — tap + to add (max ${VirtualDeviceManager.MAX})", color = Color.Gray)
                    }
                }
            } else {
                LazyRow(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices) { d -> DeviceCard(d, vm) }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { d ->
                    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (d.isRunning) Color(0xFF4CAF50) else Color.Gray, RoundedCornerShape(4.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(d.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                TextButton({ vm.getLogs(d.id) }) { Text("Refresh", color = Color(0xFF007ACC), fontSize = 10.sp) }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (d.lastLog.isNotBlank()) {
                                Surface(color = Color(0xFF0D0D0D), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(d.lastLog, color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                                }
                            } else {
                                Text("Start device and click Refresh to see logs", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(d: DeviceProfile, vm: DeviceFarmViewModel) {
    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(10.dp),
        modifier = Modifier.width(230.dp).fillMaxHeight().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF2D2D2D)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (d.isRunning) Color(0xFF4CAF50) else Color.Gray, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("API ${d.apiLevel} · ${d.container.label}", color = Color.Gray, fontSize = 9.sp)
                }
                IconButton({ vm.remove(d.id) }, Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF0D0D0D)), contentAlignment = Alignment.Center) {
                if (d.screenshot != null) {
                    Image(d.screenshot.asImageBitmap(), "${d.name} screenshot",
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF3C3C3C), modifier = Modifier.size(36.dp))
                        Text("${d.width}×${d.height}", color = Color(0xFF3C3C3C), fontSize = 10.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (d.isRunning) FarmBtn("Stop", Color(0xFFFF5252), false, Modifier.weight(1f)) { vm.stop(d.id) }
                else             FarmBtn("Start", Color(0xFF4CAF50), false, Modifier.weight(1f)) { vm.start(d.id) }
                FarmBtn("📷", Color(0xFFFF9800), false, Modifier.weight(1f)) { vm.capture(d.id) }
            }
        }
    }
}

@Composable
fun FarmBtn(label: String, color: Color, loading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier, enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f), contentColor = color),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(11.dp), color = color, strokeWidth = 1.5.dp)
        else Text(label, fontSize = 10.sp, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeviceDialog(vm: DeviceFarmViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showDialog = false },
        containerColor = Color(0xFF2D2D2D),
        title = { Text("New Virtual Device", color = Color.White) },
        text = {
            val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(vm.newName, { vm.newName = it }, Modifier.fillMaxWidth(), label = { Text("Name", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = colors)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedTextField(vm.newApi, { vm.newApi = it }, Modifier.weight(1f), label = { Text("API", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = colors)
                    OutlinedTextField(vm.newW,   { vm.newW   = it }, Modifier.weight(1f), label = { Text("W px", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = colors)
                    OutlinedTextField(vm.newH,   { vm.newH   = it }, Modifier.weight(1f), label = { Text("H px", color = Color.Gray, fontSize = 10.sp) }, singleLine = true, colors = colors)
                }
                Text("Container app:", color = Color.Gray, fontSize = 11.sp)
                ContainerType.values().forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(vm.newContainer == t, { vm.newContainer = t },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF007ACC)))
                        Text(t.label, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button({ vm.create() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Create") }
        },
        dismissButton = { TextButton({ vm.showDialog = false }) { Text("Cancel", color = Color.Gray) } }
    )
}
