package com.androidstudiomobile

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.androidstudiomobile.ui.navigation.AppNavigation
import com.androidstudiomobile.ui.theme.AndroidStudioMobileTheme

/**
 * MainActivity com suporte a alternância de orientação.
 *
 * Funcionalidade de orientação:
 * - Por padrão, o app usa landscapeMode (paisagem) para melhor aproveitamento da tela
 *   em tablets e celulares ao usar o editor de código.
 * - O botão de rotação (FAB) permite ao usuário alternar entre retrato e paisagem a qualquer momento.
 * - A preferência de orientação é persistida no SharedPreferences.
 * - O configChanges no AndroidManifest evita que o Activity seja recriado ao girar.
 */
class MainActivity : ComponentActivity() {

    private val PREF_NAME = "asm_prefs"
    private val PREF_ORIENTATION = "orientation_landscape"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restaurar preferência de orientação
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val isLandscape = prefs.getBoolean(PREF_ORIENTATION, true) // Padrão: landscape
        applyOrientation(isLandscape)

        enableEdgeToEdge()
        setContent {
            AndroidStudioMobileTheme {
                val ctx = LocalContext.current
                var landscape by remember { mutableStateOf(isLandscape) }

                Box(Modifier.fillMaxSize()) {
                    AppNavigation()

                    // FAB de rotação — sempre visível no canto inferior direito
                    FloatingActionButton(
                        onClick = {
                            landscape = !landscape
                            applyOrientation(landscape)
                            // Persistir preferência
                            (ctx as? MainActivity)?.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                                ?.edit()
                                ?.putBoolean(PREF_ORIENTATION, landscape)
                                ?.apply()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.ScreenRotation, "Girar tela")
                    }
                }
            }
        }
    }

    private fun applyOrientation(landscape: Boolean) {
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}
