package com.example.healthconnectpractice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectpractice.ui.theme.HealthConnectPracticeTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private var requestPermissions : ActivityResultLauncher<Set<String>>? = null
    private lateinit var  healthConnectClient : HealthConnectClient
    private val endTime = LocalDateTime.now()
    private val startTime = LocalDateTime.now().minusDays(1)
    private val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HealthConnectPracticeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val providerPackageName = "com.google.android.apps.healthdata"
        val availabilityStatus = HealthConnectClient.getSdkStatus(this, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            return // 실행 가능한 통합이 없기 때문에 조기 복귀
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            // 선택적으로 패키지 설치 프로그램으로 리디렉션하여 공급자를 찾음.
            openPlayStoreForHealthConnect()
            return
        }

        healthConnectClient = HealthConnectClient.getOrCreate(this)
        Log.d("taag 현재 시간", "endTime: $endTime, startTime: $startTime")

        // Create the permissions launcher
        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            lifecycleScope.launch {
                if (granted.containsAll(PERMISSIONS)) {
                    aggregateData(healthConnectClient)
                } else {
                    checkPermissionsAndRun(healthConnectClient)
                }
            }
        }

        lifecycleScope.launch {
            checkPermissionsAndRun(healthConnectClient)
        }
    }

    private suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (granted.containsAll(PERMISSIONS)) {
            aggregateData(healthConnectClient)
        } else {
            requestPermissions?.launch(PERMISSIONS)
        }
    }

    private suspend fun aggregateData(healthConnectClient: HealthConnectClient) {
        // (선택) Instant로 변환해서 시간 범위 설정
        val startTimeInstant = startTime.atZone(ZoneId.of("Asia/Seoul")).toInstant()
        val endTimeInstant = endTime.atZone(ZoneId.of("Asia/Seoul")).toInstant()

        // 30분단위 걸음 수 집계 (엉터리로 가져와 짐..), 총 걸음수 , 총 칼로리
//        aggregateStepsInto3oMins(healthConnectClient, startTime, endTime)
        readStepsByTimeRange(healthConnectClient, startTimeInstant, endTimeInstant)
        readCaloryByTimeRange(healthConnectClient, startTimeInstant, endTimeInstant)
    }

    private suspend fun readCaloryByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            Log.d("taag", response.records.toString())
            val energy = response.records[0].energy.toString()
            Log.d("taag Total Calory", energy)
            Log.d("taag Total Calory2", Math.round(energy.split(" ")[0].toDouble()).toString() + " Kcal")
        } catch (e: Exception) {
            Log.d("taag Calory Exception", "$e")
        }
    }

    private suspend fun readStepsByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            Log.d("taag Total Steps", response.records[0].count.toString())
        } catch (e: Exception) {
            Log.d("Total Steps Exception", "$e")
        }
    }

    private fun openPlayStoreForHealthConnect() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
            setPackage("com.android.vending")
        }
        startActivity(intent)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
