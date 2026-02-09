package com.mycompany.plugins.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class HealthPermissionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HealthPermission"
        const val EXTRA_READ_TYPES = "read_types"
        const val EXTRA_WRITE_TYPES = "write_types"
        const val RESULT_READ_STATUS = "read_status"
        const val RESULT_WRITE_STATUS = "write_status"

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

        fun buildReadPermissions(types: List<String>): Set<String> {
            return types.mapNotNull { type ->
                getRecordType(type)?.let { recordClass ->
                    HealthPermission.getReadPermission(recordClass)
                }
            }.toSet()
        }

        fun buildWritePermissions(types: List<String>): Set<String> {
            return types.mapNotNull { type ->
                getRecordType(type)?.let { recordClass ->
                    HealthPermission.getWritePermission(recordClass)
                }
            }.toSet()
        }
    }

    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Set<String>>
    private var permissionsToRequest: Set<String> = emptySet()
    private var permissionRequestLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called")

        // Check if Health Connect is available
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Log.e(TAG, "Health Connect is not available, status: $status")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        healthConnectClient = HealthConnectClient.getOrCreate(this)

        val contract = PermissionController.createRequestPermissionResultContract()
        requestPermissionLauncher = registerForActivityResult(contract) { grantedPermissions ->
            Log.d(TAG, "Permission result received: $grantedPermissions")
            returnPermissionStatus()
        }

        val readTypes = intent.getStringArrayListExtra(EXTRA_READ_TYPES) ?: arrayListOf()
        val writeTypes = intent.getStringArrayListExtra(EXTRA_WRITE_TYPES) ?: arrayListOf()

        Log.d(TAG, "Read types: $readTypes, Write types: $writeTypes")

        val readPermissions = buildReadPermissions(readTypes)
        val writePermissions = buildWritePermissions(writeTypes)
        permissionsToRequest = readPermissions + writePermissions

        Log.d(TAG, "Permissions to request: $permissionsToRequest")
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume called, permissionRequestLaunched: $permissionRequestLaunched")

        if (!permissionRequestLaunched) {
            permissionRequestLaunched = true

            if (permissionsToRequest.isEmpty()) {
                Log.d(TAG, "No permissions to request, returning status")
                returnPermissionStatus()
            } else {
                Log.d(TAG, "Launching permission request")
                try {
                    requestPermissionLauncher.launch(permissionsToRequest)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching permission request", e)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    private fun returnPermissionStatus() {
        lifecycleScope.launch {
            try {
                val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
                Log.d(TAG, "Granted permissions: $grantedPermissions")

                val readStatus = Bundle()
                val writeStatus = Bundle()

                for (type in ALL_TYPES) {
                    val recordClass = getRecordType(type)
                    if (recordClass != null) {
                        val readPerm = HealthPermission.getReadPermission(recordClass)
                        val writePerm = HealthPermission.getWritePermission(recordClass)

                        readStatus.putString(type, if (grantedPermissions.contains(readPerm)) "granted" else "denied")
                        writeStatus.putString(type, if (grantedPermissions.contains(writePerm)) "granted" else "denied")
                    } else {
                        readStatus.putString(type, "unsupported")
                        writeStatus.putString(type, "unsupported")
                    }
                }

                val resultIntent = Intent().apply {
                    putExtra(RESULT_READ_STATUS, readStatus)
                    putExtra(RESULT_WRITE_STATUS, writeStatus)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting permission status", e)
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }
}
