package com.androidstudiomobile.sensors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

data class GpsCoord(val lat: Double = 37.4219, val lon: Double = -122.0840, val alt: Double = 0.0)

class SensorSimulator {

    /** Send geo fix to emulator ADB console (port 5554 by default) */
    suspend fun sendGeoFix(lat: Double, lon: Double, alt: Double, adbPort: Int = 5554): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket("127.0.0.1", adbPort).use { sock ->
                    sock.getInputStream().bufferedReader().readLine() // greeting
                    PrintWriter(sock.getOutputStream(), true).println("geo fix $lon $lat $alt")
                    true
                }
            } catch (e: Exception) { false }
        }

    /** Inject mock location broadcast (same process) */
    suspend fun injectMockLocation(lat: Double, lon: Double, alt: Double): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c",
                    "am broadcast -a com.androidstudiomobile.MOCK_LOCATION " +
                    "--ef lat $lat --ef lon $lon --ef alt $alt")).waitFor() == 0
            } catch (_: Exception) { false }
        }

    /** Generic sensor broadcast for Xposed/LSPosed mock injection */
    suspend fun injectSensorValue(sensorType: Int, values: FloatArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val vals = values.joinToString(",")
                Runtime.getRuntime().exec(arrayOf("sh", "-c",
                    "am broadcast -a com.androidstudiomobile.MOCK_SENSOR " +
                    "--ei type $sensorType --es values $vals")).waitFor() == 0
            } catch (_: Exception) { false }
        }

    val locationPresets = listOf(
        "New York"    to GpsCoord(40.7128,  -74.0060,  10.0),
        "São Paulo"   to GpsCoord(-23.5505, -46.6333,  760.0),
        "London"      to GpsCoord(51.5074,  -0.1278,   11.0),
        "Tokyo"       to GpsCoord(35.6762,  139.6503,  40.0),
        "Sydney"      to GpsCoord(-33.8688, 151.2093,  58.0),
        "Paris"       to GpsCoord(48.8566,   2.3522,   35.0),
        "Dubai"       to GpsCoord(25.2048,   55.2708,   8.0),
        "Los Angeles" to GpsCoord(34.0522, -118.2437,  86.0)
    )
}
