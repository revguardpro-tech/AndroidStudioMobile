package com.androidstudiomobile.preview

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compose Preview estático.
 *
 * Solução adotada:
 * - Parseia o código Kotlin/XML em busca de funções @Preview e parâmetros de device.
 * - Gera um "mapa de preview" que o DesignPreviewCanvas renderiza em Compose.
 * - Para XML: parseia as tags e converte em uma árvore de ViewNodes que são
 *   desenhados no Canvas do Compose como representações visuais aproximadas.
 * - Para Compose: extrai metadados do @Preview (widthDp, heightDp, device, etc.)
 *   e renderiza um placeholder estático com as informações extraídas.
 *
 * Isso NÃO é execução real do código Compose, mas uma representação visual
 * suficiente para ter noção do layout antes de compilar.
 */
object ComposePreviewer {

    data class PreviewAnnotation(
        val functionName: String,
        val widthDp: Int = 360,
        val heightDp: Int = 640,
        val device: String = "Pixel 6",
        val showBackground: Boolean = true,
        val backgroundColor: Long = 0xFFFFFFFF,
        val apiLevel: Int = 33,
        val uiMode: String = "normal",
        val fontScale: Float = 1.0f,
        val group: String = "",
        val name: String = ""
    )

    data class XmlViewNode(
        val tag: String,
        val id: String = "",
        val text: String = "",
        val widthSpec: String = "wrap_content",
        val heightSpec: String = "wrap_content",
        val background: String = "",
        val textColor: String = "",
        val textSize: Float = 14f,
        val paddingDp: Int = 0,
        val marginDp: Int = 0,
        val gravity: String = "",
        val orientation: String = "vertical",
        val children: List<XmlViewNode> = emptyList(),
        val attributes: Map<String, String> = emptyMap()
    )

    data class ParsedLayout(
        val root: XmlViewNode?,
        val source: String, // "xml" or "compose"
        val previewAnnotations: List<PreviewAnnotation>
    )

    /**
     * Parseia um arquivo Kotlin em busca de funções @Preview.
     */
    suspend fun parseKotlinFile(filePath: String): List<PreviewAnnotation> =
        withContext(Dispatchers.IO) {
            try {
                val content = File(filePath).readText()
                parseKotlinContent(content)
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun parseKotlinContent(content: String): List<PreviewAnnotation> {
        val previews = mutableListOf<PreviewAnnotation>()

        // Regex para capturar @Preview com parâmetros e a função seguinte
        val previewBlockRegex = Regex(
            """@Preview\s*(\([^)]*\))?\s*(?:@[A-Za-z]+\s*(?:\([^)]*\))?\s*)*@Composable\s*(?:(?:private|internal|public)\s+)?fun\s+(\w+)""",
            RegexOption.DOT_MATCHES_ALL
        )

        previewBlockRegex.findAll(content).forEach { match ->
            val params = match.groupValues[1]
            val funcName = match.groupValues[2]

            previews += PreviewAnnotation(
                functionName = funcName,
                widthDp = extractIntParam(params, "widthDp") ?: 360,
                heightDp = extractIntParam(params, "heightDp") ?: 640,
                device = extractStringParam(params, "device") ?: "Pixel 6",
                showBackground = extractBoolParam(params, "showBackground") ?: true,
                backgroundColor = extractLongParam(params, "backgroundColor") ?: 0xFFFFFFFF,
                fontScale = extractFloatParam(params, "fontScale") ?: 1.0f,
                group = extractStringParam(params, "group") ?: "",
                name = extractStringParam(params, "name") ?: funcName,
                uiMode = extractStringParam(params, "uiMode") ?: "normal"
            )
        }

        // Fallback: buscar @Composable sem @Preview
        if (previews.isEmpty()) {
            val composableRegex = Regex("""@Composable\s+(?:(?:private|internal|public)\s+)?fun\s+(\w+)""")
            composableRegex.findAll(content).take(5).forEach { match ->
                val name = match.groupValues[1]
                if (!name[0].isLowerCase() || name.endsWith("Screen") || name.endsWith("View")) {
                    previews += PreviewAnnotation(
                        functionName = name,
                        name = name,
                        widthDp = 360,
                        heightDp = 640
                    )
                }
            }
        }

        return previews
    }

    /**
     * Parseia um arquivo XML de layout Android.
     */
    suspend fun parseXmlLayout(filePath: String): XmlViewNode? =
        withContext(Dispatchers.IO) {
            try {
                val content = File(filePath).readText()
                parseXmlContent(content)
            } catch (_: Exception) {
                null
            }
        }

    fun parseXmlContent(xml: String): XmlViewNode? {
        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())
            doc.documentElement?.let { parseElement(it, 0) }
        } catch (_: Exception) {
            // Fallback: regex simples para extrair a tag raiz
            val tagMatch = Regex("""<(\w[\w.]+)""").find(xml)
            tagMatch?.let {
                XmlViewNode(
                    tag = it.groupValues[1],
                    widthSpec = "match_parent",
                    heightSpec = "match_parent"
                )
            }
        }
    }

