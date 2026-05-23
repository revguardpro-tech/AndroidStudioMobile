package com.androidstudiomobile.data.db
import androidx.room.*
import com.androidstudiomobile.data.model.Project
import kotlinx.coroutines.flow.Flow
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpened DESC")
    fun getAllProjects(): Flow<List<Project>>
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): Project?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project): Long
    @Update suspend fun update(project: Project)
    @Delete suspend fun delete(project: Project)
    @Query("UPDATE projects SET lastOpened = :time WHERE id = :id")
    suspend fun updateLastOpened(id: Long, time: Long)
    @Query("UPDATE projects SET buildStatus = :status WHERE id = :id")
    suspend fun updateBuildStatus(id: Long, status: String)
}