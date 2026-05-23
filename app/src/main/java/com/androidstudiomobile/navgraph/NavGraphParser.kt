package com.androidstudiomobile.navgraph

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

// ─────────────────────────────────────────────────────────────────────────────
// NavGraphParser.kt
//
// Parseia arquivos de Navigation Graph XML (Jetpack Navigation).
// Extrai: fragments, activities, dialogs, include, actions, arguments, deepLinks.
// Suporta nested graphs.
// Gera XML válido de volta via toXml().
// Layout automático de nós via computeLayout().
// ─────────────────────────────────────────────────────────────────────────────

object NavGraphParser {

    // ── modelo ────────────────────────────────────────────────────────────────

    enum class DestType { FRAGMENT, ACTIVITY, DIALOG, NAVIGATION, INCLUDE }

    data class NavArgument(
        val name: String,
        val type: String,
        val default: String = ""
    )

    data class NavAction(
        val id: String,
        val destination: String,
        val popUpTo: String      = "",
        val inclusive: Boolean   = false,
        val enterAnim: String    = "",
        val exitAnim: String     = ""
    )

    data class NavDestination(
        val id: String,
        val name: String,
        val type: DestType,
        val label: String                       = "",
        val layout: String                      = "",
        val actions: MutableList<NavAction>     = mutableListOf(),
        val arguments: MutableList<NavArgument> = mutableListOf(),
        val deepLinks: MutableList<String>      = mutableListOf(),
        var x: Float = 0f,
        var y: Float = 0f
    )

    data class NavGraph(
        val id: String,
        val startDestination: String,
        val destinations: MutableList<NavDestination> = mutableListOf()
    )

    // ── parse ─────────────────────────────────────────────────────────────────

    fun parse(file: File): NavGraph? = parse(file.readText())

    fun parse(xml: String): NavGraph? = runCatching {
        val xpp = XmlPullParserFactory.newInstance()
            .also { it.isNamespaceAware = true }
            .newPullParser()
            .also { it.setInput(StringReader(xml)) }

        var graph: NavGraph? = null
        val stack = ArrayDeque<NavDestination>()

        fun attr(vararg keys: String): String {
            for (k in keys) {
                val v = runCatching { xpp.getAttributeValue(null, k.substringAfter(':')) }.getOrNull()
                    ?: runCatching { xpp.getAttributeValue("http://schemas.android.com/apk/res/android", k.substringAfter(':')) }.getOrNull()
                    ?: runCatching { xpp.getAttributeValue("http://schemas.android.com/apk/res-auto", k.substringAfter(':')) }.getOrNull()
                if (!v.isNullOrEmpty()) return v
            }
            return ""
        }

        fun cleanId(s: String) = s.removePrefix("@+id/").removePrefix("@id/")

        var ev = xpp.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (xpp.name) {
                    "navigation" -> {
                        graph = NavGraph(
                            id = cleanId(attr("android:id","id")),
                            startDestination = cleanId(attr("app:startDestination","startDestination"))
                        )
                    }
                    "fragment","activity","dialog","include","navigation" -> {
                        if (graph == null) { ev = xpp.next(); continue }
                        val type = when (xpp.name) {
                            "fragment"   -> DestType.FRAGMENT
                            "activity"   -> DestType.ACTIVITY
                            "dialog"     -> DestType.DIALOG
                            "include"    -> DestType.INCLUDE
                            else         -> DestType.NAVIGATION
                        }
                        val dest = NavDestination(
                            id     = cleanId(attr("android:id","id")),
                            name   = attr("android:name","name").substringAfterLast('.'),
                            type   = type,
                            label  = attr("android:label","tools:label","label"),
                            layout = attr("android:layout","layout","tools:layout").removePrefix("@layout/")
                        )
                        graph!!.destinations.add(dest)
                        stack.addLast(dest)
                    }
                    "action" -> {
                        val action = NavAction(
                            id          = cleanId(attr("android:id","id")),
                            destination = cleanId(attr("app:destination","destination")),
                            popUpTo     = cleanId(attr("app:popUpTo","popUpTo")),
                            inclusive   = attr("app:popUpToInclusive","popUpToInclusive") == "true",
                            enterAnim   = attr("app:enterAnim","enterAnim"),
                            exitAnim    = attr("app:exitAnim","exitAnim")
                        )
                        stack.lastOrNull()?.actions?.add(action)
                    }
                    "argument" -> {
                        val arg = NavArgument(
                            name    = attr("android:name","name"),
                            type    = attr("app:argType","argType","android:argType"),
                            default = attr("android:defaultValue","defaultValue","app:defaultValue")
                        )
                        stack.lastOrNull()?.arguments?.add(arg)
                    }
                    "deepLink" -> {
                        val uri = attr("app:uri","uri","android:uri")
                        if (uri.isNotEmpty()) stack.lastOrNull()?.deepLinks?.add(uri)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if (xpp.name in listOf("fragment","activity","dialog","include","navigation"))
                    if (stack.isNotEmpty()) stack.removeLast()
            }
            ev = xpp.next()
        }
        graph
    }.getOrNull()

