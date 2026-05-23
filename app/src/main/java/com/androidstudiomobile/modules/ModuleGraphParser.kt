package com.androidstudiomobile.modules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-module Gradle Sync — parser de settings.gradle.kts e build.gradle.kts.
 *
 * Solução adotada:
 * - Parseia o arquivo settings.gradle(.kts) para detectar todos os módulos incluídos.
 * - Para cada módulo, lê o build.gradle(.kts) e extrai as dependências entre módulos
 *   (implementation(project(":modulo")), api(project(":modulo")), etc.)
 * - Constrói um grafo de dependências que é exibido visualmente.
 * - NÃO baixa dependências externas — apenas analisa a estrutura do projeto.
 * - A sincronização detecta: módulos app, lib, feature, core, etc.
 */
object ModuleGraphParser {

    data class GradleModule(
        val name: String,          // e.g. ":app", ":feature:home"
        val path: String,          // caminho absoluto no sistema de arquivos
        val type: ModuleType,
        val dependencies: List<ModuleDependency>,
        val externalDeps: List<String>,
        val buildFile: String,
        val hasKotlin: Boolean,
        val hasCompose: Boolean,
        val plugins: List<String>
    )

    data class ModuleDependency(
        val targetModule: String,
        val configuration: String // implementation, api, testImplementation, etc.
    )

    enum class ModuleType {
        APP, LIBRARY, FEATURE, CORE, TEST, UNKNOWN
    }

    data class ModuleGraph(
        val modules: List<GradleModule>,
        val edges: List<Triple<String, String, String>>, // from, to, config
        val rootPath: String,
        val settingsFile: String
    )

    /**
     * Parseia a estrutura completa de módulos de um projeto Android.
     */
    suspend fun parseProject(projectPath: String): ModuleGraph = withContext(Dispatchers.IO) {
        val rootDir = File(projectPath)

        // 1. Encontrar o settings.gradle
        val settingsFile = findSettingsFile(rootDir)
            ?: return@withContext ModuleGraph(emptyList(), emptyList(), projectPath, "")

        // 2. Parsear módulos do settings.gradle
        val moduleNames = parseSettingsFile(settingsFile)

        // 3. Para cada módulo, parsear seu build.gradle
        val modules = moduleNames.mapNotNull { moduleName ->
            val modulePath = moduleName.replace(":", File.separator)
            val moduleDir = File(rootDir, modulePath.trimStart(File.separatorChar))
            if (!moduleDir.exists()) return@mapNotNull null
            parseBuildFile(moduleDir, moduleName, rootDir)
        }

        // 4. Construir grafo de arestas
        val edges = modules.flatMap { module ->
            module.dependencies.map { dep ->
                Triple(module.name, dep.targetModule, dep.configuration)
            }
        }

        ModuleGraph(modules, edges, projectPath, settingsFile.absolutePath)
    }

    private fun findSettingsFile(rootDir: File): File? {
        return listOf(
            File(rootDir, "settings.gradle.kts"),
            File(rootDir, "settings.gradle")
        ).firstOrNull { it.exists() }
    }

