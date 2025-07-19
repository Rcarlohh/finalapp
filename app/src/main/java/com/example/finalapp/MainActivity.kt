package com.example.finalapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity(), SensorEventListener {
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 123
    }

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

        initViews()
        setupClickListener()
        checkAndRequestPermissions()
    }

    private fun initViews() {
        tvHeartRate = findViewById(R.id.tv_heart_rate)
        tvOxygen = findViewById(R.id.tv_oxygen)
        tvStress = findViewById(R.id.tv_stress)
        tvSteps = findViewById(R.id.tv_steps)
        btnSend = findViewById(R.id.btn_send)
    }

    private fun initSensors() {
        Log.d(TAG, "Iniciando sensores...")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        registerSensors()
        Toast.makeText(this, "Sensores iniciados", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListener() {
        btnSend.setOnClickListener {
            sendData()
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            // Verificar permiso de sensores del cuerpo
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BODY_SENSORS)
                Log.d(TAG, "Solicitando permiso BODY_SENSORS")
            }
            
            // Verificar permiso de sensores del cuerpo en segundo plano (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
                    Log.d(TAG, "Solicitando permiso BODY_SENSORS_BACKGROUND")
                }
            }
            
            if (permissions.isNotEmpty()) {
                Log.d(TAG, "Solicitando permisos: ${permissions.joinToString()}")
                Toast.makeText(this, "Solicitando permisos de sensores...", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                Log.d(TAG, "Todos los permisos ya están concedidos")
                initSensors()
            }
        } else {
            Log.d(TAG, "Android < 6.0, no se requieren permisos")
            initSensors()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Todos los permisos concedidos")
                    Toast.makeText(this, "¡Permisos concedidos! Iniciando sensores...", Toast.LENGTH_SHORT).show()
                    initSensors()
                } else {
                    Log.w(TAG, "Algunos permisos fueron denegados")
                    Toast.makeText(this, "Se requieren permisos para acceder a los sensores del reloj", Toast.LENGTH_LONG).show()
                    // Mostrar valores por defecto
                    runOnUiThread {
                        tvHeartRate.text = "Ritmo cardíaco: Sin permiso"
                        tvOxygen.text = "Oxigenación: Sin permiso"
                        tvStress.text = "Estrés: Sin permiso"
                        tvSteps.text = "Pasos: Sin permiso"
                    }
                }
            }
        }
    }

    private fun registerSensors() {
        // Registro del sensor de ritmo cardíaco
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Sensor de ritmo cardíaco registrado")
        } ?: Log.w(TAG, "Sensor de ritmo cardíaco no disponible")

        // Registro del contador de pasos
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Sensor de pasos registrado")
        } ?: Log.w(TAG, "Sensor de pasos no disponible")

        // Intentar registrar sensores no estándar (pueden no estar disponibles)
        try {
            // Oxigenación (no estándar)
            sensorManager.getDefaultSensor(65539)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Sensor de oxigenación registrado")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sensor de oxigenación no disponible: ${e.message}")
        }

        try {
            // Estrés (no estándar)
            sensorManager.getDefaultSensor(65540)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Sensor de estrés registrado")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sensor de estrés no disponible: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                heartRate = event.values[0]
                runOnUiThread {
                    tvHeartRate.text = "Ritmo cardíaco: ${heartRate.toInt()} bpm"
                }
                Log.d(TAG, "Ritmo cardíaco actualizado: $heartRate")
            }
            Sensor.TYPE_STEP_COUNTER -> {
                steps = event.values[0]
                runOnUiThread {
                    tvSteps.text = "Pasos: ${steps.toInt()}"
                }
                Log.d(TAG, "Pasos actualizados: $steps")
            }
            65539 -> { // Oxigenación
                oxygen = event.values[0]
                runOnUiThread {
                    tvOxygen.text = "Oxigenación: ${oxygen.toInt()}%"
                }
                Log.d(TAG, "Oxigenación actualizada: $oxygen")
            }
            65540 -> { // Estrés
                stress = event.values[0]
                runOnUiThread {
                    tvStress.text = "Estrés: ${stress.toInt()}"
                }
                Log.d(TAG, "Estrés actualizado: $stress")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Precisión del sensor cambiada: ${sensor?.name} - $accuracy")
    }

    private fun sendData() {
        val json = JSONObject().apply {
            put("heart_rate", heartRate.toDouble())
            put("oxygen", oxygen.toDouble())
            put("stress", stress.toDouble())
            put("steps", steps.toDouble())
            put("timestamp", System.currentTimeMillis())
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://3.145.62.106:9000/upload/")
                val conn = url.openConnection() as HttpURLConnection

                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "Respuesta del servidor: $responseCode")

                withContext(Dispatchers.Main) {
                    if (responseCode in 200..299) {
                        Toast.makeText(this@MainActivity, "Datos enviados correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Error al enviar datos: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }

                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Error enviando datos", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}