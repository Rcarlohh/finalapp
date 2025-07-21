package com.example.finalapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager

    // Variables para los datos de sensores
    private var heartRate: Float = 0f
    private var oxygen: Float = 0f
    private var stress: Float = 0f
    private var steps: Float = 0f
    private var stepOffset: Float = 0f // Para calcular pasos relativos

    // UI Elements
    private lateinit var tvHeartRate: TextView
    private lateinit var tvOxygen: TextView
    private lateinit var tvStress: TextView
    private lateinit var tvSteps: TextView
    private lateinit var btnSend: Button

    // Sensores registrados
    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var oxygenSensor: Sensor? = null
    private var stressSensor: Sensor? = null

    private val PERMISSION_REQUEST_CODE = 123
    private val TAG = "WearHealthApp"

    // Handler para timeouts de sensores
    private val handler = Handler(Looper.getMainLooper())
    private val sensorTimeout = 10000L // 10 segundos timeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "=== INICIANDO APLICACIÓN ===")

        // Inicializar vistas
        initViews()

        // Inicializar sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Verificar permisos
        checkAndRequestPermissions()

        // Configurar botón de envío
        btnSend.setOnClickListener {
            sendDataToServer()
        }

        // Log de información del dispositivo
        logDeviceInfo()
    }

    private fun initViews() {
        tvHeartRate = findViewById(R.id.tv_heart_rate)
        tvOxygen = findViewById(R.id.tv_oxygen)
        tvStress = findViewById(R.id.tv_stress)
        tvSteps = findViewById(R.id.tv_steps)
        btnSend = findViewById(R.id.btn_send)

        // Establecer valores iniciales
        updateUI()
    }

    private fun logDeviceInfo() {
        Log.d(TAG, "Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d(TAG, "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        Log.d(TAG, "Wear OS detectado: ${packageManager.hasSystemFeature("android.hardware.type.watch")}")
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
                Log.d(TAG, "Permiso faltante: $permission")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Solicitando permisos: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "Todos los permisos concedidos")
            initializeSensors()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()

            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isEmpty()) {
                Log.d(TAG, "Todos los permisos concedidos")
                Toast.makeText(this, "✓ Permisos concedidos", Toast.LENGTH_SHORT).show()
                initializeSensors()
            } else {
                Log.w(TAG, "Permisos denegados: ${deniedPermissions.joinToString()}")
                Toast.makeText(this, "⚠ Algunos permisos fueron denegados", Toast.LENGTH_LONG).show()
                // Intentar inicializar sensores disponibles
                initializeSensors()
            }
        }
    }

    private fun initializeSensors() {
        Log.d(TAG, "=== INICIALIZANDO SENSORES ===")

        // Listar todos los sensores disponibles
        listAllAvailableSensors()

        // Intentar registrar sensores básicos
        registerHeartRateSensor()
        registerStepCounter()
        registerOxygenSensor()
        registerStressSensor()

        Log.d(TAG, "=== FIN INICIALIZACIÓN SENSORES ===")

        // Configurar timeout para sensores que no respondan
        handler.postDelayed({
            checkSensorStatus()
        }, sensorTimeout)
    }

    private fun listAllAvailableSensors() {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "SENSORES DISPONIBLES (${allSensors.size} total):")

        for (sensor in allSensors) {
            Log.d(TAG, "├─ Tipo: ${sensor.type}, Nombre: '${sensor.name}'")
            Log.d(TAG, "│  Vendor: '${sensor.vendor}', Versión: ${sensor.version}")
            Log.d(TAG, "│  Potencia: ${sensor.power}mA, Resolución: ${sensor.resolution}")
            Log.d(TAG, "└─ Rango máximo: ${sensor.maximumRange}")
        }
    }

    private fun registerHeartRateSensor() {
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor != null) {
            val success = sensorManager.registerListener(
                this,
                heartRateSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            Log.d(TAG, "Sensor ritmo cardíaco - Registrado: $success")
            Log.d(TAG, "  └─ ${heartRateSensor?.name} (${heartRateSensor?.vendor})")
        } else {
            Log.w(TAG, "❌ Sensor de ritmo cardíaco no disponible")
            runOnUiThread {
                tvHeartRate.text = "Ritmo cardíaco: No disponible"
            }
        }
    }

    private fun registerStepCounter() {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCounterSensor != null) {
            val success = sensorManager.registerListener(
                this,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            Log.d(TAG, "Sensor contador de pasos - Registrado: $success")
            Log.d(TAG, "  └─ ${stepCounterSensor?.name} (${stepCounterSensor?.vendor})")
        } else {
            Log.w(TAG, "❌ Sensor contador de pasos no disponible")
            // Intentar con step detector como alternativa
            val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            if (stepDetector != null) {
                val success = sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
                Log.d(TAG, "Sensor detector de pasos alternativo - Registrado: $success")
            } else {
                runOnUiThread {
                    tvSteps.text = "Pasos: No disponible"
                }
            }
        }
    }

    private fun registerOxygenSensor() {
        // Lista de tipos de sensores de SpO2 conocidos
        val oxygenSensorTypes = listOf(
            65572, // Samsung SpO2 (Galaxy Watch)
            65571, // Samsung SpO2 alternativo
            65574, // Otro código Samsung
            21,    // Código genérico SpO2
            22,    // Variante SpO2
            Sensor.TYPE_HEART_RATE + 1000 // Códigos derivados
        )

        for (sensorType in oxygenSensorTypes) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                if (success) {
                    oxygenSensor = sensor
                    Log.d(TAG, "Sensor SpO2 - Registrado: Tipo $sensorType")
                    Log.d(TAG, "  └─ ${sensor.name} (${sensor.vendor})")
                    break
                }
            }
        }

        if (oxygenSensor == null) {
            Log.w(TAG, "❌ Ningún sensor de SpO2 disponible")
            runOnUiThread {
                tvOxygen.text = "SpO2: No disponible"
            }
        }
    }

    private fun registerStressSensor() {
        // Lista de tipos de sensores de estrés/HRV conocidos
        val stressSensorTypes = listOf(
            65540, // Estrés genérico Samsung
            65541, // Estrés alternativo Samsung
            65542, // Variante estrés
            31,    // HRV sensor
            32,    // Variante HRV
            65536 + 4, // Samsung stress específico
            65536 + 5  // Otra variante Samsung
        )

        for (sensorType in stressSensorTypes) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                if (success) {
                    stressSensor = sensor
                    Log.d(TAG, "Sensor estrés - Registrado: Tipo $sensorType")
                    Log.d(TAG, "  └─ ${sensor.name} (${sensor.vendor})")
                    break
                }
            }
        }

        if (stressSensor == null) {
            Log.w(TAG, "❌ Ningún sensor de estrés disponible")
            runOnUiThread {
                tvStress.text = "Estrés: No disponible"
            }
        }
    }

    private fun checkSensorStatus() {
        Log.d(TAG, "=== VERIFICANDO ESTADO DE SENSORES ===")

        if (heartRate == 0f && heartRateSensor != null) {
            Log.w(TAG, "⚠ Sensor de ritmo cardíaco no ha enviado datos")
        }

        if (steps == 0f && stepCounterSensor != null) {
            Log.w(TAG, "⚠ Sensor de pasos no ha enviado datos")
        }

        // Simular datos para testing si no hay sensores reales
        if (heartRate == 0f && oxygen == 0f && stress == 0f && steps == 0f) {
            Log.d(TAG, "Generando datos simulados para testing...")
            simulateTestData()
        }
    }

    private fun simulateTestData() {
        // Solo para testing - generar datos aleatorios
        heartRate = (60..100).random().toFloat()
        oxygen = (95..100).random().toFloat()
        stress = (10..80).random().toFloat()
        steps = (100..5000).random().toFloat()

        Log.d(TAG, "Datos simulados generados")
        updateUI()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val sensorName = event.sensor?.name ?: "Desconocido"
        val sensorType = event.sensor?.type ?: -1

        when (sensorType) {
            Sensor.TYPE_HEART_RATE -> {
                if (event.values[0] > 0 && event.values[0] < 250) { // Rango válido
                    heartRate = event.values[0]
                    Log.d(TAG, "❤️ Ritmo cardíaco: $heartRate bpm")
                    runOnUiThread {
                        tvHeartRate.text = "❤️ ${heartRate.toInt()} bpm"
                        tvHeartRate.setTextColor(getColor(android.R.color.holo_red_light))
                    }
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val currentSteps = event.values[0]
                if (stepOffset == 0f) {
                    stepOffset = currentSteps // Primera lectura
                }
                steps = currentSteps - stepOffset
                Log.d(TAG, "👟 Pasos: $steps (total: $currentSteps)")
                runOnUiThread {
                    tvSteps.text = "👟 ${steps.toInt()} pasos"
                    tvSteps.setTextColor(getColor(android.R.color.holo_green_light))
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                steps++ // Incrementar contador manual
                Log.d(TAG, "👟 Paso detectado. Total: $steps")
                runOnUiThread {
                    tvSteps.text = "👟 ${steps.toInt()} pasos"
                    tvSteps.setTextColor(getColor(android.R.color.holo_green_light))
                }
            }

            in listOf(65572, 65571, 65574, 21, 22) -> { // Sensores SpO2
                if (event.values[0] > 70 && event.values[0] <= 100) { // Rango válido SpO2
                    oxygen = event.values[0]
                    Log.d(TAG, "🫁 SpO2: $oxygen% (Sensor: $sensorName)")
                    runOnUiThread {
                        tvOxygen.text = "🫁 ${oxygen.toInt()}%"
                        tvOxygen.setTextColor(getColor(android.R.color.holo_blue_light))
                    }
                }
            }

            in listOf(65540, 65541, 65542, 31, 32) -> { // Sensores estrés
                stress = event.values[0]
                val stressLevel = when {
                    stress <= 25 -> "Bajo"
                    stress <= 50 -> "Normal"
                    stress <= 75 -> "Alto"
                    else -> "Muy Alto"
                }
                Log.d(TAG, "😰 Estrés: $stress ($stressLevel) - Sensor: $sensorName")
                runOnUiThread {
                    tvStress.text = "😰 $stressLevel"
                    tvStress.setTextColor(getColor(android.R.color.holo_orange_light))
                }
            }

            else -> {
                // Log para sensores desconocidos pero potencialmente útiles
                if (event.values[0] > 0) {
                    Log.d(TAG, "🔍 Sensor desconocido - Tipo: $sensorType, Nombre: '$sensorName', Valor: ${event.values[0]}")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Alta"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Media"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Baja"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "No confiable"
            else -> "Desconocida"
        }
        Log.d(TAG, "Precisión sensor '${sensor?.name}': $accuracyText ($accuracy)")
    }

    private fun updateUI() {
        runOnUiThread {
            tvHeartRate.text = if (heartRate > 0) "❤️ ${heartRate.toInt()} bpm" else "❤️ Esperando..."
            tvOxygen.text = if (oxygen > 0) "🫁 ${oxygen.toInt()}%" else "🫁 Esperando..."
            tvStress.text = if (stress > 0) {
                val level = when {
                    stress <= 25 -> "Bajo"
                    stress <= 50 -> "Normal"
                    stress <= 75 -> "Alto"
                    else -> "Muy Alto"
                }
                "😰 $level"
            } else "😰 Esperando..."
            tvSteps.text = if (steps > 0) "👟 ${steps.toInt()} pasos" else "👟 Esperando..."
        }
    }

    private fun sendDataToServer() {
        Log.d(TAG, "=== ENVIANDO DATOS AL SERVIDOR ===")

        // Preparar datos con valores por defecto para nulos
        val jsonData = JSONObject().apply {
            put("heart_rate", if (heartRate > 0) heartRate.toInt() else 0)
            put("oxygen", if (oxygen > 0) oxygen.toInt() else 0)
            put("stress", if (stress > 0) stress.toInt() else 0)
            put("steps", if (steps > 0) steps.toInt() else 0)
            put("timestamp", System.currentTimeMillis())
            put("device_model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("app_version", "1.0")
        }

        Log.d(TAG, "Datos a enviar: $jsonData")

        runOnUiThread {
            btnSend.isEnabled = false
            btnSend.text = "Enviando..."
            Toast.makeText(this, "📤 Enviando datos...", Toast.LENGTH_SHORT).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            var success = false
            var responseMessage = ""

            try {
                val url = URL("http://3.145.62.106:9000/upload/")
                connection = url.openConnection() as HttpURLConnection

                // Configurar conexión
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "WearOS-HealthApp/1.0")
                    setRequestProperty("X-Device-Type", "WearOS")
                    doOutput = true
                    doInput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                // Enviar datos
                connection.outputStream.use { outputStream ->
                    val jsonBytes = jsonData.toString().toByteArray(Charsets.UTF_8)
                    outputStream.write(jsonBytes)
                    outputStream.flush()
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Código de respuesta: $responseCode")

                // Leer respuesta
                val inputStream = if (responseCode >= 400) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }

                responseMessage = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                success = responseCode in 200..299

                Log.d(TAG, "Respuesta del servidor: $responseMessage")

            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Error de red - Host desconocido: ${e.message}")
                responseMessage = "Sin conexión a Internet"
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout de conexión: ${e.message}")
                responseMessage = "Timeout de conexión"
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Error de conexión: ${e.message}")
                responseMessage = "No se pudo conectar al servidor"
            } catch (e: Exception) {
                Log.e(TAG, "Error general al enviar datos", e)
                responseMessage = "Error: ${e.javaClass.simpleName}"
            } finally {
                try {
                    connection?.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Error al cerrar conexión: ${e.message}")
                }
            }

            // Actualizar UI en el hilo principal
            withContext(Dispatchers.Main) {
                btnSend.isEnabled = true
                btnSend.text = "Enviar"

                if (success) {
                    Toast.makeText(this@MainActivity, "✅ Datos enviados correctamente", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "✅ Envío exitoso")
                } else {
                    Toast.makeText(this@MainActivity, "❌ Error: $responseMessage", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "❌ Error en envío: $responseMessage")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "App resumida - Re-registrando sensores")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
            // Re-registrar sensores
            heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            stepCounterSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            oxygenSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            stressSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "App pausada - Desregistrando sensores")
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "App destruida")
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
    }
}