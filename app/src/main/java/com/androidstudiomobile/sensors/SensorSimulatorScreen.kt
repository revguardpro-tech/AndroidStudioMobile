package com.androidstudiomobile.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

class SensorViewModel : ViewModel() {
    val sim = SensorSimulator()
    var lat by mutableStateOf("37.4219"); var lon by mutableStateOf("-122.0840"); var alt by mutableStateOf("0.0")
    var adbPort by mutableStateOf("5554")
    var accelX by mutableFloatStateOf(0f); var accelY by mutableFloatStateOf(9.81f); var accelZ by mutableFloatStateOf(0f)
    var gyroX  by mutableFloatStateOf(0f); var gyroY  by mutableFloatStateOf(0f);   var gyroZ  by mutableFloatStateOf(0f)
    var light  by mutableFloatStateOf(500f); var temp  by mutableFloatStateOf(25f)
    var pressure by mutableFloatStateOf(1013.25f); var humidity by mutableFloatStateOf(50f)
    var status by mutableStateOf("Ready")

    fun sendGps() = viewModelScope.launch {
        val ok = sim.sendGeoFix(lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0, alt.toDoubleOrNull() ?: 0.0, adbPort.toIntOrNull() ?: 5554)
        status = if (ok) "GPS sent: $lat, $lon" else "GPS error — check ADB port"
    }
    fun injectMock() = viewModelScope.launch {
        val ok = sim.injectMockLocation(lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0, alt.toDoubleOrNull() ?: 0.0)
        status = if (ok) "Mock location injected" else "Injection failed"
    }
    fun preset(p: GpsCoord) { lat = p.lat.toString(); lon = p.lon.toString(); alt = p.alt.toString() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSimulatorScreen(navController: NavController) {
    val vm: SensorViewModel = viewModel()

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Text("Sensor Simulator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(vm.status, color = Color.Gray, fontSize = 11.sp)
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // GPS
            item {
                SensorCard("GPS / Location", Color(0xFF4CAF50)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoordField("Lat",  vm.lat,  Modifier.weight(1f)) { vm.lat  = it }
                        CoordField("Lon",  vm.lon,  Modifier.weight(1f)) { vm.lon  = it }
                        CoordField("Alt m",vm.alt,  Modifier.weight(1f)) { vm.alt  = it }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ vm.sendGps() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("geo fix (ADB)", fontSize = 11.sp) }
                        Button({ vm.injectMock() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))) { Text("Mock inject", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.sim.locationPresets.take(4).forEach { (name, coord) ->
                            OutlinedButton({ vm.preset(coord) }, contentPadding = PaddingValues(6.dp)) { Text(name, color = Color.Gray, fontSize = 10.sp) }
                        }
                    }
                }
            }
            // Accelerometer
            item {
                SensorCard("Accelerometer (m/s²)", Color(0xFFFF9800)) {
                    SensorSlider("X", vm.accelX, -20f, 20f) { vm.accelX = it }
                    SensorSlider("Y", vm.accelY, -20f, 20f) { vm.accelY = it }
                    SensorSlider("Z", vm.accelZ, -20f, 20f) { vm.accelZ = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Portrait" to (0f to 9.81f to 0f), "Landscape" to (9.81f to 0f to 0f), "Face up" to (0f to 0f to 9.81f)).forEach { (lbl, vals) ->
                            OutlinedButton({ vm.accelX = vals.first.first; vm.accelY = vals.first.second; vm.accelZ = vals.second }, contentPadding = PaddingValues(6.dp)) { Text(lbl, color = Color.Gray, fontSize = 10.sp) }
                        }
                    }
                }
            }
            // Gyroscope
            item {
                SensorCard("Gyroscope (rad/s)", Color(0xFF9C27B0)) {
                    SensorSlider("X", vm.gyroX, -10f, 10f) { vm.gyroX = it }
                    SensorSlider("Y", vm.gyroY, -10f, 10f) { vm.gyroY = it }
                    SensorSlider("Z", vm.gyroZ, -10f, 10f) { vm.gyroZ = it }
                }
            }
            // Environment
            item {
                SensorCard("Environment", Color(0xFF00BCD4)) {
                    SensorSlider("Light (lux)",    vm.light,    0f, 100000f) { vm.light    = it }
                    SensorSlider("Temp (°C)",      vm.temp,     -40f, 85f)  { vm.temp     = it }
                    SensorSlider("Pressure (hPa)", vm.pressure, 300f, 1100f){ vm.pressure = it }
                    SensorSlider("Humidity (%)",   vm.humidity, 0f, 100f)   { vm.humidity = it }
                }
            }
            item {
                SensorCard("ADB Console Config", Color.Gray) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Emulator port:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                        OutlinedTextField(vm.adbPort, { vm.adbPort = it }, Modifier.width(90.dp), singleLine = true, colors = studioColors())
                    }
                }
            }
        }
    }
}

@Composable fun SensorCard(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF252526), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp))); Spacer(Modifier.width(6.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp)); content()
        }
    }
}

@Composable fun SensorSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(90.dp))
        Slider(value, onChange, valueRange = min..max, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color(0xFF007ACC), activeTrackColor = Color(0xFF007ACC)))
        Text("%.2f".format(value), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(56.dp).padding(start = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CoordField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, modifier, label = { Text(label, color = Color.Gray, fontSize = 9.sp) }, singleLine = true, colors = studioColors())

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun studioColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF007ACC), unfocusedBorderColor = Color.Gray)