    // ── layout automático ─────────────────────────────────────────────────────

    fun computeLayout(graph: NavGraph, canvasW: Float = 760f, canvasH: Float = 520f) {
        val n = graph.destinations.size.takeIf { it > 0 } ?: return
        val cols = maxOf(1, Math.ceil(Math.sqrt(n.toDouble())).toInt())
        val rows = Math.ceil(n.toDouble() / cols).toInt()
        val sx = canvasW / (cols + 1); val sy = canvasH / (rows + 1)
        graph.destinations.forEachIndexed { i, d ->
            d.x = sx * ((i % cols) + 1)
            d.y = sy * ((i / cols) + 1)
        }
        // Start destination vai para o canto superior esquerdo
        graph.destinations.find { it.id == graph.startDestination }?.let { d.x = sx; d.y = sy }
    }

    // ── serialização ──────────────────────────────────────────────────────────

    fun toXml(graph: NavGraph): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<navigation xmlns:android="http://schemas.android.com/apk/res/android"""")
        appendLine("""    xmlns:app="http://schemas.android.com/apk/res-auto"""")
        appendLine("""    xmlns:tools="http://schemas.android.com/tools"""")
        appendLine("""    android:id="@+id/${graph.id}"""")
        appendLine("""    app:startDestination="@id/${graph.startDestination}">""")
        graph.destinations.forEach { d ->
            val tag = when (d.type) {
                DestType.FRAGMENT   -> "fragment"
                DestType.ACTIVITY   -> "activity"
                DestType.DIALOG     -> "dialog"
                DestType.INCLUDE    -> "include"
                DestType.NAVIGATION -> "navigation"
            }
            append("    <$tag")
            append(""" android:id="@+id/${d.id}"""")
            if (d.name.isNotEmpty())   append(""" android:name="${d.name}"""")
            if (d.label.isNotEmpty())  append(""" android:label="${d.label}"""")
            if (d.layout.isNotEmpty()) append(""" tools:layout="@layout/${d.layout}"""")
            if (d.actions.isEmpty() && d.arguments.isEmpty() && d.deepLinks.isEmpty()) {
                appendLine(" />")
            } else {
                appendLine(">")
                d.actions.forEach { a ->
                    append("""        <action android:id="@+id/${a.id}" app:destination="@id/${a.destination}"""")
                    if (a.popUpTo.isNotEmpty()) append(""" app:popUpTo="@id/${a.popUpTo}"""")
                    if (a.inclusive) append(""" app:popUpToInclusive="true"""")
                    appendLine("/>")
                }
                d.arguments.forEach { a ->
                    append("""        <argument android:name="${a.name}" app:argType="${a.type}"""")
                    if (a.default.isNotEmpty()) append(""" android:defaultValue="${a.default}"""")
                    appendLine("/>")
                }
                d.deepLinks.forEach { uri -> appendLine("""        <deepLink app:uri="$uri"/>""") }
                appendLine("    </$tag>")
            }
        }
        appendLine("</navigation>")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    fun addDestination(graph: NavGraph, name: String, type: DestType): NavDestination {
        val id = name.replaceFirstChar { it.lowercase() }.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val dest = NavDestination(id = id, name = name, type = type,
            label = name.replace(Regex("([A-Z])"), " $1").trim())
        graph.destinations.add(dest)
        computeLayout(graph)
        return dest
    }
}