    private fun parseElement(element: org.w3c.dom.Element, depth: Int): XmlViewNode {
        if (depth > 10) return XmlViewNode(tag = element.tagName)

        val attrs = mutableMapOf<String, String>()
        val attrList = element.attributes
        for (i in 0 until attrList.length) {
            val attr = attrList.item(i)
            val localName = attr.localName ?: attr.nodeName.substringAfterLast(":")
            attrs[localName] = attr.nodeValue
        }

        val children = mutableListOf<XmlViewNode>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                children += parseElement(child as org.w3c.dom.Element, depth + 1)
            }
        }

        return XmlViewNode(
            tag = element.localName ?: element.tagName.substringAfterLast("."),
            id = attrs["id"]?.removePrefix("@+id/")?.removePrefix("@id/") ?: "",
            text = attrs["text"]?.removePrefix("@string/") ?: "",
            widthSpec = normalizeSize(attrs["layout_width"] ?: "wrap_content"),
            heightSpec = normalizeSize(attrs["layout_height"] ?: "wrap_content"),
            background = attrs["background"] ?: "",
            textColor = attrs["textColor"] ?: "",
            textSize = attrs["textSize"]?.removeSuffix("sp")?.toFloatOrNull() ?: 14f,
            paddingDp = attrs["padding"]?.removeSuffix("dp")?.toIntOrNull() ?: 0,
            gravity = attrs["gravity"] ?: attrs["layout_gravity"] ?: "",
            orientation = attrs["orientation"] ?: "vertical",
            children = children,
            attributes = attrs
        )
    }

    private fun normalizeSize(size: String): String = when (size) {
        "match_parent", "-1" -> "match_parent"
        "wrap_content", "-2" -> "wrap_content"
        else -> size
    }

    // ─── Parsing helpers ────────────────────────────────────────────────

    private fun extractIntParam(params: String, name: String): Int? =
        Regex("""$name\s*=\s*(\d+)""").find(params)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractStringParam(params: String, name: String): String? =
        Regex("""$name\s*=\s*"([^"]*)" """).find(params)?.groupValues?.get(1)

    private fun extractBoolParam(params: String, name: String): Boolean? =
        Regex("""$name\s*=\s*(true|false)""").find(params)?.groupValues?.get(1)?.toBoolean()

    private fun extractFloatParam(params: String, name: String): Float? =
        Regex("""$name\s*=\s*([\d.]+)f?""").find(params)?.groupValues?.get(1)?.toFloatOrNull()

    private fun extractLongParam(params: String, name: String): Long? =
        Regex("""$name\s*=\s*0x([0-9A-Fa-f]+)L?""").find(params)?.groupValues?.get(1)
            ?.toLongOrNull(16)

    /**
     * Detecta se um arquivo tem previews (Compose ou XML).
     */
    fun detectPreviewType(filePath: String): PreviewType {
        val file = File(filePath)
        return when (file.extension.lowercase()) {
            "kt", "kts" -> {
                val content = file.readText()
                if (content.contains("@Preview")) PreviewType.COMPOSE_PREVIEW
                else if (content.contains("@Composable")) PreviewType.COMPOSE_NO_PREVIEW
                else PreviewType.NONE
            }
            "xml" -> {
                val content = file.readText()
                if (content.contains("ConstraintLayout") || content.contains("LinearLayout") ||
                    content.contains("RelativeLayout") || content.contains("FrameLayout") ||
                    content.contains("CoordinatorLayout") || content.contains("RecyclerView")) {
                    PreviewType.XML_LAYOUT
                } else PreviewType.NONE
            }
            else -> PreviewType.NONE
        }
    }

    enum class PreviewType {
        COMPOSE_PREVIEW,
        COMPOSE_NO_PREVIEW,
        XML_LAYOUT,
        NONE
    }
}
