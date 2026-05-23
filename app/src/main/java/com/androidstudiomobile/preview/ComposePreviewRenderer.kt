package com.androidstudiomobile.preview

import android.os.FileObserver
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// ComposePreviewRenderer.kt
//
// Estratégia de renderização em 3 camadas:
//  1. Parseia @Preview com regex → extrai metadados (widthDp, heightDp, etc.)
//  2. Analisa o corpo da função @Composable estaticamente → árvore SemanticNode
//  3. Essa árvore é renderizada em Compose puro (PreviewPane.kt) como widgets
//     reais — sem execução de bytecode, resultado em < 300 ms.
//
// FileObserver dispara re-parse automático ao detectar CLOSE_WRITE no arquivo.
// ─────────────────────────────────────────────────────────────────────────────

object ComposePreviewRenderer {

    // ── modelo ────────────────────────────────────────────────────────────────

    data class PreviewMeta(
        val name: String,
        val widthDp: Int    = 360,
        val heightDp: Int   = 640,
        val showBg: Boolean = true,
        val bgColor: Long   = 0xFFFFFFFF,
        val fontScale: Float = 1f,
        val group: String   = "",
        val device: String  = "",
        val uiMode: Int     = 0
    )

    sealed class SemanticNode {
        data class Txt(val text: String, val style: String = "body") : SemanticNode()
        data class Btn(val label: String, val variant: String = "filled") : SemanticNode()
        data class Img(val desc: String) : SemanticNode()
        data class Card(val children: List<SemanticNode>) : SemanticNode()
        data class Col(val children: List<SemanticNode>) : SemanticNode()
        data class Row(val children: List<SemanticNode>) : SemanticNode()
        data class Field(val label: String, val outlined: Boolean = true) : SemanticNode()
        data class Space(val dp: Int) : SemanticNode()
        data class Toggle(val label: String) : SemanticNode()
        data class Slide(val label: String) : SemanticNode()
        data class Check(val label: String) : SemanticNode()
        data class Chip(val label: String) : SemanticNode()
        data class Fab(val icon: String = "+") : SemanticNode()
        data class Unknown(val tag: String) : SemanticNode()
    }

    data class PreviewResult(
        val meta: PreviewMeta,
        val tree: List<SemanticNode>,
        val warnings: List<String> = emptyList()
    )

    // ── public API ────────────────────────────────────────────────────────────

    fun parseFile(file: File): List<PreviewResult> = parseSource(file.readText())

    fun parseSource(src: String): List<PreviewResult> {
        val results = mutableListOf<PreviewResult>()

        // Captura @Preview(...) seguido de @Composable fun Name()
        val re = Regex(
            """@Preview\s*(\([^)]*\))?\s*(?:@\w+\s*)*fun\s+(\w+)\s*\([^)]*\)\s*\{""",
            RegexOption.DOT_MATCHES_ALL
        )
        re.findAll(src).forEach { m ->
            val meta = parseMeta(m.groupValues[2], m.groupValues[1])
            val body = extractBody(src, m.range.last)
            results += PreviewResult(meta, parseBody(body))
        }

        // Fallback: qualquer @Composable fun
        if (results.isEmpty()) {
            Regex("""@Composable\s+fun\s+(\w+)\s*\(""").findAll(src).take(5).forEach { m ->
                val body = extractBody(src, m.range.last)
                results += PreviewResult(
                    meta = PreviewMeta(m.groupValues[1]),
                    tree = parseBody(body),
                    warnings = listOf("Sem @Preview — visualização estimada")
                )
            }
        }
        return results
    }

