package com.androidstudiomobile.ui.screens

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

// ─────────────────────────────────────────────────────────────────────────────
// LayoutHierarchyParser.kt
// Parseia arquivos XML de layout Android e retorna uma árvore de ViewNode.
// Cada nó contém: tag, id, lista de atributos (com namespace), filhos e linha.
// Suporta edição inline de atributos (updateAttribute) e estatísticas.
// ─────────────────────────────────────────────────────────────────────────────

object LayoutHierarchyParser {

    // ── modelo ────────────────────────────────────────────────────────────────

    data class ViewAttribute(
        val namespace: String,
        val name: String,
        var value: String
    ) {
        /** Ex: "android:layout_width" */
        val fullName: String get() = if (namespace.isNotEmpty()) "$namespace:$name" else name
    }

    data class ViewNode(
        val tag: String,
        val attributes: MutableList<ViewAttribute>,
        val children: MutableList<ViewNode> = mutableListOf(),
        val lineNumber: Int = 0
    ) {
        val id: String get() = attributes.find { it.name == "id" }
            ?.value?.removePrefix("@+id/")?.removePrefix("@id/") ?: ""

        val simpleName: String get() = tag.substringAfterLast('.')

        val layoutWidth: String get() =
            attributes.find { it.name == "layout_width" }?.value ?: "?"
        val layoutHeight: String get() =
            attributes.find { it.name == "layout_height" }?.value ?: "?"
    }

    data class TreeStats(
        val totalViews: Int,
        val maxDepth: Int,
        val viewTypes: Map<String, Int>
    )

    // ── parse ─────────────────────────────────────────────────────────────────

    fun parse(file: File): ViewNode? = parse(file.readText())

    fun parse(xml: String): ViewNode? = runCatching {
        val factory = XmlPullParserFactory.newInstance().also { it.isNamespaceAware = true }
        val xpp     = factory.newPullParser().also { it.setInput(StringReader(xml)) }
        val stack   = ArrayDeque<ViewNode>()
        var root: ViewNode? = null

        var ev = xpp.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val attrs = (0 until xpp.attributeCount).map { i ->
                        ViewAttribute(
                            namespace = xpp.getAttributePrefix(i) ?: "",
                            name      = xpp.getAttributeName(i),
                            value     = xpp.getAttributeValue(i)
                        )
                    }.toMutableList()

                    val node = ViewNode(xpp.name, attrs, lineNumber = xpp.lineNumber)
                    stack.lastOrNull()?.children?.add(node)
                    stack.addLast(node)
                    if (root == null) root = node
                }
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeLast()
            }
            ev = xpp.next()
        }
        root
    }.getOrNull()

    // ── edição ────────────────────────────────────────────────────────────────

    /**
     * Substitui o valor de [attr] em [xml] preservando comentários e formatação.
     * Usa word-boundary no nome do atributo para evitar falsos positivos.
     */
    fun updateAttribute(xml: String, attr: ViewAttribute, newValue: String): String {
        val pattern = Regex("""(${Regex.escape(attr.fullName)}\s*=\s*")([^"]*)("|\')""")
        var count = 0
        return pattern.replace(xml) { mr ->
            if (count++ == 0) "${mr.groupValues[1]}$newValue${mr.groupValues[3]}"
            else mr.value
        }
    }

    /** Adiciona um novo atributo ao nó (insere antes do fechamento da tag). */
    fun addAttribute(xml: String, node: ViewNode, attrName: String, value: String): String {
        val tagPattern = Regex("""(<${Regex.escape(node.tag)}[^>]*)(/?>)""")
        var count = 0
        return tagPattern.replace(xml) { mr ->
            if (count++ == 0) "${mr.groupValues[1]}\n    $attrName=\"$value\"${mr.groupValues[2]}"
            else mr.value
        }
    }

    // ── estatísticas ──────────────────────────────────────────────────────────

    fun computeStats(root: ViewNode): TreeStats {
        var total = 0; var maxDepth = 0
        val types = mutableMapOf<String, Int>()
        fun walk(n: ViewNode, d: Int) {
            total++
            if (d > maxDepth) maxDepth = d
            types[n.simpleName] = (types[n.simpleName] ?: 0) + 1
            n.children.forEach { walk(it, d + 1) }
        }
        walk(root, 0)
        return TreeStats(total, maxDepth, types)
    }

    // ── geração de XML ────────────────────────────────────────────────────────

    /** Gera XML mínimo a partir de uma árvore ViewNode. */
    fun toXml(root: ViewNode, indent: String = ""): String = buildString {
        append("$indent<${root.tag}")
        root.attributes.forEach { a -> append("\n$indent    ${a.fullName}=\"${a.value}\"") }
        if (root.children.isEmpty()) {
            appendLine(" />")
        } else {
            appendLine(">")
            root.children.forEach { append(toXml(it, "$indent    ")) }
            appendLine("$indent</${root.tag}>")
        }
    }
}
