package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sementia.caregiver.ui.HubViewModel

@Composable
fun SettingsScreen(
    hubViewModel: HubViewModel,
    onForgotHub: () -> Unit,
) {
    val baseUrl by hubViewModel.baseUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (baseUrl != null) "Hub: $baseUrl" else "No hub connected",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onForgotHub) {
            Text("Forget hub & sign in again")
        }
    }
}
