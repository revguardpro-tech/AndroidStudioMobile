package com.androidstudiomobile.lsp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoToDefinitionSheet(
    result: GoToResult,
    onNavigate: (filePath: String, line: Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (result.type == GoToType.DEFINITION) Icons.Default.MyLocation else Icons.Default.Search,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (result.type == GoToType.DEFINITION) "Go to Definition" else "Find Usages",
                        fontWeight = FontWeight.Bold)
                    Text("`${result.symbol}` — ${result.results.size} result(s)",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()
            if (result.results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Text("No definition found for `${result.symbol}`",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(result.results) { def ->
                        Card(Modifier.fillMaxWidth().clickable { onNavigate(def.filePath, def.line); onDismiss() },
                            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(when (def.kind) {
                                    DefinitionKind.CLASS, DefinitionKind.DATA_CLASS -> Icons.Default.DataObject
                                    DefinitionKind.INTERFACE -> Icons.Default.Lan
                                    DefinitionKind.OBJECT    -> Icons.Default.Category
                                    DefinitionKind.ENUM      -> Icons.Default.List
                                    DefinitionKind.FUN       -> Icons.Default.Functions
                                    DefinitionKind.VARIABLE  -> Icons.Default.Code
                                    else -> Icons.Default.InsertDriveFile
                                }, null, tint = when (def.kind) {
                                    DefinitionKind.CLASS, DefinitionKind.DATA_CLASS -> Color(0xFFFFC66D)
                                    DefinitionKind.INTERFACE -> Color(0xFFBBB529)
                                    DefinitionKind.FUN       -> Color(0xFFFFC66D)
                                    DefinitionKind.VARIABLE  -> Color(0xFF9876AA)
                                    else -> MaterialTheme.colorScheme.primary
                                }, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(def.fileName, fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("Ln ${def.line}", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(def.snippet, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    Text(def.filePath.substringAfter("/com/").substringBeforeLast("/").replace("/", "."),
                                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
