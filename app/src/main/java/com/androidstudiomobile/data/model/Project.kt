package com.androidstudiomobile.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val packageName: String = "",
    val description: String = "",
    val lastOpened: Long = System.currentTimeMillis(),
    val buildStatus: String = "UNKNOWN",
    val language: String = "Kotlin"
)