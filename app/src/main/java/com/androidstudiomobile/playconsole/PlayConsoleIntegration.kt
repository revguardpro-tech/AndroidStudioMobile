package com.androidstudiomobile.playconsole

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class PlayTrack(val name: String, val status: String, val versionCodes: List<Long>)
data class AppEdit(val id: String, val expiryTime: String)

class PlayConsoleIntegration(private val context: Context) {

    companion object {
        private const val TAG = "PlayConsoleIntegration"
        private const val OAUTH = "https://accounts.google.com/o/oauth2"
        private const val API   = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
        private const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"
        private const val REDIRECT = "com.androidstudiomobile:/oauth2callback"
    }

    private var accessToken  = ""
    private var refreshToken = ""
    var clientId     = ""; var clientSecret = ""

    fun authUrl(): String = Uri.parse("$OAUTH/auth").buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", REDIRECT)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("scope", SCOPE)
        .appendQueryParameter("access_type", "offline")
        .appendQueryParameter("prompt", "consent")
        .build().toString()

    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(post("$OAUTH/token",
                "code=$code&client_id=$clientId&client_secret=$clientSecret&redirect_uri=$REDIRECT&grant_type=authorization_code",
                "application/x-www-form-urlencoded"))
            accessToken = j.optString("access_token"); refreshToken = j.optString("refresh_token")
            save(); accessToken.isNotBlank()
        } catch (e: Exception) { Log.e(TAG, "exchangeCode", e); false }
    }

    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) return@withContext false
        try {
            val j = JSONObject(post("$OAUTH/token",
                "refresh_token=$refreshToken&client_id=$clientId&client_secret=$clientSecret&grant_type=refresh_token",
                "application/x-www-form-urlencoded"))
            accessToken = j.optString("access_token"); save(); true
        } catch (e: Exception) { false }
    }

    fun loadTokens(): Boolean {
        val p = prefs(); accessToken = p.getString("at", "") ?: ""; refreshToken = p.getString("rt", "") ?: ""
        clientId = p.getString("cid", "") ?: ""; clientSecret = p.getString("cs", "") ?: ""
        return accessToken.isNotBlank()
    }

    fun saveCredentials(cid: String, cs: String) { clientId = cid; clientSecret = cs; prefs().edit().putString("cid", cid).putString("cs", cs).apply() }
    fun clearTokens() { accessToken = ""; refreshToken = ""; prefs().edit().clear().apply() }

    suspend fun createEdit(pkg: String): AppEdit = withContext(Dispatchers.IO) {
        val j = JSONObject(apiPost("$API/$pkg/edits", "{}")); AppEdit(j.getString("id"), j.getString("expiryTimeSeconds"))
    }

    suspend fun commitEdit(pkg: String, id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { apiPost("$API/$pkg/edits/$id:commit", ""); true }.getOrDefault(false)
    }

    suspend fun deleteEdit(pkg: String, id: String) = withContext(Dispatchers.IO) {
        runCatching { apiDelete("$API/$pkg/edits/$id") }
    }

    suspend fun uploadApk(pkg: String, editId: String, file: File): Long = withContext(Dispatchers.IO) {
        JSONObject(upload("https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$pkg/edits/$editId/apks?uploadType=media",
            file, "application/vnd.android.package-archive")).getLong("versionCode")
    }

    suspend fun uploadAab(pkg: String, editId: String, file: File): Long = withContext(Dispatchers.IO) {
        JSONObject(upload("https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$pkg/edits/$editId/bundles?uploadType=media",
            file, "application/octet-stream")).getLong("versionCode")
    }

    suspend fun listTracks(pkg: String, editId: String): List<PlayTrack> = withContext(Dispatchers.IO) {
        val arr = JSONObject(apiGet("$API/$pkg/edits/$editId/tracks")).optJSONArray("tracks") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i); val vcs = mutableListOf<Long>()
            t.optJSONArray("releases")?.let { rs -> for (j in 0 until rs.length()) rs.getJSONObject(j).optJSONArray("versionCodes")?.let { vc -> for (k in 0 until vc.length()) vcs.add(vc.getLong(k)) } }
            PlayTrack(t.getString("track"), t.optString("status","unknown"), vcs)
        }
    }

    suspend fun updateTrack(pkg: String, editId: String, track: String, vcs: List<Long>,
        status: String, fraction: Double? = null, notes: List<Pair<String,String>> = emptyList()): Boolean =
        withContext(Dispatchers.IO) {
            val fp = if (fraction != null) ""","userFraction":$fraction""" else ""
            val ns = if (notes.isNotEmpty()) ""","releaseNotes":[${notes.joinToString(",") { """{"language":"${it.first}","text":"${it.second}"}""" }}]""" else ""
            runCatching {
                apiPut("$API/$pkg/edits/$editId/tracks/$track",
                    """{"track":"$track","releases":[{"versionCodes":[${vcs.joinToString()}],"status":"$status"$fp$ns}]}""")
                true
            }.getOrDefault(false)
        }

    private fun save() = prefs().edit().putString("at", accessToken).putString("rt", refreshToken).apply()
    private fun prefs() = context.getSharedPreferences("play_console", Context.MODE_PRIVATE)

    private fun apiGet(url: String): String { val c = conn(url); c.setRequestProperty("Authorization","Bearer $accessToken"); return c.inputStream.bufferedReader().readText().also { c.disconnect() } }
    private fun apiPost(url: String, body: String): String = post(url, body, "application/json", accessToken)
    private fun apiPut(url: String, body: String): String { val c = conn(url); c.requestMethod = "PUT"; c.setRequestProperty("Authorization","Bearer $accessToken"); c.setRequestProperty("Content-Type","application/json"); c.doOutput = true; OutputStreamWriter(c.outputStream).use { it.write(body) }; return c.inputStream.bufferedReader().readText().also { c.disconnect() } }
    private fun apiDelete(url: String) { val c = conn(url); c.requestMethod = "DELETE"; c.setRequestProperty("Authorization","Bearer $accessToken"); c.responseCode; c.disconnect() }

    private fun post(url: String, body: String, ct: String, token: String = ""): String {
        val c = conn(url); c.requestMethod = "POST"; c.setRequestProperty("Content-Type", ct)
        if (token.isNotBlank()) c.setRequestProperty("Authorization","Bearer $token")
        c.doOutput = true; OutputStreamWriter(c.outputStream).use { it.write(body) }
        return (try { c.inputStream } catch (_: Exception) { c.errorStream }).bufferedReader().readText().also { c.disconnect() }
    }

    private fun upload(url: String, file: File, mime: String): String {
        val c = conn(url); c.requestMethod = "POST"; c.setRequestProperty("Authorization","Bearer $accessToken")
        c.setRequestProperty("Content-Type", mime); c.setRequestProperty("Content-Length", file.length().toString())
        c.doOutput = true; c.readTimeout = 300_000; file.inputStream().use { i -> c.outputStream.use { o -> i.copyTo(o) } }
        return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }

    private fun conn(url: String) = (URL(url).openConnection() as HttpURLConnection).also { it.connectTimeout = 15_000; it.readTimeout = 30_000 }

    val isAuthenticated get() = accessToken.isNotBlank()
}
