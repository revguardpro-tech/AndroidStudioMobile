package com.androidstudiomobile.ui.viewmodel

import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DbColumn(val name: String, val type: String, val isPrimaryKey: Boolean, val isNullable: Boolean)
data class DbTable(val name: String, val columns: List<DbColumn>, val rowCount: Int, val createStatement: String)
data class DbInspectorState(
    val isLoading: Boolean = false,
    val dbPath: String = "",
    val tables: List<DbTable> = emptyList(),
    val queryResults: List<Map<String, Any?>> = emptyList(),
    val errorMessage: String? = null,
    val sqliteVersion: String = ""
)

class DatabaseInspectorViewModel : ViewModel() {
    private val _state = MutableStateFlow(DbInspectorState())
    val state: StateFlow<DbInspectorState> = _state.asStateFlow()
    private var db: SQLiteDatabase? = null

    fun openDatabase(path: String) {
        if (path.isBlank()) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (!file.exists()) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Arquivo não encontrado: $path") }
                        return@withContext
                    }
                    db?.close()
                    db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
                    val version = querySingle("SELECT sqlite_version()") ?: "?"
                    val tables = loadTables()
                    _state.update { it.copy(isLoading = false, dbPath = path, tables = tables, sqliteVersion = version, errorMessage = null) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Erro ao abrir banco: ${e.message}") }
                }
            }
        }
    }

    fun executeQuery(sql: String) {
        val database = db ?: run {
            _state.update { it.copy(errorMessage = "Nenhum banco aberto") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val results = mutableListOf<Map<String, Any?>>()
                    val cursor = database.rawQuery(sql, null)
                    cursor.use {
                        val cols = it.columnNames
                        while (it.moveToNext()) {
                            val row = mutableMapOf<String, Any?>()
                            cols.forEachIndexed { i, col ->
                                row[col] = when (it.getType(i)) {
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> it.getLong(i)
                                    android.database.Cursor.FIELD_TYPE_FLOAT   -> it.getDouble(i)
                                    android.database.Cursor.FIELD_TYPE_STRING  -> it.getString(i)
                                    android.database.Cursor.FIELD_TYPE_BLOB    -> "[BLOB]"
                                    else -> null
                                }
                            }
                            results += row
                        }
                    }
                    _state.update { it.copy(isLoading = false, queryResults = results) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Erro na query: ${e.message}") }
                }
            }
        }
    }

    fun refresh() {
        val path = _state.value.dbPath
        if (path.isNotBlank()) openDatabase(path)
    }

    private fun loadTables(): List<DbTable> {
        val database = db ?: return emptyList()
        val tables = mutableListOf<DbTable>()
        val cursor = database.rawQuery(
            "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null
        )
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val sql = it.getString(1) ?: ""
                val columns = loadColumns(name)
                val rowCount = try {
                    val c = database.rawQuery("SELECT COUNT(*) FROM ${name.sqlEscape()}", null)
                    c.use { rc -> if (rc.moveToFirst()) rc.getInt(0) else 0 }
                } catch (_: Exception) { 0 }
                tables += DbTable(name, columns, rowCount, sql)
            }
        }
        return tables
    }

    private fun loadColumns(tableName: String): List<DbColumn> {
        val database = db ?: return emptyList()
        val columns = mutableListOf<DbColumn>()
        try {
            val cursor = database.rawQuery("PRAGMA table_info(${tableName.sqlEscape()})", null)
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow("name"))
                    val type = it.getString(it.getColumnIndexOrThrow("type"))
                    val notNull = it.getInt(it.getColumnIndexOrThrow("notnull")) == 1
                    val pk = it.getInt(it.getColumnIndexOrThrow("pk")) > 0
                    columns += DbColumn(name, type, pk, !notNull)
                }
            }
        } catch (_: Exception) {}
        return columns
    }

    private fun querySingle(sql: String): String? {
        return try {
            val cursor = db?.rawQuery(sql, null)
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun String.sqlEscape() = "\"${replace("\"", "\"\"")}\" "

    override fun onCleared() {
        db?.close()
        super.onCleared()
    }
}
