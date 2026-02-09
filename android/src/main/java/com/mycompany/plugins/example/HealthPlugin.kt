package com.mycompany.plugins.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import java.util.ArrayList
import kotlin.reflect.KClass

@CapacitorPlugin(
    name = "Health",
    permissions = [
        Permission(
            alias = "activity",
            strings = [Manifest.permission.ACTIVITY_RECOGNITION]
        ),
        Permission(
            alias = "sensors",
            strings = [Manifest.permission.BODY_SENSORS]
        )
    ]
)
class HealthPlugin : Plugin(), SensorEventListener {

    private val implementation = Health()
    private var sensorManager: SensorManager? = null
    private val activeSensors = mutableMapOf<Int, Sensor?>()
    private var initialSteps: Float = -1f
    private var dailyBaselineSteps: Long = 0L
    private val monitoringTypes = mutableSetOf<String>()

    companion object {
        private val ALL_TYPES = listOf("steps", "distance", "calories", "heart_rate", "sleep")

        fun getRecordType(type: String): KClass<out androidx.health.connect.client.records.Record>? {
            return when (type) {
                "steps" -> StepsRecord::class
                "distance" -> DistanceRecord::class
                "calories" -> TotalCaloriesBurnedRecord::class
                "heart_rate" -> HeartRateRecord::class
                "sleep" -> SleepSessionRecord::class
                else -> null
            }
        }
    }

    private var permissionCall: PluginCall? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Intent>

