package com.androidstudiomobile.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prefix: String,
    val body: String,
    val description: String = "",
    val language: String = "kotlin"
)