    private fun parseSettingsFile(settingsFile: File): List<String> {
        val content = settingsFile.readText()
        val modules = mutableListOf<String>()

        // include(":app"), include(":feature:home", ":core:network")
        val includeRegex = Regex("""include\s*\(\s*"([^"]+)"\s*\)""")
        includeRegex.findAll(content).forEach { match ->
            val args = match.groupValues[1]
            // Pode ter múltiplos módulos separados por vírgula dentro do include
            args.split(",").forEach { part ->
                val name = part.trim().removeSurrounding("\"")
                if (name.startsWith(":")) modules += name
            }
        }

        // Formato alternativo: include ":app", ":lib"
        val includeShortRegex = Regex("""include\s+"([^"]+)"""")
        includeShortRegex.findAll(content).forEach { match ->
            val name = match.groupValues[1]
            if (name.startsWith(":")) modules += name
        }

        return modules.distinct()
    }

    private fun parseBuildFile(moduleDir: File, moduleName: String, rootDir: File): GradleModule? {
        val buildFile = listOf(
            File(moduleDir, "build.gradle.kts"),
            File(moduleDir, "build.gradle")
        ).firstOrNull { it.exists() } ?: return GradleModule(
            name = moduleName,
            path = moduleDir.absolutePath,
            type = inferModuleType(moduleName),
            dependencies = emptyList(),
            externalDeps = emptyList(),
            buildFile = "",
            hasKotlin = false,
            hasCompose = false,
            plugins = emptyList()
        )

        val content = buildFile.readText()

        // Extrair plugins
        val plugins = extractPlugins(content)

        // Extrair dependências de projeto
        val moduleDeps = extractModuleDependencies(content)

        // Extrair dependências externas (top 10)
        val externalDeps = extractExternalDependencies(content)

        return GradleModule(
            name = moduleName,
            path = moduleDir.absolutePath,
            type = inferModuleType(moduleName, plugins, content),
            dependencies = moduleDeps,
            externalDeps = externalDeps,
            buildFile = buildFile.absolutePath,
            hasKotlin = content.contains("kotlin") || plugins.any { it.contains("kotlin") },
            hasCompose = content.contains("compose") || content.contains("Compose"),
            plugins = plugins
        )
    }

    private fun extractPlugins(content: String): List<String> {
        val plugins = mutableListOf<String>()

        // plugins { id("com.android.application") }
        val pluginBlockRegex = Regex("""plugins\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        pluginBlockRegex.find(content)?.groupValues?.get(1)?.let { block ->
            val idRegex = Regex("""(?:id|alias)\s*\(\s*["']([^"']+)["']\s*\)""")
            idRegex.findAll(block).forEach { plugins += it.groupValues[1] }
            val aliasRegex = Regex("""alias\(libs\.plugins\.([a-zA-Z.]+)\)""")
            aliasRegex.findAll(block).forEach { plugins += "libs.plugins.${it.groupValues[1]}" }
        }

        return plugins
    }

    private fun extractModuleDependencies(content: String): List<ModuleDependency> {
        val deps = mutableListOf<ModuleDependency>()
        // implementation(project(":module")) or api(project(":module"))
        val depRegex = Regex("""(implementation|api|testImplementation|androidTestImplementation|compileOnly|runtimeOnly)\s*\(\s*project\s*\(\s*["']([^"']+)["']\s*\)\s*\)""")
        depRegex.findAll(content).forEach { match ->
            val config = match.groupValues[1]
            val module = match.groupValues[2]
            deps += ModuleDependency(module, config)
        }
        return deps
    }

    private fun extractExternalDependencies(content: String): List<String> {
        val deps = mutableListOf<String>()
        // implementation("group:artifact:version") ou libs.xxx
        val depRegex = Regex("""(?:implementation|api|testImplementation)\s*\(\s*["']([^"']+)["']\s*\)""")
        depRegex.findAll(content).take(10).forEach { match ->
            deps += match.groupValues[1]
        }
        val libsRegex = Regex("""(?:implementation|api)\s*\(libs\.([a-zA-Z.]+)\)""")
        libsRegex.findAll(content).take(10).forEach { match ->
            deps += "libs.${match.groupValues[1]}"
        }
        return deps.take(15)
    }

    private fun inferModuleType(name: String, plugins: List<String> = emptyList(), content: String = ""): ModuleType {
        val lowerName = name.lowercase()
        return when {
            lowerName == ":app" || lowerName.endsWith(":app") -> ModuleType.APP
            plugins.any { it.contains("android.application") } -> ModuleType.APP
            lowerName.contains(":feature") || lowerName.contains("feature:") -> ModuleType.FEATURE
            lowerName.contains(":core") || lowerName.contains("core:") -> ModuleType.CORE
            plugins.any { it.contains("android.library") } -> ModuleType.LIBRARY
            content.contains("com.android.library") -> ModuleType.LIBRARY
            lowerName.contains("test") -> ModuleType.TEST
            else -> ModuleType.LIBRARY
        }
    }

    /**
     * Detecta dependências circulares no grafo.
     */
    fun detectCircularDependencies(graph: ModuleGraph): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val adjacency = graph.edges.groupBy({ it.first }, { it.second })

        fun dfs(node: String, path: MutableList<String>, visited: MutableSet<String>) {
            if (path.contains(node)) {
                val cycleStart = path.indexOf(node)
                cycles += path.subList(cycleStart, path.size) + node
                return
            }
            if (visited.contains(node)) return
            visited += node
            path += node
            adjacency[node]?.forEach { neighbor -> dfs(neighbor, path, visited) }
            path.removeLastOrNull()
        }

        graph.modules.forEach { module ->
            dfs(module.name, mutableListOf(), mutableSetOf())
        }
        return cycles
    }
}
