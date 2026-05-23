package com.androidstudiomobile.data.model
data class OpenFile(val path: String, val name: String, val content: String, val language: String = "text", val isModified: Boolean = false)