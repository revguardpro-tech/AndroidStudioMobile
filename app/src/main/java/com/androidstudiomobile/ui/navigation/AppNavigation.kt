package com.androidstudiomobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.androidstudiomobile.MainApplication
import com.androidstudiomobile.navgraph.NavGraphEditorScreen
import com.androidstudiomobile.profiler.ProfilerScreen
import com.androidstudiomobile.ui.screens.*
import com.androidstudiomobile.nativedebug.NativeDebugScreen
import com.androidstudiomobile.energy.EnergyProfilerScreen
import com.androidstudiomobile.sensors.SensorSimulatorScreen
import com.androidstudiomobile.testrunner.TestRunnerScreen
import com.androidstudiomobile.semantic.AdvancedRefactorScreen
import com.androidstudiomobile.playconsole.PlayConsoleScreen
import com.androidstudiomobile.xmlcompose.XmlToComposeScreen
import com.androidstudiomobile.remotebuild.RemoteBuildScreen
import com.androidstudiomobile.devicefarm.DeviceFarmScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Projects        : Screen("projects")
    object NewProject      : Screen("new_project")
    object Workspace       : Screen("workspace/{projectId}") {
        fun withId(id: Long) = "workspace/$id"
    }
    object Git             : Screen("git/{projectPath}") {
        fun withPath(p: String) = "git/${URLEncoder.encode(p, "UTF-8")}"
    }
    object FindInProject   : Screen("find/{projectPath}") {
        fun withPath(p: String) = "find/${URLEncoder.encode(p, "UTF-8")}"
    }
    object ApkAnalyzer     : Screen("apk/{apkPath}") {
        fun withPath(p: String) = "apk/${URLEncoder.encode(p, "UTF-8")}"
    }
    object SdkManager      : Screen("sdk_manager")
    object Settings        : Screen("settings")
    object Logcat          : Screen("logcat")
    object ResourceManager : Screen("resources/{projectPath}") {
        fun withPath(p: String) = "resources/${URLEncoder.encode(p, "UTF-8")}"
    }
    object Refactor        : Screen("refactor/{projectPath}") {
        fun withPath(p: String) = "refactor/${URLEncoder.encode(p, "UTF-8")}"
    }
    object SignedApkWizard : Screen("signed_apk/{projectPath}") {
        fun withPath(p: String) = "signed_apk/${URLEncoder.encode(p, "UTF-8")}"
    }
    object ComposePreview  : Screen("compose_preview/{filePath}") {
        fun withPath(p: String) = "compose_preview/${URLEncoder.encode(p, "UTF-8")}"
    }
    object ThemeEditor     : Screen("theme_editor/{projectPath}") {
        fun withPath(p: String) = "theme_editor/${URLEncoder.encode(p, "UTF-8")}"
    }
    object DeviceSimulator : Screen("device_sim/{filePath}") {
        fun withPath(p: String) = "device_sim/${URLEncoder.encode(p, "UTF-8")}"
    }
    object HierarchyViewer : Screen("hierarchy/{filePath}") {
        fun withPath(p: String) = "hierarchy/${URLEncoder.encode(p, "UTF-8")}"
    }
    object DatabaseInspector : Screen("db_inspector/{dbPath}") {
        fun withPath(p: String) = "db_inspector/${URLEncoder.encode(p, "UTF-8")}"
        val empty = "db_inspector/${URLEncoder.encode("", "UTF-8")}"
    }
    object ModuleGraph     : Screen("module_graph/{projectPath}") {
        fun withPath(p: String) = "module_graph/${URLEncoder.encode(p, "UTF-8")}"
    }
    object DebugInspector  : Screen("debug/{filePath}/{projectPath}") {
        fun withPaths(f: String, p: String) =
            "debug/${URLEncoder.encode(f, "UTF-8")}/${URLEncoder.encode(p, "UTF-8")}"
    }
    // ── Telas originais extras ────────────────────────────────────────────────
    object NavGraphEditor  : Screen("nav_graph/{filePath}") {
        fun withPath(p: String) = "nav_graph/${URLEncoder.encode(p, "UTF-8")}"
    }
    object LayoutEditor    : Screen("layout_editor/{outputPath}") {
        fun withPath(p: String) = "layout_editor/${URLEncoder.encode(p, "UTF-8")}"
        val empty = "layout_editor/${URLEncoder.encode("", "UTF-8")}"
    }
    object Profiler        : Screen("profiler")

    // ── 10 Novos Blocos ───────────────────────────────────────────────────────
    object NativeDebug     : Screen("native_debug")
    object EnergyProfiler  : Screen("energy_profiler")
    object SensorSimulator : Screen("sensor_simulator")
    object TestRunner      : Screen("test_runner")
    object AdvancedRefactor: Screen("advanced_refactor/{projectPath}") {
        fun withPath(p: String) = "advanced_refactor/${URLEncoder.encode(p, "UTF-8")}"
        val empty = "advanced_refactor/${URLEncoder.encode("", "UTF-8")}"
    }
    object PlayConsole     : Screen("play_console")
    object XmlToCompose    : Screen("xml_to_compose")
    object RemoteBuild     : Screen("remote_build/{projectPath}") {
        fun withPath(p: String) = "remote_build/${URLEncoder.encode(p, "UTF-8")}"
        val empty = "remote_build/${URLEncoder.encode("", "UTF-8")}"
    }
    object DeviceFarm      : Screen("device_farm")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Projects.route) {
        composable(Screen.Projects.route)   { ProjectsScreen(navController) }
        composable(Screen.NewProject.route) { NewProjectScreen(navController) }

        composable(Screen.Workspace.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { back -> WorkspaceScreen(navController, back.arguments!!.getLong("projectId")) }

        composable(Screen.SdkManager.route) { SdkManagerScreen(navController) }
        composable(Screen.Settings.route)   { SettingsScreen(navController) }
        composable(Screen.Logcat.route)     { LogcatScreen(navController) }

        composable(Screen.Git.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            GitScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.FindInProject.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            FindInProjectScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.ApkAnalyzer.route,
            arguments = listOf(navArgument("apkPath") { type = NavType.StringType })) { back ->
            ApkAnalyzerScreen(navController, decode(back.arguments?.getString("apkPath")))
        }
        composable(Screen.ResourceManager.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            ResourceManagerScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.Refactor.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            RefactorScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.SignedApkWizard.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            SignedApkWizardScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.ComposePreview.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })) { back ->
            ComposePreviewScreen(navController, decode(back.arguments?.getString("filePath")))
        }
        composable(Screen.ThemeEditor.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            ThemeEditorScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.DeviceSimulator.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })) { back ->
            DeviceSimulatorScreen(navController, decode(back.arguments?.getString("filePath")))
        }
        composable(Screen.HierarchyViewer.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })) { back ->
            HierarchyViewerScreen(navController, decode(back.arguments?.getString("filePath")))
        }
        composable(Screen.DatabaseInspector.route,
            arguments = listOf(navArgument("dbPath") { type = NavType.StringType })) { back ->
            DatabaseInspectorScreen(navController, decode(back.arguments?.getString("dbPath")))
        }
        composable(Screen.ModuleGraph.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            ModuleGraphScreen(navController, decode(back.arguments?.getString("projectPath")))
        }
        composable(Screen.DebugInspector.route,
            arguments = listOf(
                navArgument("filePath")    { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType }
            )) { back ->
            DebugInspectorScreen(
                navController,
                decode(back.arguments?.getString("filePath")),
                decode(back.arguments?.getString("projectPath"))
            )
        }

        // ── Nav Graph Editor ─────────────────────────────────────────────────
        composable(Screen.NavGraphEditor.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })) { back ->
            NavGraphEditorScreen(
                filePath      = decode(back.arguments?.getString("filePath") ?: ""),
                navController = navController
            )
        }

        // ── Drag-Drop Layout Editor ──────────────────────────────────────────
        composable(Screen.LayoutEditor.route,
            arguments = listOf(navArgument("outputPath") { type = NavType.StringType })) { back ->
            DragDropLayoutEditorScreen(
                outputXmlPath = decode(back.arguments?.getString("outputPath") ?: "")
            )
        }

        // ── Build Profiler ───────────────────────────────────────────────────
        composable(Screen.Profiler.route) {
            ProfilerScreen(navController = navController, profiler = MainApplication.profiler)
        }

        // ═══════════════════════════════════════════════════════════════════════
        // BLOCOS AVANÇADOS (10 novos)
        // ═══════════════════════════════════════════════════════════════════════

        // Bloco 1 — LLDB Native Debugger
        composable(Screen.NativeDebug.route) {
            NativeDebugScreen(navController)
        }

        // Bloco 2 — Energy Profiler
        composable(Screen.EnergyProfiler.route) {
            EnergyProfilerScreen(navController)
        }

        // Bloco 3 — Sensor Simulator
        composable(Screen.SensorSimulator.route) {
            SensorSimulatorScreen(navController)
        }

        // Bloco 4 — Test Runner (Bloco 5 re-enumerado)
        composable(Screen.TestRunner.route) {
            TestRunnerScreen(navController)
        }

        // Bloco 5 — Advanced Refactoring (Semantic Analyzer)
        composable(Screen.AdvancedRefactor.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            AdvancedRefactorScreen(navController, decode(back.arguments?.getString("projectPath") ?: ""))
        }

        // Bloco 6 — Play Console
        composable(Screen.PlayConsole.route) {
            PlayConsoleScreen(navController)
        }

        // Bloco 7 — XML → Compose Converter
        composable(Screen.XmlToCompose.route) {
            XmlToComposeScreen(navController)
        }

        // Bloco 8 — Remote Build
        composable(Screen.RemoteBuild.route,
            arguments = listOf(navArgument("projectPath") { type = NavType.StringType })) { back ->
            RemoteBuildScreen(navController, decode(back.arguments?.getString("projectPath") ?: ""))
        }

        // Bloco 9 — Virtual Device Farm
        composable(Screen.DeviceFarm.route) {
            DeviceFarmScreen(navController)
        }
    }
}

private fun decode(s: String?) = URLDecoder.decode(s ?: "", "UTF-8")
