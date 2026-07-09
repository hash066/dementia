package com.sementia.caregiver.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sementia.caregiver.ui.CaptureViewModel
import com.sementia.caregiver.ui.HubViewModel

private val CAPTURE_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun CaptureScreen(
    hubViewModel: HubViewModel,
    captureViewModel: CaptureViewModel,
) {
    val context = LocalContext.current
    val baseUrl by hubViewModel.baseUrl.collectAsState()
    val isCapturing by captureViewModel.isCapturing.collectAsState()
    val log by captureViewModel.log.collectAsState()
    val sent by captureViewModel.sentCount.collectAsState()
    val failed by captureViewModel.failCount.collectAsState()
    val lastPeak by captureViewModel.lastPeak.collectAsState()
    val gpsFix by captureViewModel.location.lastFix.collectAsState()
    val gpsStatus by captureViewModel.location.status.collectAsState()
    val demoMode by hubViewModel.demoMode.collectAsState()

    fun micGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    var pendingStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true || micGranted()
        val locOk = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            captureViewModel.location.hasPermission()
        if (!micOk) captureViewModel.appendLog("Audio permission denied", isError = true)
        if (!locOk) captureViewModel.appendLog("Location permission denied — events will have no GPS", isError = true)
        if (micOk && pendingStart) {
            captureViewModel.startCapture(hubViewModel.clientOrNull(), demoMode)
        }
        pendingStart = false
    }

    fun toggleCapture() {
        if (isCapturing) {
            captureViewModel.stopCapture()
        } else if (micGranted()) {
            if (!captureViewModel.location.hasPermission()) {
                permissionLauncher.launch(CAPTURE_PERMISSIONS)
            }
            captureViewModel.startCapture(hubViewModel.clientOrNull(), demoMode)
        } else {
            pendingStart = true
            permissionLauncher.launch(CAPTURE_PERMISSIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Live Capture", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Streams live audio and location to the hub in 10-second chunks.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Hub: ${baseUrl ?: "not connected"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = gpsFix?.let {
                        "GPS: ${it.asPayloadString()}" +
                            (it.accuracyM?.let { a -> " ±${a.toInt()}m" } ?: "")
                    } ?: "GPS: no fix",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(text = gpsStatus, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Sent: $sent   Failed: $failed   Audio peak: $lastPeak/32767",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { toggleCapture() },
                colors = if (isCapturing) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Icon(
                    if (isCapturing) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isCapturing) "Stop capture" else "Start capture")
            }
            OutlinedButton(
                onClick = { captureViewModel.sendTestPing(hubViewModel.clientOrNull(), demoMode) },
                enabled = !isCapturing,
            ) {
                Text("Send test ping")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Debug log", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { captureViewModel.clearLog() }) { Text("Clear") }
        }

        Card(modifier = Modifier.weight(1f)) {
            if (log.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No log entries yet.\nTry \"Send test ping\" to check hub connectivity.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(log) { entry ->
                        Text(
                            text = "${entry.time}  ${entry.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
