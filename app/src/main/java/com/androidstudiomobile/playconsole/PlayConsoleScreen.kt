package com.androidstudiomobile.playconsole

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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

class PlayConsoleViewModel : ViewModel() {
    private lateinit var api: PlayConsoleIntegration
    private val _tracks = MutableStateFlow<List<PlayTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    var pkg         by mutableStateOf("")
    var clientId    by mutableStateOf("")
    var clientSecret by mutableStateOf("")
    var apkPath     by mutableStateOf("")
    var track       by mutableStateOf("internal")
    var notes       by mutableStateOf("")
    var rollout     by mutableStateOf("100")
    var oauthCode   by mutableStateOf("")
    var status      by mutableStateOf("Not authenticated")
    var isLoading   by mutableStateOf(false)
    var isAuth      by mutableStateOf(false)
    var tab         by mutableStateOf(0)
    var editId      by mutableStateOf("")

    fun init(ctx: android.content.Context) {
        api = PlayConsoleIntegration(ctx)
        isAuth = api.loadTokens()
        if (isAuth) { status = "Authenticated"; api.clientId = api.clientId; api.clientSecret = api.clientSecret }
    }

    fun authUrl(): String { api.clientId = clientId; api.clientSecret = clientSecret; return api.authUrl() }

    fun exchange() = viewModelScope.launch {
        isLoading = true; status = "Exchanging code…"
        isAuth = api.exchangeCode(oauthCode)
        status = if (isAuth) "Authenticated ✓" else "Auth failed"; isLoading = false
    }

    fun loadTracks() = viewModelScope.launch {
        if (pkg.isBlank()) return@launch; isLoading = true; status = "Loading tracks…"
        try { val e = api.createEdit(pkg); editId = e.id; _tracks.value = api.listTracks(pkg, e.id); status = "${_tracks.value.size} tracks loaded" }
        catch (e: Exception) { status = "Error: ${e.message}" }; isLoading = false
    }

    fun upload() = viewModelScope.launch {
        if (pkg.isBlank() || apkPath.isBlank()) { status = "Set package & file"; return@launch }
        isLoading = true; status = "Creating edit…"
        try {
            val e = api.createEdit(pkg); editId = e.id
            status = "Uploading…"
            val f = java.io.File(apkPath)
            val vc = if (apkPath.endsWith(".aab")) api.uploadAab(pkg, e.id, f) else api.uploadApk(pkg, e.id, f)
            status = "Setting track $track…"
            val frac = rollout.toDoubleOrNull()?.div(100.0)
            val ns = if (notes.isNotBlank()) listOf("en-US" to notes) else emptyList()
            api.updateTrack(pkg, e.id, track, listOf(vc), "completed", frac, ns)
            api.commitEdit(pkg, e.id); status = "Published v$vc → $track ✓"; loadTracks()
        } catch (e: Exception) { status = "Failed: ${e.message}"; runCatching { api.deleteEdit(pkg, editId) } }
        isLoading = false
    }

    fun signOut() { api.clearTokens(); isAuth = false; status = "Signed out" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayConsoleScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: PlayConsoleViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init(ctx) }
    val tracks by vm.tracks.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Box(Modifier.size(24.dp).background(Color(0xFF4CAF50), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 13.sp) }
                    Spacer(Modifier.width(6.dp))
                    Text("Play Console", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    if (vm.isAuth) TextButton({ vm.signOut() }) { Text("Sign out", color = Color(0xFFFF5252), fontSize = 11.sp) }
                }
                if (vm.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF4CAF50), trackColor = Color(0xFF3C3C3C))
                Text(vm.status, color = Color.Gray, fontSize = 11.sp)
            }
        }

        TabRow(vm.tab, containerColor = Color(0xFF252526), contentColor = Color(0xFF4CAF50)) {
            listOf("Setup", "Upload", "Tracks").forEachIndexed { i, t -> Tab(vm.tab == i, { vm.tab = i }, text = { Text(t, fontSize = 11.sp, color = if (vm.tab == i) Color(0xFF4CAF50) else Color.Gray) }) }
        }

        when (vm.tab) {
            0 -> LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("OAuth 2.0 Credentials", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            PlayField("Client ID", vm.clientId) { vm.clientId = it }
                            Spacer(Modifier.height(6.dp))
                            PlayField("Client Secret", vm.clientSecret) { vm.clientSecret = it }
                            Spacer(Modifier.height(6.dp))
                            PlayField("Package name", vm.pkg) { vm.pkg = it }
                            Spacer(Modifier.height(10.dp))
                            if (!vm.isAuth) {
                                Button({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.authUrl()))) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Sign in with Google") }
                                Spacer(Modifier.height(6.dp))
                                PlayField("Paste auth code from browser", vm.oauthCode) { vm.oauthCode = it }
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton({ vm.exchange() }, Modifier.fillMaxWidth()) { Text("Exchange Code", color = Color(0xFF4CAF50)) }
                            } else {
                                Surface(color = Color(0xFF1A3A1A), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp)); Text("Ready to publish", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Upload APK / AAB", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            PlayField("File path (.apk or .aab)", vm.apkPath) { vm.apkPath = it }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("internal","alpha","beta","production").forEach { t ->
                                    FilterChip(vm.track == t, { vm.track = t }, label = { Text(t.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50), selectedLabelColor = Color.White, containerColor = Color(0xFF3C3C3C), labelColor = Color.Gray))
                                }
                            }
                            if (vm.track == "production") { Spacer(Modifier.height(6.dp)); PlayField("Rollout % (1-100)", vm.rollout) { vm.rollout = it } }
                            Spacer(Modifier.height(6.dp))
                            PlayField("Release notes (en-US)", vm.notes) { vm.notes = it }
                            Spacer(Modifier.height(10.dp))
                            Button({ vm.upload() }, Modifier.fillMaxWidth(), enabled = vm.isAuth && !vm.isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Upload & Publish") }
                        }
                    }
                }
            }
            2 -> Column(Modifier.fillMaxSize()) {
                Button({ vm.loadTracks() }, Modifier.padding(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Refresh") }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracks) { t ->
                        val color = when (t.name) { "production" -> Color(0xFF4CAF50); "beta" -> Color(0xFF2196F3); "alpha" -> Color(0xFFFF9800); else -> Color(0xFF9C27B0) }
                        Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) { Text(t.name.replaceFirstChar { it.uppercase() }, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Status: ${t.status}", color = Color.Gray, fontSize = 11.sp)
                                    if (t.versionCodes.isNotEmpty()) Text("v${t.versionCodes.joinToString()}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayField(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label, color = Color.Gray, fontSize = 11.sp) }, singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF4CAF50), unfocusedBorderColor = Color.Gray))
