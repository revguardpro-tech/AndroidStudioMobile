package com.androidstudiomobile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TestResult(
    val testName: String,
    val className: String,
    val passed: Boolean,
    val duration: Long,
    val errorMessage: String? = null
)

data class TestSuiteResult(
    val suiteName: String,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val duration: Long,
    val results: List<TestResult> = emptyList()
)

class TestRunnerViewModel(app: Application) : AndroidViewModel(app) {
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestSuiteResult>>(emptyList())
    val testResults: StateFlow<List<TestSuiteResult>> = _testResults.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun runUnitTests(projectPath: String) {
        viewModelScope.launch {
            _isRunning.value = true
            _progress.value = 0f
            
            try {
                val results = executeTests(projectPath)
                _testResults.value = results
                _progress.value = 1f
            } catch (e: Exception) {
                _testResults.value = emptyList()
            } finally {
                _isRunning.value = false
            }
        }
    }

    private suspend fun executeTests(projectPath: String): List<TestSuiteResult> = 
        withContext(Dispatchers.IO) {
            val results = mutableListOf<TestSuiteResult>()
            
            try {
                val process = ProcessBuilder(
                    "sh", "-c",
                    "cd \"$projectPath\" && ./gradlew testDebugUnitTest --quiet 2>&1 || true"
                ).redirectErrorStream(true).start()

                var totalTests = 0
                var passedTests = 0
                val suiteResults = mutableListOf<TestResult>()

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        when {
                            line.contains("PASSED") -> {
                                passedTests++
                                totalTests++
                            }
                            line.contains("FAILED") -> {
                                totalTests++
                            }
                        }
                    }
                }

                process.waitFor()

                results.add(TestSuiteResult(
                    suiteName = "Unit Tests",
                    totalTests = totalTests,
                    passedTests = passedTests,
                    failedTests = totalTests - passedTests,
                    duration = 0L,
                    results = suiteResults
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            results
        }

    fun clearResults() {
        _testResults.value = emptyList()
        _progress.value = 0f
    }
}
