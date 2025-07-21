package com.example.finalapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.widget.BoxInsetLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeartRate = findViewById(R.id.tv_heart_rate)
        tvOxygen = findViewById(R.id.tv_oxygen)
        tvStress = findViewById(R.id.tv_stress)
        tvSteps = findViewById(R.id.tv_steps)
        btnSend = findViewById(R.id.btn_send)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Verificar permisos antes de registrar sensores
        checkAndRequestPermissions()

        btnSend.setOnClickListener {
            sendData()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            registerSensors()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                registerSensors()
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permisos necesarios para el funcionamiento", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerSensors() {
        // Registrar sensor de ritmo cardíaco
        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null) {
            val registered = sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI)
            println("Sensor de ritmo cardíaco registrado: $registered")
        } else {
            println("Sensor de ritmo cardíaco no disponible")
        }

        // Registrar sensor de pasos
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            val registered = sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            println("Sensor de pasos registrado: $registered")
        } else {
            println("Sensor de pasos no disponible")
        }

        // Para Galaxy Watch, probar diferentes tipos de sensores de oxígeno
        val oxygenSensors = listOf(
            65572, // Samsung SpO2
            65571, // Otro código Samsung
            Sensor.TYPE_HEART_RATE + 1000, // Variante
            21 // Otro código posible
        )

        var oxygenRegistered = false
        for (sensorType in oxygenSensors) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                if (registered) {
                    println("Sensor de oxígeno registrado - Tipo: $sensorType, Nombre: ${sensor.name}")
                    oxygenRegistered = true
                    break
                }
            }
        }

        if (!oxygenRegistered) {
            println("Ningún sensor de oxígeno disponible")
            // Simular datos de oxígeno para testing
            runOnUiThread {
                tvOxygen.text = "Oxigenación: No disponible"
            }
        }

        // Intentar sensores de estrés/HRV
        val stressSensors = listOf(
            65540, // Tipo estrés genérico
            65541, // Variante estrés
            31,    // HRV sensor
            65536 + 4 // Samsung stress
        )

        var stressRegistered = false
        for (sensorType in stressSensors) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                if (registered) {
                    println("Sensor de estrés registrado - Tipo: $sensorType, Nombre: ${sensor.name}")
                    stressRegistered = true
                    break
                }
            }
        }

        if (!stressRegistered) {
            println("Ningún sensor de estrés disponible")
            runOnUiThread {
                tvStress.text = "Estrés: No disponible"
            }
        }

        // Mostrar todos los sensores disponibles para debug
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        println("=== TODOS LOS SENSORES DISPONIBLES ===")
        for (sensor in allSensors) {
            println("ID: ${sensor.type}, Nombre: ${sensor.name}, Vendor: ${sensor.vendor}, Versión: ${sensor.version}")
        }
        println("=== FIN LISTA SENSORES ===")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                if (event.values[0] > 0) {
                    heartRate = event.values[0]
                    runOnUiThread {
                        tvHeartRate.text = "Ritmo cardíaco: ${heartRate.toInt()} bpm"
                    }
                    println("Ritmo cardíaco actualizado: $heartRate")
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                steps = event.values[0]
                runOnUiThread {
                    tvSteps.text = "Pasos: ${steps.toInt()}"
                }
                println("Pasos actualizados: $steps")
            }
            65572, 65571, 21 -> { // Sensores de SpO2
                if (event.values[0] > 0 && event.values[0] <= 100) {
                    oxygen = event.values[0]
                    runOnUiThread {
                        tvOxygen.text = "Oxigenación: ${oxygen.toInt()}%"
                    }
                    println("SpO2 actualizado: $oxygen (Sensor tipo: ${event.sensor.type})")
                }
            }
            65540, 65541, 31 -> { // Sensores de estrés/HRV
                stress = event.values[0]
                runOnUiThread {
                    // Normalizar valor de estrés a 0-100
                    val stressLevel = when {
                        stress <= 25 -> "Bajo"
                        stress <= 50 -> "Normal"
                        stress <= 75 -> "Alto"
                        else -> "Muy Alto"
                    }
                    tvStress.text = "Estrés: $stressLevel (${stress.toInt()})"
                }
                println("Estrés actualizado: $stress (Sensor tipo: ${event.sensor.type})")
            }
            else -> {
                // Log para sensores desconocidos
                println("Sensor desconocido - Tipo: ${event.sensor.type}, Nombre: ${event.sensor.name}, Valor: ${event.values[0]}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        println("Precisión del sensor ${sensor?.name} cambiada a: $accuracy")
    }

    private fun isValidSensorValue(value: Float): Boolean {
        return !value.isNaN() && !value.isInfinite() && value > 0
    }

    private fun sendData() {
        // Validar que hay datos para enviar
        if (!isValidSensorValue(heartRate) && !isValidSensorValue(steps)) {
            Toast.makeText(this, "No hay datos de sensores para enviar", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar oxígeno y estrés
        val validOxygen = isValidSensorValue(oxygen)
        val validStress = isValidSensorValue(stress)

        runOnUiThread {
            if (!validOxygen) {
                tvOxygen.text = "Oxigenación: No disponible en este dispositivo"
            }
            if (!validStress) {
                tvStress.text = "Estrés: No disponible en este dispositivo"
            }
        }

        val json = JSONObject()
        json.put("heart_rate", if (isValidSensorValue(heartRate)) heartRate else 0)
        json.put("steps", if (isValidSensorValue(steps)) steps else 0)
        // Solo incluir oxígeno y estrés si son válidos
        if (validOxygen) json.put("oxygen", oxygen)
        if (validStress) json.put("stress", stress)
        json.put("timestamp", System.currentTimeMillis())

        // Mostrar lo que se va a enviar
        runOnUiThread {
            Toast.makeText(this, "Enviando datos...", Toast.LENGTH_SHORT).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("http://3.145.62.106:9000/upload/")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "WearOS-App/1.0")
                conn.doOutput = true
                conn.doInput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                // Escribir datos
                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                val responseMessage = conn.responseMessage

                println("Respuesta del servidor: $responseCode - $responseMessage")
                println("Datos enviados: ${json.toString()}")

                withContext(Dispatchers.Main) {
                    when (responseCode) {
                        HttpURLConnection.HTTP_OK,
                        HttpURLConnection.HTTP_CREATED,
                        HttpURLConnection.HTTP_ACCEPTED -> {
                            Toast.makeText(this@MainActivity, "✓ Datos enviados", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Error servidor: $responseCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            } catch (e: java.net.UnknownHostException) {
                println("Error de red: No se puede conectar al servidor")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: Sin conexión a Internet", Toast.LENGTH_LONG).show()
                }
            } catch (e: java.net.SocketTimeoutException) {
                println("Error: Timeout de conexión")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: Timeout de conexión", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                println("Error general al enviar datos: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    conn?.disconnect()
                } catch (e: Exception) {
                    println("Error al cerrar conexión: ${e.message}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-registrar sensores cuando la app vuelve al primer plano
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
            registerSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar sensores para ahorrar batería
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    // NOTA IMPORTANTE:
    // Para obtener datos reales de oxígeno y estrés en Galaxy Watch 5 con Wear OS 5,
    // es necesario usar el Samsung Health SDK y pedir permisos especiales.
    // SensorManager no garantiza acceso a estos sensores en todos los relojes.
    // Consulta la documentación oficial de Samsung para más detalles.
}