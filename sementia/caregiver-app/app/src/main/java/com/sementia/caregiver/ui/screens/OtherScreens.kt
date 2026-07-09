package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sementia.caregiver.ui.HubViewModel

@Composable
fun SettingsScreen(
    hubViewModel: HubViewModel,
    onForgotHub: () -> Unit,
) {
    val baseUrl by hubViewModel.baseUrl.collectAsState()
    val demoMode by hubViewModel.demoMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Demo mode", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Show built-in sample data on every screen, no hub required. " +
                            "Turn off to use a live hub.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = demoMode,
                    onCheckedChange = { hubViewModel.setDemoMode(it) },
                )
            }
        }

        Text(
            text = when {
                demoMode -> "Source: sample data (demo mode on)"
                baseUrl != null -> "Hub: $baseUrl"
                else -> "No hub connected"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = onForgotHub) {
            Text("Forget hub & sign in again")
        }
    }
}
