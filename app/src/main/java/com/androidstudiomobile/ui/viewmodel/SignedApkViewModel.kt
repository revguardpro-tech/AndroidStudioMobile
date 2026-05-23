package com.androidstudiomobile.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.apksign.ApkSignEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SignWizardStep { KEYSTORE, APK_SELECT, SIGN, DONE }

data class SignedApkState(
    val step: SignWizardStep = SignWizardStep.KEYSTORE,
    val keystoreName: String = "my-release-key",
    val keystoreAlias: String = "mykey",
    val keystorePassword: String = "",
    val keyPassword: String = "",
    val ownerName: String = "CN=Android Studio Mobile,O=ASM,C=BR",
    val validityYears: Int = 25,
    val apkPath: String = "",
    val signedApkPath: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val buildLogs: List<String> = emptyList(),
    val existingKeystores: List<File> = emptyList(),
    val selectedKeystore: File? = null,
    val generatedKeystorePath: String = "",
    val signingMethod: String = "Detectando..."
)

class SignedApkViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SignedApkState())
    val state: StateFlow<SignedApkState> = _state.asStateFlow()

    fun init(context: Context, projectPath: String) {
        val keystores = ApkSignEngine.listSavedKeystores(context)
        val defaultApk = File(projectPath).walkTopDown()
            .filter { it.extension == "apk" && !it.name.contains("signed") }
            .firstOrNull()?.absolutePath ?: ""
        _state.update { it.copy(existingKeystores = keystores, apkPath = defaultApk) }
        detectSigningMethod(context)
    }

    private fun detectSigningMethod(context: Context) {
        viewModelScope.launch {
            val sdkHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: ""
            val method = when {
                sdkHome.isNotBlank() && File(sdkHome, "build-tools").exists() -> "apksigner (SDK)"
                File("/data/data/com.termux/files/usr/bin/apksigner").exists() -> "apksigner (Termux)"
                File("/data/data/com.termux/files/usr/bin/jarsigner").exists() -> "jarsigner (Termux)"
                else -> "jarsigner (JDK embutido)"
            }
            _state.update { it.copy(signingMethod = method) }
        }
    }

    fun setKeystoreName(v: String)     = _state.update { it.copy(keystoreName = v, errorMessage = null) }
    fun setKeystoreAlias(v: String)    = _state.update { it.copy(keystoreAlias = v, errorMessage = null) }
    fun setKeystorePassword(v: String) = _state.update { it.copy(keystorePassword = v, errorMessage = null) }
    fun setOwnerName(v: String)        = _state.update { it.copy(ownerName = v) }
    fun setValidityYears(v: Int)       = _state.update { it.copy(validityYears = v) }
    fun selectApk(path: String)        = _state.update { it.copy(apkPath = path, errorMessage = null) }

    fun selectExistingKeystore(file: File) =
        _state.update { it.copy(selectedKeystore = file, generatedKeystorePath = file.absolutePath) }

    fun proceedToApkSelect() = _state.update { it.copy(step = SignWizardStep.APK_SELECT, errorMessage = null) }
    fun proceedToSign()      = _state.update { it.copy(step = SignWizardStep.SIGN, errorMessage = null) }

    fun goBack() {
        _state.update { s ->
            val prev = when (s.step) {
                SignWizardStep.APK_SELECT -> SignWizardStep.KEYSTORE
                SignWizardStep.SIGN       -> SignWizardStep.APK_SELECT
                else                     -> s.step
            }
            s.copy(step = prev, errorMessage = null)
        }
    }

    fun generateKeystore() {
        val s = _state.value
        if (s.keystorePassword.length < 6) {
            _state.update { it.copy(errorMessage = "A senha deve ter pelo menos 6 caracteres") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val ksDir = File(context.filesDir, "keystores").also { it.mkdirs() }
            val ksPath = File(ksDir, "${s.keystoreName}.p12").absolutePath
            val config = ApkSignEngine.KeystoreConfig(
                alias = s.keystoreAlias,
                password = s.keystorePassword,
                keystorePath = ksPath,
                validity = s.validityYears,
                dname = s.ownerName.let { if (it.startsWith("CN=")) it else "CN=$it,O=ASM,C=BR" }
            )
            val result = ApkSignEngine.generateKeystore(context, config)
            if (result.isSuccess) {
                _state.update { it.copy(
                    isLoading = false,
                    generatedKeystorePath = result.getOrThrow(),
                    step = SignWizardStep.APK_SELECT
                ) }
            } else {
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = "Erro ao gerar keystore: ${result.exceptionOrNull()?.message}"
                ) }
            }
        }
    }

    fun signApk(context: Context) {
        val s = _state.value
        if (s.apkPath.isBlank()) {
            _state.update { it.copy(errorMessage = "Selecione um APK") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, buildLogs = emptyList()) }
        viewModelScope.launch {
            val ksPath = s.selectedKeystore?.absolutePath ?: s.generatedKeystorePath
            val result = ApkSignEngine.signApk(
                context = context,
                unsignedApkPath = s.apkPath,
                keystorePath = ksPath,
                keystorePassword = s.keystorePassword,
                keyAlias = s.keystoreAlias,
                keyPassword = s.keystorePassword,
                outputPath = s.apkPath.replace(".apk", "-signed.apk").replace("-unsigned", "")
            )
            if (result.success) {
                _state.update { it.copy(
                    isLoading = false,
                    signedApkPath = result.signedApkPath,
                    buildLogs = result.logs,
                    step = SignWizardStep.DONE
                ) }
            } else {
                _state.update { it.copy(
                    isLoading = false,
                    buildLogs = result.logs,
                    errorMessage = result.error ?: "Falha ao assinar o APK"
                ) }
            }
        }
    }
}
