package com.dementor.caregiver.ui.screens

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
import com.dementor.caregiver.data.remote.MedicalRowDto
import com.dementor.caregiver.domain.model.EventEnvelope
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.theme.*

@Composable
fun MedicalDashboardScreen(hubViewModel: HubViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<MedicalRowDto>>(emptyList()) }
    var events by remember { mutableStateOf<List<EventEnvelope>>(emptyList()) }
    val tabs = listOf("Meds", "Symptoms", "All Medical")

    LaunchedEffect(hubViewModel) {
        rows = hubViewModel.fetchMedical()
        events = hubViewModel.fetchEvents().filter { event ->
            val text = "${event.description} ${event.payload}".lowercase()
            listOf("aspirin", "pill", "medicine", "pain", "hurt", "fall", "confusion").any { it in text }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(WarmGrayBackground)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = BluePrimary) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> MedicalRows(rows.filter { it.category == "medication" }, "No medication memories yet.")
            1 -> EventRows(events, "No symptom or fall memories yet.")
            2 -> MedicalRows(rows, "No medical memories extracted yet.")
        }
    }
}

@Composable
private fun MedicalRows(rows: List<MedicalRowDto>, emptyText: String) {
    if (rows.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows) { row ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = row.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = row.value ?: row.category, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EventRows(events: List<EventEnvelope>, emptyText: String) {
    if (events.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(events) { event ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = event.type.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = event.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary)
    }
}
