package com.androidstudiomobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.androidstudiomobile.ui.viewmodel.SignedApkViewModel
import com.androidstudiomobile.ui.viewmodel.SignWizardStep
import java.io.File

/**
 * Signed APK Wizard — wizard completo para gerar keystores e assinar APKs.
 *
 * Solução: interface Compose em 3 etapas:
 *  Etapa 1 — Configurar ou criar keystore (geração via java.security, sem keytool externo)
 *  Etapa 2 — Selecionar APK não assinado e configurar assinatura
 *  Etapa 3 — Executar assinatura (apksigner ou jarsigner) e exibir resultado
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignedApkWizardScreen(
    navController: NavController,
    projectPath: String,
    vm: SignedApkViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(projectPath) { vm.init(ctx, projectPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signed APK Wizard") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Indicador de etapas ────────────────────────────────────────────
            StepIndicator(currentStep = state.step)

            HorizontalDivider()

            when (state.step) {
                SignWizardStep.KEYSTORE -> KeystoreStep(vm, state)
                SignWizardStep.APK_SELECT -> ApkSelectStep(vm, state, projectPath)
                SignWizardStep.SIGN -> SignStep(vm, state, ctx)
                SignWizardStep.DONE -> DoneStep(state, navController)
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: SignWizardStep) {
    val steps = listOf("1. Keystore", "2. APK", "3. Assinar", "4. Pronto")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        steps.forEachIndexed { idx, label ->
            val active = idx == currentStep.ordinal
            val done = idx < currentStep.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(
                            when {
                                done -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    else Text("${idx + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(label.substringAfter(". "), fontSize = 10.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KeystoreStep(vm: SignedApkViewModel, state: com.androidstudiomobile.ui.viewmodel.SignedApkState) {
    var useExisting by remember { mutableStateOf(state.existingKeystores.isNotEmpty()) }
    var showPassword by remember { mutableStateOf(false) }

    Text("Configurar Keystore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    if (state.existingKeystores.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useExisting, onCheckedChange = { useExisting = it })
            Spacer(Modifier.width(8.dp))
            Text("Usar keystore existente")
        }
    }

    AnimatedVisibility(visible = !useExisting) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Criar novo keystore", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = state.keystoreName,
                onValueChange = vm::setKeystoreName,
                label = { Text("Nome do keystore") },
                placeholder = { Text("my-release-key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, null) }
            )
            OutlinedTextField(
                value = state.keystoreAlias,
                onValueChange = vm::setKeystoreAlias,
                label = { Text("Alias da chave") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.keystorePassword,
                onValueChange = vm::setKeystorePassword,
                label = { Text("Senha do keystore (min. 6 chars)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
            )
            OutlinedTextField(
                value = state.ownerName,
                onValueChange = vm::setOwnerName,
                label = { Text("Nome/Organização") },
                placeholder = { Text("CN=MeuApp,O=MinhEmpresa,C=BR") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.validityYears.toString(),
                onValueChange = { vm.setValidityYears(it.toIntOrNull() ?: 25) },
                label = { Text("Validade (anos)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }

    AnimatedVisibility(visible = useExisting && state.existingKeystores.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Selecionar keystore:", style = MaterialTheme.typography.titleSmall)
            state.existingKeystores.forEach { ks ->
                Card(
                    onClick = { vm.selectExistingKeystore(ks) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.selectedKeystore == ks)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(ks.name, fontWeight = FontWeight.Medium)
                            Text(ks.absolutePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.keystorePassword,
                onValueChange = vm::setKeystorePassword,
                label = { Text("Senha do keystore") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }
    }

    state.errorMessage?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(it, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            }
        }
    }

    Button(
        onClick = {
            if (useExisting) vm.proceedToApkSelect()
            else vm.generateKeystore()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (useExisting) "Próximo" else "Gerar Keystore e Próximo")
    }
}

@Composable
private fun ApkSelectStep(vm: SignedApkViewModel, state: com.androidstudiomobile.ui.viewmodel.SignedApkState, projectPath: String) {
    Text("Selecionar APK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    val apks = remember(projectPath) {
        File(projectPath).walkTopDown()
            .filter { it.extension == "apk" && !it.name.contains("signed") }
            .take(20).toList()
    }

    if (apks.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary)
                Text("Nenhum APK encontrado. Faça o build do projeto primeiro (assembleRelease).")
            }
        }
    } else {
        Text("APKs encontrados:", style = MaterialTheme.typography.titleSmall)
        apks.forEach { apk ->
            Card(
                onClick = { vm.selectApk(apk.absolutePath) },
                colors = CardDefaults.cardColors(
                    containerColor = if (state.apkPath == apk.absolutePath)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, tint = Color(0xFF78C257))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(apk.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${apk.absolutePath.replace(projectPath, ".")} (${apk.length() / 1024} KB)",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (state.apkPath == apk.absolutePath)
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    OutlinedTextField(
        value = state.apkPath,
        onValueChange = vm::selectApk,
        label = { Text("Ou cole o caminho do APK") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::goBack, modifier = Modifier.weight(1f)) { Text("Voltar") }
        Button(
            onClick = vm::proceedToSign,
            modifier = Modifier.weight(1f),
            enabled = state.apkPath.isNotBlank()
        ) { Text("Próximo") }
    }
}

@Composable
private fun SignStep(vm: SignedApkViewModel, state: com.androidstudiomobile.ui.viewmodel.SignedApkState, ctx: android.content.Context) {
    Text("Assinar APK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("APK:", File(state.apkPath).name)
            InfoRow("Keystore:", state.selectedKeystore?.name ?: state.keystoreName)
            InfoRow("Alias:", state.keystoreAlias)
            InfoRow("Método:", state.signingMethod)
        }
    }

    if (state.isLoading) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Assinando APK...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (state.buildLogs.isNotEmpty()) {
        Card {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(8.dp)) {
                items(state.buildLogs) { log ->
                    Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = if (log.contains("erro", true) || log.contains("failed", true))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    state.errorMessage?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::goBack, modifier = Modifier.weight(1f), enabled = !state.isLoading) { Text("Voltar") }
        Button(
            onClick = { vm.signApk(ctx) },
            modifier = Modifier.weight(1f),
            enabled = !state.isLoading
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Assinar APK")
        }
    }
}

@Composable
private fun DoneStep(state: com.androidstudiomobile.ui.viewmodel.SignedApkState, navController: NavController) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = Color(0xFF4CAF50))
        Text("APK Assinado com Sucesso!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        state.signedApkPath?.let { path ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("APK assinado:", fontWeight = FontWeight.Bold)
                    Text(path, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Tamanho: ${File(path).length() / 1024} KB",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Concluir")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(80.dp), fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
