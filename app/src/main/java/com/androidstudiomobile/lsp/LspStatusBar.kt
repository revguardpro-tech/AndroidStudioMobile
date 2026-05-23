package com.androidstudiomobile.lsp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidstudiomobile.lint.LintIssue
import com.androidstudiomobile.lint.LintSeverity

@Composable
fun LspStatusBar(
    lspStatus: LspManager.LspStatus,
    issues: List<LintIssue>,
    isAnalyzing: Boolean,
    cursorLine: Int,
    cursorCol: Int,
    language: String,
    buildVariant: String,
    onErrorClick: () -> Unit,
    onLspClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errors   = issues.count { it.severity == LintSeverity.ERROR }
    val warnings = issues.count { it.severity == LintSeverity.WARNING }

    Row(modifier = modifier.fillMaxWidth().height(24.dp)
        .background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {

        // KLS status dot
        Row(Modifier.clickable(onClick = onLspClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(7.dp).background(color = when (lspStatus) {
                LspManager.LspStatus.RUNNING  -> Color(0xFF4CAF50)
                LspManager.LspStatus.STARTING -> Color(0xFFFFC107)
                LspManager.LspStatus.ERROR    -> Color(0xFFFF5252)
                LspManager.LspStatus.STOPPED  -> Color(0xFF757575)
            }, shape = CircleShape))
            Text(when (lspStatus) {
                LspManager.LspStatus.RUNNING  -> "KLS ✓"
                LspManager.LspStatus.STARTING -> "KLS…"
                LspManager.LspStatus.ERROR    -> "KLS ✗"
                LspManager.LspStatus.STOPPED  -> "KLS"
            }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isAnalyzing) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)

        // Issue counts
        Row(Modifier.clickable(onClick = onErrorClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (errors > 0) {
                Icon(Icons.Default.Error, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                Text("$errors", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
            if (warnings > 0) {
                Icon(Icons.Default.Warning, null, Modifier.size(12.dp), tint = Color(0xFFFFC107))
                Text("$warnings", fontSize = 10.sp, color = Color(0xFFFFC107))
            }
            if (errors == 0 && warnings == 0 && !isAnalyzing) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(12.dp), tint = Color(0xFF4CAF50))
            }
        }

        Spacer(Modifier.weight(1f))
        Text(language, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ln $cursorLine:$cursorCol", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(buildVariant, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
    }
}
