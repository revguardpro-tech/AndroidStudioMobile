package com.androidstudiomobile.git
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
object GitManager {
    fun openRepo(path: String): Git? = try { Git.open(File(path)) } catch (_: Exception) { null }
    fun initRepo(path: String): Git = Git.init().setDirectory(File(path)).call()
    fun isGitRepo(path: String): Boolean = File(path, ".git").exists()
    fun getBranch(git: Git): String = try { git.repository.branch } catch (_: Exception) { "unknown" }
}