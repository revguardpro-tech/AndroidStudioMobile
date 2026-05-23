package com.androidstudiomobile.xmlcompose

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class ConversionResult(val code: String, val warnings: List<String>, val unmapped: List<String>)

class XmlToComposeConverter {

    private val warnings  = mutableListOf<String>()
    private val unmapped  = mutableListOf<String>()
    private val imports   = mutableSetOf<String>()

    fun convert(xml: String): ConversionResult {
        warnings.clear(); unmapped.clear(); imports.clear()
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
            doc.documentElement.normalize()
            val body = el(doc.documentElement, 0)
            val imp = buildImports()
            ConversionResult(buildString {
                appendLine(imp); appendLine()
                appendLine("@Composable"); appendLine("fun ConvertedLayout() {")
                appendLine(body.prependIndent("    ")); appendLine("}")
            }, warnings.toList(), unmapped.toList())
        } catch (e: Exception) { ConversionResult("// XML parse error: ${e.message}", listOf(e.message ?: ""), emptyList()) }
    }

    private fun el(e: Element, d: Int): String {
        val tag = e.tagName.substringAfterLast('.')
        return when (tag) {
            "LinearLayout"     -> layout(e, d, if (e.getAttribute("android:orientation") == "horizontal") { imports += "Row"; "Row" } else { imports += "Column"; "Column" })
            "RelativeLayout", "FrameLayout" -> { imports += "Box"; layout(e, d, "Box") }
            "ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout" -> { warnings += "ConstraintLayout → Box (constraints not preserved)"; imports += "Box"; layout(e, d, "Box") }
            "ScrollView"       -> { imports += "Column"; imports += "verticalScroll"; imports += "rememberScrollState"; "${ind(d)}Column(modifier = ${mod(e)}.verticalScroll(rememberScrollState())) {\n${children(e,d+1)}\n${ind(d)}}" }
            "HorizontalScrollView" -> { imports += "Row"; imports += "horizontalScroll"; imports += "rememberScrollState"; "${ind(d)}Row(modifier = ${mod(e)}.horizontalScroll(rememberScrollState())) {\n${children(e,d+1)}\n${ind(d)}}" }
            "CardView", "com.google.android.material.card.MaterialCardView", "androidx.cardview.widget.CardView" -> { imports += "Card"; "${ind(d)}Card(modifier = ${mod(e)}) {\n${children(e,d+1)}\n${ind(d)}}" }
            "TextView"         -> { imports += "Text"; "${ind(d)}Text(text = ${txt(e)}, modifier = ${mod(e)}${textStyle(e)})" }
            "EditText", "com.google.android.material.textfield.TextInputEditText" -> { imports += "OutlinedTextField"; "${ind(d)}OutlinedTextField(value = \"\", onValueChange = {}, modifier = ${mod(e)}${hint(e)})" }
            "com.google.android.material.textfield.TextInputLayout" -> { imports += "OutlinedTextField"; "${ind(d)}OutlinedTextField(value = \"\", onValueChange = {}, label = { Text(${txt(e)}) }, modifier = ${mod(e)})" }
            "Button", "com.google.android.material.button.MaterialButton" -> { imports += "Button"; "${ind(d)}Button(onClick = {}, modifier = ${mod(e)}) { Text(${txt(e)}) }" }
            "ImageView"        -> { imports += "Image"; imports += "painterResource"; val src = e.getAttribute("android:src").substringAfterLast('/').ifBlank { "placeholder" }; "${ind(d)}Image(painter = painterResource(R.drawable.$src), contentDescription = ${cd(e)}, modifier = ${mod(e)})" }
            "CheckBox"         -> { imports += "Checkbox"; "${ind(d)}Checkbox(checked = false, onCheckedChange = {}, modifier = ${mod(e)})" }
            "RadioButton"      -> { imports += "RadioButton"; "${ind(d)}RadioButton(selected = false, onClick = {})" }
            "Switch"           -> { imports += "Switch"; "${ind(d)}Switch(checked = false, onCheckedChange = {})" }
            "SeekBar"          -> { imports += "Slider"; "${ind(d)}Slider(value = 0f, onValueChange = {}, valueRange = 0f..${e.getAttribute("android:max").toFloatOrNull() ?: 100f}f)" }
            "ProgressBar"      -> if (e.getAttribute("style").contains("Horizontal", true)) { imports += "LinearProgressIndicator"; "${ind(d)}LinearProgressIndicator(progress = { 0f })" } else { imports += "CircularProgressIndicator"; "${ind(d)}CircularProgressIndicator()" }
            "RecyclerView", "androidx.recyclerview.widget.RecyclerView", "ListView" -> { imports += "LazyColumn"; imports += "items"; "${ind(d)}LazyColumn(modifier = ${mod(e)}) {\n${ind(d+1)}items(emptyList<Any>()) { /* TODO */ }\n${ind(d)}}" }
            "FloatingActionButton", "com.google.android.material.floatingactionbutton.FloatingActionButton" -> { imports += "FloatingActionButton"; "${ind(d)}FloatingActionButton(onClick = {}) { /* icon */ }" }
            "com.google.android.material.chip.Chip" -> { imports += "AssistChip"; "${ind(d)}AssistChip(onClick = {}, label = { Text(${txt(e)}) })" }
            "com.google.android.material.chip.ChipGroup" -> { imports += "FlowRow"; warnings += "ChipGroup → FlowRow (API 34+)"; "${ind(d)}FlowRow {\n${children(e,d+1)}\n${ind(d)}}" }
            "Space"            -> { imports += "Spacer"; val h = e.getAttribute("android:layout_height").filter { it.isDigit() }.toIntOrNull(); "${ind(d)}Spacer(modifier = ${if (h != null && h > 0) "Modifier.height(${h}.dp)" else "Modifier.weight(1f)"})" }
            "View"             -> { imports += "Divider"; "${ind(d)}Divider()" }
            "include"          -> { val l = e.getAttribute("layout").substringAfterLast('/').removeSuffix(".xml"); warnings += "include @layout/$l — extract to composable"; "${ind(d)}/* TODO: $l() */" }
            "merge"            -> children(e, d)
            "WebView"          -> { imports += "AndroidView"; warnings += "WebView → AndroidView"; "${ind(d)}AndroidView(factory = { ctx -> android.webkit.WebView(ctx) }, modifier = ${mod(e)})" }
            "TabLayout"        -> { imports += "TabRow"; "${ind(d)}TabRow(selectedTabIndex = 0) { /* TODO */ }" }
            "BottomNavigationView" -> { imports += "NavigationBar"; "${ind(d)}NavigationBar { /* TODO */ }" }
            else               -> { unmapped += tag; warnings += "Unknown view '$tag'"; "${ind(d)}Box(modifier = ${mod(e)}) { /* TODO: $tag */ }" }
        }
    }

    private fun layout(e: Element, d: Int, comp: String): String {
        val m = mod(e); val arr = arrange(e)
        return "${ind(d)}$comp(modifier = $m$arr) {\n${children(e,d+1)}\n${ind(d)}}"
    }

    private fun children(e: Element, d: Int): String {
        val sb = StringBuilder()
        val nodes = e.childNodes
        for (i in 0 until nodes.length) { val c = nodes.item(i) as? Element ?: continue; sb.appendLine(el(c, d)) }
        return sb.toString().trimEnd()
    }

    private fun mod(e: Element): String {
        val parts = mutableListOf("Modifier")
        val w = e.getAttribute("android:layout_width"); val h = e.getAttribute("android:layout_height")
        when (w) { "match_parent", "0dp" -> parts += "fillMaxWidth()"; "wrap_content" -> {}; else -> w.filter { it.isDigit() }.toIntOrNull()?.let { parts += "width(${it}.dp)" } }
        when (h) { "match_parent" -> parts += "fillMaxHeight()"; "wrap_content" -> {}; else -> h.filter { it.isDigit() }.toIntOrNull()?.let { parts += "height(${it}.dp)" } }
        val p = e.getAttribute("android:padding").filter { it.isDigit() }.toIntOrNull(); if (p != null) parts += "padding(${p}.dp)"
        val ph = e.getAttribute("android:paddingHorizontal").filter { it.isDigit() }.toIntOrNull(); if (ph != null) parts += "padding(horizontal = ${ph}.dp)"
        val pv = e.getAttribute("android:paddingVertical").filter { it.isDigit() }.toIntOrNull(); if (pv != null) parts += "padding(vertical = ${pv}.dp)"
        return parts.joinToString(".")
    }

    private fun arrange(e: Element): String {
        val g = e.getAttribute("android:gravity"); val parts = mutableListOf<String>()
        if (g.contains("center")) { parts += ", horizontalAlignment = Alignment.CenterHorizontally"; parts += ", verticalArrangement = Arrangement.Center" }
        return parts.joinToString()
    }

    private fun txt(e: Element): String { val t = e.getAttribute("android:text").ifBlank { "" }; return if (t.startsWith("\"")) t else "\"$t\"" }
    private fun hint(e: Element): String { val h = e.getAttribute("android:hint").ifBlank { "" }; return if (h.isNotBlank()) """, placeholder = { Text("$h") }""" else "" }
    private fun cd(e: Element): String { val c = e.getAttribute("android:contentDescription"); return if (c.isBlank()) "null" else "\"$c\"" }
    private fun textStyle(e: Element): String { val sz = e.getAttribute("android:textSize").filter { it.isDigit() }.toIntOrNull(); return if (sz != null) ", fontSize = ${sz}.sp" else "" }
    private fun ind(d: Int) = "    ".repeat(d)

    private fun buildImports() = setOf(
        "androidx.compose.foundation.layout.*",
        "androidx.compose.material3.*",
        "androidx.compose.runtime.Composable",
        "androidx.compose.ui.Alignment",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.unit.dp",
        "androidx.compose.ui.unit.sp"
    ).joinToString("\n") { "import $it" }
}
