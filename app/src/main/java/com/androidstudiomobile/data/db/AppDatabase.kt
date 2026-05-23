package com.androidstudiomobile.data.db
import androidx.room.Database
import androidx.room.RoomDatabase
import com.androidstudiomobile.data.model.Project
import com.androidstudiomobile.data.model.Snippet
@Database(entities = [Project::class, Snippet::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}