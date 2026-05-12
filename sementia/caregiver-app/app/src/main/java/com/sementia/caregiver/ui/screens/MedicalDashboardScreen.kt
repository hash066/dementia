package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sementia.caregiver.ui.theme.*

@Composable
fun MedicalDashboardScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Meds", "Health Trends", "Appointments")

    Column(modifier = Modifier.fillMaxSize().background(WarmGrayBackground)) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = BluePrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> MedicationList()
            1 -> HealthTrends()
            2 -> AppointmentLog()
        }
    }
}

@Composable
fun MedicationList() {
    val meds = listOf(
        "Aspirin" to "81mg - Daily (Morning)",
        "Lisinopril" to "10mg - Daily (Evening)",
        "Donepezil" to "5mg - Daily (Bedtime)"
    )

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(meds) { (name, dosage) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(text = dosage, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun HealthTrends() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Health Charts (Vico Integration) Coming Soon", color = TextSecondary)
    }
}

@Composable
fun AppointmentLog() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No upcoming appointments found.", color = TextSecondary)
    }
}