    // ── watchers ──────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    fun watchFile(path: String, onChanged: (List<PreviewResult>) -> Unit): FileObserver {
        val obs = object : FileObserver(path, CLOSE_WRITE) {
            override fun onEvent(event: Int, p: String?) {
                onChanged(parseFile(File(path)))
            }
        }
        obs.startWatching()
        return obs
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun parseMeta(name: String, ann: String): PreviewMeta {
        fun int(k: String, d: Int)    = Regex("""$k\s*=\s*(\d+)""").find(ann)?.gv(1)?.toIntOrNull() ?: d
        fun float(k: String, d: Float) = Regex("""$k\s*=\s*([\d.]+)""").find(ann)?.gv(1)?.toFloatOrNull() ?: d
        fun str(k: String)             = Regex("""$k\s*=\s*"([^"]*)"""").find(ann)?.gv(1) ?: ""
        fun bool(k: String, d: Boolean) = Regex("""$k\s*=\s*(true|false)""").find(ann)?.gv(1)
            ?.let { it == "true" } ?: d
        return PreviewMeta(name, int("widthDp",360), int("heightDp",640),
            bool("showBackground",true), 0xFFFFFFFF, float("fontScale",1f),
            str("group"), str("device"), int("uiMode",0))
    }

    private fun extractBody(src: String, from: Int): String {
        var depth = 0; var started = false
        val sb = StringBuilder()
        for (i in from until minOf(from + 6000, src.length)) {
            val c = src[i]
            if (c == '{') { depth++; started = true }
            if (started) sb.append(c)
            if (c == '}') { depth--; if (started && depth == 0) break }
        }
        return sb.toString()
    }

    private fun parseBody(body: String): List<SemanticNode> {
        val nodes = mutableListOf<SemanticNode>()
        body.lines().forEach { raw ->
            val t = raw.trim()
            when {
                t.startsWith("Text(") || t.startsWith("BasicText(") -> {
                    val txt = Regex("""text\s*=\s*"([^"]*)"""").find(t)?.gv(1)
                        ?: Regex(""""([^"]*)"""").find(t)?.gv(1) ?: "Text"
                    val sty = when {
                        t.contains("headline",true) || t.contains("Title",true) -> "headline"
                        t.contains("titleMedium",true) || t.contains("titleLarge",true) -> "title"
                        t.contains("labelSmall",true) || t.contains("caption",true) -> "label"
                        else -> "body"
                    }
                    nodes += SemanticNode.Txt(txt.take(40), sty)
                }
                t.startsWith("Button(") || t.startsWith("ElevatedButton(") -> {
                    val lbl = Regex("""text\s*=\s*"([^"]*)"""").find(t)?.gv(1) ?: "Button"
                    nodes += SemanticNode.Btn(lbl, if (t.startsWith("Elevated")) "elevated" else "filled")
                }
                t.startsWith("OutlinedButton(") -> nodes += SemanticNode.Btn(
                    Regex("""text\s*=\s*"([^"]*)"""").find(t)?.gv(1) ?: "Button", "outlined")
                t.startsWith("TextButton(") -> nodes += SemanticNode.Btn(
                    Regex("""text\s*=\s*"([^"]*)"""").find(t)?.gv(1) ?: "Button", "text")
                t.startsWith("FloatingActionButton(") || t.startsWith("ExtendedFloatingActionButton(") ->
                    nodes += SemanticNode.Fab()
                t.startsWith("TextField(") -> nodes += SemanticNode.Field(
                    Regex("""label\s*=\s*\{?\s*Text\("([^"]*)""").find(t)?.gv(1) ?: "Texto", false)
                t.startsWith("OutlinedTextField(") -> nodes += SemanticNode.Field(
                    Regex("""label\s*=\s*\{?\s*Text\("([^"]*)""").find(t)?.gv(1) ?: "Texto", true)
                t.startsWith("Card(") || t.startsWith("ElevatedCard(") || t.startsWith("OutlinedCard(") ->
                    nodes += SemanticNode.Card(listOf(SemanticNode.Txt("Card content")))
                t.startsWith("Spacer(") ->
                    nodes += SemanticNode.Space(Regex("""(\d+)\.dp""").find(t)?.gv(1)?.toIntOrNull() ?: 8)
                t.startsWith("Switch(") -> nodes += SemanticNode.Toggle("Switch")
                t.startsWith("Slider(") || t.startsWith("RangeSlider(") -> nodes += SemanticNode.Slide("Slider")
                t.startsWith("Checkbox(") -> nodes += SemanticNode.Check("Checkbox")
                t.startsWith("RadioButton(") -> nodes += SemanticNode.Check("RadioButton")
                t.startsWith("AssistChip(") || t.startsWith("FilterChip(") ||
                t.startsWith("SuggestionChip(") || t.startsWith("InputChip(") ->
                    nodes += SemanticNode.Chip(Regex("""label\s*=\s*"([^"]*)"""").find(t)?.gv(1) ?: "Chip")
                t.startsWith("Image(") || t.startsWith("AsyncImage(") ->
                    nodes += SemanticNode.Img(Regex("""contentDescription\s*=\s*"([^"]*)"""").find(t)?.gv(1) ?: "image")
                t.startsWith("Icon(") ->
                    nodes += SemanticNode.Img("icon")
            }
        }
        return nodes.ifEmpty { listOf(SemanticNode.Unknown("@Composable")) }
    }

    private fun MatchResult.gv(i: Int) = groupValues[i]
}
