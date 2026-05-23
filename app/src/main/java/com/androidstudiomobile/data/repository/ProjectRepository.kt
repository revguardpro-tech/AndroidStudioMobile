package com.androidstudiomobile.data.repository
import android.content.Context
import com.androidstudiomobile.MainApplication
import com.androidstudiomobile.data.model.Project
import kotlinx.coroutines.flow.Flow
class ProjectRepository(context: Context) {
    private val dao = MainApplication.db.projectDao()
    fun getAllProjects(): Flow<List<Project>> = dao.getAllProjects()
    suspend fun getProject(id: Long): Project? = dao.getById(id)
    suspend fun insertProject(project: Project): Long = dao.insert(project)
    suspend fun updateLastOpened(id: Long) = dao.updateLastOpened(id, System.currentTimeMillis())
    suspend fun updateBuildStatus(id: Long, status: String) = dao.updateBuildStatus(id, status)
    suspend fun deleteProject(project: Project) = dao.delete(project)
}