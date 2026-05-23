package com.androidstudiomobile.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

data class GitState(
    val branch: String = "main", val status: String = "", val log: List<String> = emptyList(),
    val isBusy: Boolean = false, val error: String? = null, val success: String? = null
)

class GitViewModel : ViewModel() {
    private val _state = MutableStateFlow(GitState())
    val state: StateFlow<GitState> = _state.asStateFlow()
    private var git: Git? = null

    fun openRepo(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                git = Git.open(File(path))
                val branch = git!!.repository.branch
                val st = git!!.status().call()
                val log = git!!.log().setMaxCount(20).call().map { "${it.name().take(7)} — ${it.shortMessage}" }
                _state.update { it.copy(branch = branch, log = log,
                    status = "Modified: ${st.modified.size}, Added: ${st.added.size}, Untracked: ${st.untracked.size}") }
            } catch (e: Exception) { _state.update { it.copy(error = e.message) } }
        }
    }

    fun commit(message: String, authorName: String = "Android Studio Mobile", email: String = "asm@local") {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, error = null) }
            try {
                git?.add()?.addFilepattern(".")?.call()
                git?.commit()?.setMessage(message)?.setAuthor(authorName, email)?.call()
                _state.update { it.copy(isBusy = false, success = "Committed: $message") }
                git?.repository?.workTree?.absolutePath?.let { openRepo(it) }
            } catch (e: Exception) { _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun push(remote: String = "origin", username: String = "", token: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true) }
            try {
                val cred = UsernamePasswordCredentialsProvider(username, token)
                git?.push()?.setRemote(remote)?.setCredentialsProvider(cred)?.call()
                _state.update { it.copy(isBusy = false, success = "Pushed to $remote") }
            } catch (e: Exception) { _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun cloneRepo(url: String, dest: String, username: String = "", token: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true) }
            try {
                val builder = Git.cloneRepository().setURI(url).setDirectory(File(dest))
                if (token.isNotBlank()) builder.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                git = builder.call()
                _state.update { it.copy(isBusy = false, success = "Cloned to $dest") }
                openRepo(dest)
            } catch (e: Exception) { _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun dismissError()   = _state.update { it.copy(error = null) }
    fun dismissSuccess() = _state.update { it.copy(success = null) }
}