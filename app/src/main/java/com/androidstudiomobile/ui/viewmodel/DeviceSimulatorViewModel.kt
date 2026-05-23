package com.androidstudiomobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.androidstudiomobile.ui.screens.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceSimulatorState(
    val availableDevices: List<DeviceProfile> = DeviceSimulatorViewModel.allDevices,
    val selectedDevice: DeviceProfile? = DeviceSimulatorViewModel.allDevices.first(),
    val isLandscape: Boolean = false,
    val currentFile: String = ""
)

class DeviceSimulatorViewModel : ViewModel() {
    private val _state = MutableStateFlow(DeviceSimulatorState())
    val state: StateFlow<DeviceSimulatorState> = _state.asStateFlow()

    fun loadFile(filePath: String) = _state.update { it.copy(currentFile = filePath) }
    fun selectDevice(device: DeviceProfile) = _state.update { it.copy(selectedDevice = device) }
    fun toggleOrientation() = _state.update { it.copy(isLandscape = !it.isLandscape) }

    companion object {
        val allDevices = listOf(
            DeviceProfile("Pixel 6",           1080, 2400, 411, 6.4f, 33),
            DeviceProfile("Pixel 7 Pro",       1440, 3120, 512, 6.7f, 33),
            DeviceProfile("Pixel 8",           1080, 2400, 428, 6.2f, 34),
            DeviceProfile("Pixel 9 Pro XL",    1344, 2992, 486, 6.8f, 35),
            DeviceProfile("Samsung S24",       1080, 2340, 416, 6.2f, 34),
            DeviceProfile("Samsung S24 Ultra", 1440, 3088, 505, 6.8f, 34),
            DeviceProfile("Samsung A54",       1080, 2340, 390, 6.4f, 33),
            DeviceProfile("Pixel 4a",          1080, 2340, 443, 5.8f, 30),
            DeviceProfile("Pixel C Tablet",    2048, 1536, 308, 9.9f, 23),
            DeviceProfile("Pixel Tablet",      2560, 1600, 320, 10.95f, 33),
            DeviceProfile("Samsung Tab S8",    2560, 1600, 274, 11.0f, 32),
            DeviceProfile("Small Phone",        720, 1280, 320, 4.7f, 26),
            DeviceProfile("Large Phone",       1440, 3040, 550, 6.9f, 33),
            DeviceProfile("Foldable (open)",   2208, 1768, 373, 7.6f, 33),
            DeviceProfile("Foldable (closed)", 1080, 2640, 388, 6.2f, 33),
            DeviceProfile("Android TV 1080p",  1920, 1080, 320, 55.0f, 33),
            DeviceProfile("WearOS Round",       450,  450, 320, 1.4f, 30)
        )
    }
}
