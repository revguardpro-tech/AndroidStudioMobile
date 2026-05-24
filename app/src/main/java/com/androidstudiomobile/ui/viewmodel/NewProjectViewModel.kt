package com.androidstudiomobile.ui.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidstudiomobile.data.model.Project
import com.androidstudiomobile.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
data class NewProjectState(
    val name: String = "MyApp", val packageName: String = "com.example.myapp",
    val minSdk: Int = 26, val language: String = "Kotlin",
    val template: String = "EmptyActivity",
    val isCreating: Boolean = false, val error: String? = null, val createdProjectId: Long? = null
)
class NewProjectViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProjectRepository(app)
    private val _state = MutableStateFlow(NewProjectState())
    val state: StateFlow<NewProjectState> = _state.asStateFlow()
    fun updateName(v: String)        = _state.value.let { _state.value = it.copy(name = v) }
    fun updatePackage(v: String)     = _state.value.let { _state.value = it.copy(packageName = v) }
    fun updateMinSdk(v: Int)         = _state.value.let { _state.value = it.copy(minSdk = v) }
    fun updateLanguage(v: String)    = _state.value.let { _state.value = it.copy(language = v) }
    fun updateTemplate(v: String)    = _state.value.let { _state.value = it.copy(template = v) }
    fun createProject() {
        val s = _state.value
        _state.value = s.copy(isCreating = true, error = null)
        viewModelScope.launch {
            try {
                val base = File(getApplication<Application>().filesDir, "projects/${s.name}")
                base.mkdirs()
                scaffoldProject(base, s)
                val id = repo.insertProject(Project(name = s.name, path = base.absolutePath, packageName = s.packageName, language = s.language))
                _state.value = _state.value.copy(isCreating = false, createdProjectId = id)
            } catch (e: Exception) { _state.value = _state.value.copy(isCreating = false, error = e.message) }
        }
    }
    private fun scaffoldProject(base: File, s: NewProjectState) {
        val pkgPath = s.packageName.replace(".", "/")
        File(base, "app/src/main/java/$pkgPath").mkdirs()
        File(base, "app/src/main/res/layout").mkdirs()
        File(base, "app/src/main/res/values").mkdirs()
        File(base, "app/src/main/java/$pkgPath/MainActivity.kt").writeText("""
            package ${s.packageName}
            
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.Text
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent { Text("Hello, ${s.name}!") }
                }
            }
        """.trimIndent())
        File(base, "app/src/main/AndroidManifest.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?><manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="${s.name}" android:theme="@style/Theme.Material3.DayNight"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>""")
        File(base, "app/src/main/res/values/strings.xml").writeText("""<?xml version="1.0" encoding="utf-8"?><resources><string name="app_name">${s.name}</string></resources>""")
    }
}
