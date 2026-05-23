package com.androidstudiomobile.utils
import java.io.File
import java.text.DecimalFormat
fun File.humanSize(): String {
    val bytes = length()
    if (bytes < 1024) return "$bytes B"
    val df = DecimalFormat("0.#")
    return when {
        bytes < 1_048_576L    -> "${df.format(bytes/1024.0)} KB"
        bytes < 1_073_741_824L -> "${df.format(bytes/1_048_576.0)} MB"
        else                   -> "${df.format(bytes/1_073_741_824.0)} GB"
    }
}
fun String.languageFromPath(): String = when (substringAfterLast(".","").lowercase()) {
    "kt","kts" -> "kotlin"; "java" -> "java"; "xml" -> "xml"; "json" -> "json"
    "gradle"   -> "groovy"; "md"   -> "markdown"; "sh" -> "shell"
    "py"       -> "python"; "js"   -> "javascript"; "ts" -> "typescript"
    "html"     -> "html";   "css"  -> "css"; "pro" -> "proguard"
    else       -> "plaintext"
}
fun Long.toHumanDuration(): String {
    val s = this / 1000; return when { s < 60 -> "${s}s"; s < 3600 -> "${s/60}m ${s%60}s"; else -> "${s/3600}h ${(s%3600)/60}m" }
}