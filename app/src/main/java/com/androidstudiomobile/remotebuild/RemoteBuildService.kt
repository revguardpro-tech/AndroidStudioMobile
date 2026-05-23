package com.androidstudiomobile.remotebuild

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class BuildStatus { QUEUED, IN_PROGRESS, SUCCESS, FAILURE, CANCELLED, UNKNOWN }
enum class EndpointType { GITHUB_ACTIONS, CUSTOM_SERVER }

data class BuildLog(val message: String, val level: String = "INFO", val ts: Long = System.currentTimeMillis())
data class RemoteBuildConfig(
    val type: EndpointType = EndpointType.GITHUB_ACTIONS,
    val githubToken: String = "", val githubOwner: String = "",
    val githubRepo: String = "", val githubWorkflow: String = "build.yml",
    val customUrl: String = "", val customKey: String = "",
    val gradleTask: String = "assembleDebug"
)

class RemoteBuildService {

    companion object { private const val TAG = "RemoteBuildService"; private const val GH = "https://api.github.com" }

    suspend fun zipProject(dir: File, out: File) = withContext(Dispatchers.IO) {
        ZipOutputStream(out.outputStream()).use { zip ->
            dir.walkTopDown().filter { it.isFile && !it.path.contains("/.git/") && !it.path.contains("/build/") && !it.name.endsWith(".class") }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                f.inputStream().copyTo(zip); zip.closeEntry()
            }
        }
    }

    fun triggerAndMonitor(config: RemoteBuildConfig, dir: File): Flow<Pair<BuildStatus, List<BuildLog>>> = flow {
        val logs = mutableListOf<BuildLog>(); emit(BuildStatus.QUEUED to logs.toList())
        val runId = when (config.type) {
            EndpointType.GITHUB_ACTIONS -> triggerGitHub(config, dir, logs)
            EndpointType.CUSTOM_SERVER  -> triggerCustom(config, dir, logs)
        } ?: run { logs += BuildLog("Failed to trigger build", "ERROR"); emit(BuildStatus.FAILURE to logs.toList()); return@flow }

        logs += BuildLog("Build triggered — run ID: $runId"); emit(BuildStatus.IN_PROGRESS to logs.toList())

        var status = BuildStatus.IN_PROGRESS; var attempts = 0
        while (status == BuildStatus.IN_PROGRESS && attempts < 120) {
            delay(5000)
            val (s, log, artifactUrl) = poll(config, runId, logs)
            status = s; logs += BuildLog(log)
            if (artifactUrl != null) logs += BuildLog("Artifact: $artifactUrl", "SUCCESS")
            emit(status to logs.toList()); attempts++
        }
        if (attempts >= 120) { logs += BuildLog("Timed out", "WARN"); emit(BuildStatus.FAILURE to logs.toList()) }
    }

    private suspend fun triggerGitHub(config: RemoteBuildConfig, dir: File, logs: MutableList<BuildLog>): String? =
        withContext(Dispatchers.IO) {
            try {
                logs += BuildLog("Zipping project...")
                val zip = File(dir.parent, "upload.zip"); zipProject(dir, zip)
                logs += BuildLog("Triggering ${config.githubWorkflow}...")
                post("$GH/repos/${config.githubOwner}/${config.githubRepo}/actions/workflows/${config.githubWorkflow}/dispatches",
                    """{"ref":"main","inputs":{"gradle_task":"${config.gradleTask}"}}""", config.githubToken)
                delay(3000)
                val r = JSONObject(get("$GH/repos/${config.githubOwner}/${config.githubRepo}/actions/runs?per_page=1", config.githubToken))
                r.getJSONArray("workflow_runs").takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
            } catch (e: Exception) { logs += BuildLog("Trigger error: ${e.message}", "ERROR"); null }
        }

    private suspend fun triggerCustom(config: RemoteBuildConfig, dir: File, logs: MutableList<BuildLog>): String? =
        withContext(Dispatchers.IO) {
            try {
                logs += BuildLog("Uploading to ${config.customUrl}...")
                val zip = File(dir.parent, "upload.zip"); zipProject(dir, zip)
                val boundary = "b${System.currentTimeMillis()}"
                val c = conn("${config.customUrl}/build"); c.requestMethod = "POST"
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                c.setRequestProperty("X-Api-Key", config.customKey); c.doOutput = true; c.readTimeout = 120_000
                c.outputStream.use { os ->
                    os.write("--$boundary\r\nContent-Disposition: form-data; name=\"project\"; filename=\"project.zip\"\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
                    zip.inputStream().copyTo(os); os.write("\r\n--$boundary--\r\n".toByteArray())
                }
                JSONObject(c.inputStream.bufferedReader().readText().also { c.disconnect() }).optString("build_id")
            } catch (e: Exception) { logs += BuildLog("Custom error: ${e.message}", "ERROR"); null }
        }

    private data class PollResult(val status: BuildStatus, val log: String, val artifactUrl: String?)

    private fun poll(config: RemoteBuildConfig, runId: String, logs: MutableList<BuildLog>): PollResult {
        return when (config.type) {
            EndpointType.GITHUB_ACTIONS -> try {
                val j = JSONObject(get("$GH/repos/${config.githubOwner}/${config.githubRepo}/actions/runs/$runId", config.githubToken))
                val status = j.optString("status"); val conclusion = j.optString("conclusion")
                val bs = when { status == "completed" && conclusion == "success" -> BuildStatus.SUCCESS; status == "completed" -> BuildStatus.FAILURE; else -> BuildStatus.IN_PROGRESS }
                var art: String? = null
                if (bs == BuildStatus.SUCCESS) runCatching {
                    val a = JSONObject(get("$GH/repos/${config.githubOwner}/${config.githubRepo}/actions/runs/$runId/artifacts", config.githubToken))
                    art = a.getJSONArray("artifacts").takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("archive_download_url")
                }
                PollResult(bs, "GitHub: $status/$conclusion", art)
            } catch (e: Exception) { PollResult(BuildStatus.IN_PROGRESS, "Poll error: ${e.message}", null) }
            EndpointType.CUSTOM_SERVER -> try {
                val j = JSONObject(get("${config.customUrl}/build/$runId/status", config.customKey))
                val s = j.optString("status")
                PollResult(when (s) { "success" -> BuildStatus.SUCCESS; "failure","error" -> BuildStatus.FAILURE; else -> BuildStatus.IN_PROGRESS },
                    j.optString("message", s), j.optString("artifact_url").takeIf { it.isNotBlank() })
            } catch (e: Exception) { PollResult(BuildStatus.IN_PROGRESS, "Poll error: ${e.message}", null) }
        }
    }

    suspend fun downloadArtifact(url: String, dest: File, token: String) = withContext(Dispatchers.IO) {
        val c = conn(url); c.setRequestProperty("Authorization", "Bearer $token"); c.readTimeout = 300_000
        c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }; c.disconnect()
    }

    private fun get(url: String, token: String = ""): String {
        val c = conn(url); c.setRequestProperty("Accept","application/vnd.github+json")
        if (token.isNotBlank()) c.setRequestProperty("Authorization","Bearer $token")
        return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }

    private fun post(url: String, body: String, token: String): String {
        val c = conn(url); c.requestMethod = "POST"; c.setRequestProperty("Accept","application/vnd.github+json")
        c.setRequestProperty("Content-Type","application/json")
        if (token.isNotBlank()) c.setRequestProperty("Authorization","Bearer $token")
        c.doOutput = true; OutputStreamWriter(c.outputStream).use { it.write(body) }
        return (try { c.inputStream } catch (_: Exception) { c.errorStream }).bufferedReader().readText().also { c.disconnect() }
    }

    private fun conn(url: String) = (URL(url).openConnection() as HttpURLConnection).also { it.connectTimeout = 15_000; it.readTimeout = 30_000 }
}
