package com.example.gyrotracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null

    // Sensor values
    private var rawYaw = 0f
    private var rawPitch = 0f
    private var rawRoll = 0f
    
    // Offset for reset center
    private var offsetX = 0f
    private var offsetY = 0f
    private var offsetZ = 0f

    private var lastTimestamp = 0L

    // State variables for UI
    private var isStreaming by mutableStateOf(false)
    private var pcIp by mutableStateOf("192.168.1.110")
    private var pcPort by mutableStateOf("4242")
    private var sensitivity by mutableStateOf(3.0f)
    
    private var invertYaw by mutableStateOf(false)
    private var invertPitch by mutableStateOf(false)
    private var invertRoll by mutableStateOf(false)

    private var displayYaw by mutableStateOf(0f)
    private var displayPitch by mutableStateOf(0f)
    private var displayRoll by mutableStateOf(0f)

    private val udpScope = CoroutineScope(Dispatchers.IO + Job())
    private var udpSocket: DatagramSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TrackerUI()
                }
            }
        }
    }

    @Composable
    fun TrackerUI() {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Native Gyro Tracker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SensorValue("Yaw", displayYaw, Color(0xFF60A5FA))
                        SensorValue("Pitch", displayPitch, Color(0xFFC084FC))
                        SensorValue("Roll", displayRoll, Color(0xFF34D399))
                    }
                }
            }

            OutlinedTextField(
                value = pcIp,
                onValueChange = { pcIp = it },
                label = { Text("PC IP Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = pcPort,
                onValueChange = { pcPort = it },
                label = { Text("UDP Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Sensitivity: ${"%.1f".format(sensitivity)}x", fontSize = 14.sp, color = Color.Gray)
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.5f..10f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AxisToggle("Inv Yaw", invertYaw) { invertYaw = it }
                AxisToggle("Inv Pitch", invertPitch) { invertPitch = it }
                AxisToggle("Inv Roll", invertRoll) { invertRoll = it }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { 
                        isStreaming = !isStreaming
                        if (isStreaming) startTracking() else stopTracking()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) Color(0xFFEF4444) else Color(0xFF2563EB)
                    )
                ) {
                    Text(if (isStreaming) "STOP" else "START", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { resetCenter() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                ) {
                    Text("RESET CENTER", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Format: OpenTrack UDP (6 floats, LE)",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
    }

    @Composable
    fun SensorValue(label: String, value: Float, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(value)}°", fontSize = 24.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun AxisToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, fontSize = 12.sp)
        }
    }

    private fun resetCenter() {
        offsetX = rawYaw
        offsetY = rawPitch
        offsetZ = rawRoll
    }

    private fun startTracking() {
        udpScope.launch {
            try {
                udpSocket = DatagramSocket()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
    }

    private fun stopTracking() {
        sensorManager.unregisterListener(this)
        udpSocket?.close()
        udpSocket = null
        lastTimestamp = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isStreaming) return

        if (lastTimestamp != 0L) {
            val dt = (event.timestamp - lastTimestamp) * 1e-9f
            
            // Integrate gyro rad/s to degrees
            rawYaw += Math.toDegrees(event.values[0].toDouble()).toFloat() * dt
            rawPitch += Math.toDegrees(event.values[1].toDouble()).toFloat() * dt
            rawRoll += Math.toDegrees(event.values[2].toDouble()).toFloat() * dt

            // Apply offset and sensitivity
            var outYaw = (rawYaw - offsetX) * sensitivity * (if (invertYaw) -1f else 1f)
            var outPitch = (rawPitch - offsetY) * sensitivity * (if (invertPitch) -1f else 1f)
            var outRoll = (rawRoll - offsetZ) * sensitivity * (if (invertRoll) -1f else 1f)

            // Update UI
            displayYaw = outYaw
            displayPitch = outPitch
            displayRoll = outRoll
            
            sendUdp(outYaw, outPitch, outRoll)
        }
        lastTimestamp = event.timestamp
    }

    private fun sendUdp(y: Float, p: Float, r: Float) {
        val socket = udpSocket ?: return
        udpScope.launch {
            try {
                val address = InetAddress.getByName(pcIp)
                val portInt = pcPort.toIntOrNull() ?: 4242
                
                val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putFloat(y)
                buffer.putFloat(p)
                buffer.putFloat(r)
                buffer.putFloat(0f)
                buffer.putFloat(0f)
                buffer.putFloat(0f)
                
                val packet = DatagramPacket(buffer.array(), buffer.limit(), address, portInt)
                socket.send(packet)
            } catch (e: Exception) {
                // Ignore network errors to keep tracking smooth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        udpScope.cancel()
    }
}