    override fun load() {
        super.load()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handlePermissionActivityResult(result.resultCode, result.data)
        }
    }

    @PluginMethod
    fun startMonitoring(call: PluginCall) {
        val types = call.getArray("types")?.toList<String>() ?: emptyList()
        Log.d("HealthPlugin", "startMonitoring called for types: $types")
        if (types.isEmpty()) {
            call.reject("Monitoring types array is empty")
            return
        }

        val neededPermissions = mutableListOf<String>()
        if (types.contains("steps") && getPermissionState("activity") != PermissionState.GRANTED) {
            neededPermissions.add("activity")
        }
        if (types.contains("heart_rate") && getPermissionState("sensors") != PermissionState.GRANTED) {
            neededPermissions.add("sensors")
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionForAliases(neededPermissions.toTypedArray(), call, "monitoringPermissionCallback")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            startMonitoringInternal(call, types)
        }
    }

    @PermissionCallback
    private fun monitoringPermissionCallback(call: PluginCall) {
        val types = call.getArray("types")?.toList<String>() ?: emptyList()
        val denied = mutableListOf<String>()
        if (types.contains("steps") && getPermissionState("activity") != PermissionState.GRANTED) {
            denied.add("Activity Recognition")
        }
        if (types.contains("heart_rate") && getPermissionState("sensors") != PermissionState.GRANTED) {
            denied.add("Body Sensors")
        }

        if (denied.isNotEmpty()) {
            call.reject("Permissions denied: ${denied.joinToString(", ")}")
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                startMonitoringInternal(call, types)
            }
        }
    }

    private suspend fun startMonitoringInternal(call: PluginCall, types: List<String>) {
        stopMonitoringInternal()
        monitoringTypes.addAll(types)

        // Fetch baseline steps if needed
        if (types.contains("steps")) {
            try {
                val client = getOrCreateClient()
                if (client != null) {
                    dailyBaselineSteps = implementation.readTotalStepsToday(client)
                    Log.d("HealthPlugin", "Daily baseline steps fetched: $dailyBaselineSteps")
                }
            } catch (e: Exception) {
                Log.e("HealthPlugin", "Failed to fetch daily baseline steps", e)
                // Fallback to 0 if Health Connect is not available or fails
                dailyBaselineSteps = 0
            }
        }

        val failedTypes = mutableListOf<String>()

        types.forEach { type ->
            val sensorType = when (type) {
                "steps" -> Sensor.TYPE_STEP_COUNTER
                "heart_rate" -> Sensor.TYPE_HEART_RATE
                else -> null
            }

            if (sensorType != null) {
                val sensor = sensorManager?.getDefaultSensor(sensorType)
                if (sensor != null) {
                    val registered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) ?: false
                    if (registered) {
                        Log.d("HealthPlugin", "Successfully registered listener for sensor: ${sensor.name}")
                        activeSensors[sensorType] = sensor
                        if (type == "steps") initialSteps = -1f
                    } else {
                        Log.e("HealthPlugin", "Failed to register listener for sensor: ${sensor.name}")
                        failedTypes.add(type)
                    }
                } else {
                    Log.e("HealthPlugin", "Sensor $sensorType not found on device")
                    failedTypes.add(type)
                }
            }
        }

        if (failedTypes.size == types.size && types.isNotEmpty()) {
            call.reject("Failed to start monitoring for any of the requested types: ${failedTypes.joinToString()}")
        } else {
            val ret = JSObject()
            if (failedTypes.isNotEmpty()) {
                val failedArray = JSONArray(failedTypes)
                ret.put("failedTypes", failedArray)
            }
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun stopMonitoring(call: PluginCall) {
        stopMonitoringInternal()
        call.resolve()
    }

    private fun stopMonitoringInternal() {
        sensorManager?.unregisterListener(this)
        activeSensors.clear()
        monitoringTypes.clear()
        initialSteps = -1f
        dailyBaselineSteps = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorType = event?.sensor?.type ?: return
        val value = event.values[0]

        val type = when (sensorType) {
            Sensor.TYPE_STEP_COUNTER -> "steps"
            Sensor.TYPE_HEART_RATE -> "heart_rate"
            else -> null
        } ?: return

        val resultValue = if (type == "steps") {
            if (initialSteps < 0) initialSteps = value
            val delta = (value - initialSteps).toLong()
            (dailyBaselineSteps + delta).toDouble()
        } else {
            value.toDouble()
        }

        val data = JSObject()
        data.put("type", type)
        data.put("value", resultValue)
        Log.v("HealthPlugin", "Value update: $type = $resultValue")
        notifyListeners("monitoringUpdate", data)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun handlePermissionActivityResult(resultCode: Int, data: Intent?) {
        val call = permissionCall ?: return
        permissionCall = null

        if (resultCode == Activity.RESULT_OK && data != null) {
            val readBundle = data.getBundleExtra(HealthPermissionActivity.RESULT_READ_STATUS)
            val writeBundle = data.getBundleExtra(HealthPermissionActivity.RESULT_WRITE_STATUS)

            val ret = JSObject()
            val readObj = JSObject()
            val writeObj = JSObject()

            ALL_TYPES.forEach { type ->
                readObj.put(type, readBundle?.getString(type) ?: "denied")
                writeObj.put(type, writeBundle?.getString(type) ?: "denied")
            }

            ret.put("read", readObj)
            ret.put("write", writeObj)
            call.resolve(ret)
        } else {
            call.reject("Permission request cancelled or failed")
        }
    }

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")

        val ret = JSObject()
        ret.put("value", implementation.echo(value))
        call.resolve(ret)
    }

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        call.resolve(availabilityPayload(status))
    }

    @PluginMethod
    fun checkAuthorizationStatus(call: PluginCall) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val healthConnectClient = HealthConnectClient.getOrCreate(context)
                val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()

                val ret = JSObject()
                val readObj = JSObject()
                val writeObj = JSObject()

                ALL_TYPES.forEach { type ->
                    val recordClass = getRecordType(type)
                    if (recordClass != null) {
                        val readPerm = HealthPermission.getReadPermission(recordClass)
                        val writePerm = HealthPermission.getWritePermission(recordClass)

                        readObj.put(type, if (grantedPermissions.contains(readPerm)) "granted" else "denied")
                        writeObj.put(type, if (grantedPermissions.contains(writePerm)) "granted" else "denied")
                    } else {
                        readObj.put(type, "unsupported")
                        writeObj.put(type, "unsupported")
                    }
                }

                ret.put("read", readObj)
                ret.put("write", writeObj)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to check authorization status: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun requestAuthorization(call: PluginCall) {
        val readTypesArray = call.getArray("read")
        val writeTypesArray = call.getArray("write")

        val readTypes = readTypesArray?.toList<String>() ?: emptyList()
        val writeTypes = writeTypesArray?.toList<String>() ?: emptyList()

        permissionCall = call

        val intent = Intent(context, HealthPermissionActivity::class.java).apply {
            putStringArrayListExtra(HealthPermissionActivity.EXTRA_READ_TYPES, ArrayList(readTypes))
            putStringArrayListExtra(HealthPermissionActivity.EXTRA_WRITE_TYPES, ArrayList(writeTypes))
        }

        permissionLauncher.launch(intent)
    }


    @PluginMethod
    fun openSettings(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        
        // If it's available, try opening settings
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            try {
                val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                val ret = JSObject()
                ret.put("opened", true)
                call.resolve(ret)
                return
            } catch (e: Exception) {
                // Fallback to Play Store if settings intent fails for some reason
            }
        }

        // If update required or not installed/available, try to open Play Store
        try {
            val appId = "com.google.android.apps.healthdata"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$appId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
            val ret = JSObject()
            ret.put("opened", true)
            ret.put("fallbackToStore", true)
            call.resolve(ret)
        } catch (e: Exception) {
            // If market intent fails, try browser
            try {
                val appId = "com.google.android.apps.healthdata"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$appId")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val ret = JSObject()
                ret.put("opened", true)
                ret.put("fallbackToStore", true)
                call.resolve(ret)
            } catch (err: Exception) {
                call.reject("Failed to open Health Connect settings or Play Store: ${err.message}", err)
            }
        }
    }

    @PluginMethod
    fun readSamples(call: PluginCall) {
        val types = call.getArray("types")?.toList<String>() ?: emptyList()
        val startDateStr = call.getString("startDate")
        val endDateStr = call.getString("endDate")
        val limit = call.getInt("limit") ?: 1000
        val ascending = call.getBoolean("ascending") ?: true
        val includeMetadata = call.getBoolean("includeMetadata") ?: false

        if (startDateStr == null || endDateStr == null) {
            call.reject("startDate and endDate are required")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val client = getClientOrReject(call) ?: return@launch
            try {
                val start = Instant.parse(startDateStr)
                val end = Instant.parse(endDateStr)

                val samples = implementation.readSamples(client, types, start, end, limit, ascending, includeMetadata)

                val ret = JSObject()
                val samplesArray = JSONArray(samples)
                ret.put("samples", samplesArray)
                call.resolve(ret)

            } catch (e: Exception) {
                call.reject("Failed to read samples: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun writeSamples(call: PluginCall) {
        val samplesArray = call.getArray("samples")
        if (samplesArray == null) {
            call.reject("samples array is required")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val client = getClientOrReject(call) ?: return@launch
            try {
                val jsSamples = mutableListOf<JSObject>()
                for (i in 0 until samplesArray.length()) {
                    val jsonObj = samplesArray.getJSONObject(i)
                    jsSamples.add(JSObject(jsonObj.toString()))
                }
                val count = implementation.writeSamples(client, jsSamples)

                val ret = JSObject()
                ret.put("success", true)
                ret.put("writtenCount", count)
                call.resolve(ret)

            } catch (e: Exception) {
               call.reject("Failed to write samples: ${e.message}", e)
            }
        }
    }

    private fun getClientOrReject(call: PluginCall): HealthConnectClient? {
        val client = getOrCreateClient()
        if (client == null) {
            val status = HealthConnectClient.getSdkStatus(context)
            call.reject(availabilityReason(status))
            return null
        }
        return client
    }

    private fun getOrCreateClient(): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            return null
        }
        return HealthConnectClient.getOrCreate(context)
    }

    private fun availabilityPayload(status: Int): JSObject {
        val payload = JSObject()
        payload.put("platform", "android")
        payload.put("available", status == HealthConnectClient.SDK_AVAILABLE)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            payload.put("reason", availabilityReason(status))
        }
        return payload
    }

    private fun availabilityReason(status: Int): String {
        return when (status) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect needs an update."
            HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect is unavailable on this device."
            else -> "Health Connect availability unknown."
        }
    }
}
