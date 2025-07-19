package com.example.finalapp

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.wear.widget.BoxInsetLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRate: Float = 0f
    private var oxygen: Float = 0f
    private var stress: Float = 0f
    private var steps: Float = 0f

    private lateinit var tvHeartRate: TextView
    private lateinit var tvOxygen: TextView
    private lateinit var tvStress: TextView
    private lateinit var tvSteps: TextView
    private lateinit var btnSend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeartRate = findViewById(R.id.tv_heart_rate)
        tvOxygen = findViewById(R.id.tv_oxygen)
        tvStress = findViewById(R.id.tv_stress)
        tvSteps = findViewById(R.id.tv_steps)
        btnSend = findViewById(R.id.btn_send)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        registerSensors()

        btnSend.setOnClickListener {
            sendData()
        }
    }

    private fun registerSensors() {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // Oxígeno y estrés pueden no estar disponibles en todos los relojes, pero intentamos:
        sensorManager.getDefaultSensor(65539)?.let { // TYPE_OXYGEN_SATURATION (no estándar)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(65540)?.let { // TYPE_STRESS_LEVEL (no estándar)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                heartRate = event.values[0]
                tvHeartRate.text = "Ritmo cardiaco: $heartRate"
            }
            Sensor.TYPE_STEP_COUNTER -> {
                steps = event.values[0]
                tvSteps.text = "Pasos: $steps"
            }
            65539 -> { // Oxigenación
                oxygen = event.values[0]
                tvOxygen.text = "Oxigenación: $oxygen"
            }
            65540 -> { // Estrés
                stress = event.values[0]
                tvStress.text = "Estrés: $stress"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendData() {
        val json = JSONObject()
        json.put("heart_rate", heartRate)
        json.put("oxygen", oxygen)
        json.put("stress", stress)
        json.put("steps", steps)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://3.145.62.106:9000/upload/")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(json.toString())
                writer.flush()
                writer.close()
                conn.inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
} 