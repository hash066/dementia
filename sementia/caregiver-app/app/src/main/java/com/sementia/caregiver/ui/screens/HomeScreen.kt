package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sementia.caregiver.data.remote.HubStatusDto
import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.ui.HubViewModel
import com.sementia.caregiver.ui.theme.*

@Composable
fun HomeScreen(
    hubViewModel: HubViewModel,
    onNavigateToChat: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToMedical: () -> Unit = {},
) {
    var status by remember { mutableStateOf<HubStatusDto?>(null) }
    var recent by remember { mutableStateOf<EventEnvelope?>(null) }
    var medication by remember { mutableStateOf("No medication memories yet") }

    LaunchedEffect(hubViewModel) {
        status = hubViewModel.fetchStatus()
        val events = hubViewModel.fetchEvents()
        recent = events.firstOrNull()
        medication = hubViewModel.fetchMedical("medication").firstOrNull()?.let {
            "${it.label}: ${it.value ?: "mentioned in memory"}"
        } ?: "No medication memories yet"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground),
    ) {
        DashboardHeader(status)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader("Patient Status")
                StatusGrid(status)
            }
            item {
                SectionHeader("Quick Actions")
                QuickActions(
                    onChatClick = onNavigateToChat,
                    onTimelineClick = onNavigateToTimeline,
                    onMedicalClick = onNavigateToMedical,
                )
            }
            item {
                SectionHeader("Recent Activity")
                ActivitySummaryCard(recent = recent, onClick = onNavigateToTimeline)
            }
            item {
                SectionHeader("Medication Memory")
                MedicationSummaryCard(text = medication, onClick = onNavigateToMedical)
            }
        }
    }
}

@Composable
fun DashboardHeader(status: HubStatusDto?) {
    val emergency = status?.activeEmergency == true
    val subtitle = if (emergency) {
        "Emergency active. Open timeline to acknowledge."
    } else {
        "Current hub state: ${status?.fsmState ?: "waiting for connection"}"
    }

    Surface(
        color = if (emergency) CriticalRed else BluePrimary,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 28.dp),
        ) {
            Text("Dementor", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.86f))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun StatusGrid(status: HubStatusDto?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusCard("Events/min", "${status?.eventCountLastMin ?: 0}", Icons.Default.Timeline, BluePrimary, Modifier.weight(1f))
        StatusCard("Emergency", if (status?.activeEmergency == true) "Active" else "Clear", Icons.Default.Warning, CriticalRed, Modifier.weight(1f))
    }
}

@Composable
fun StatusCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun QuickActions(onChatClick: () -> Unit, onTimelineClick: () -> Unit, onMedicalClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionButton("Chat", Icons.Default.Chat, TealAccent, onClick = onChatClick)
        ActionButton("Timeline", Icons.Default.Timeline, BluePrimary, onClick = onTimelineClick)
        ActionButton("Medical", Icons.Default.MedicalServices, WarningOrange, onClick = onMedicalClick)
        ActionButton("Images", Icons.Default.Image, SuccessGreen, onClick = onTimelineClick)
    }
}

@Composable
fun ActionButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(64.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
fun ActivitySummaryCard(recent: EventEnvelope?, onClick: () -> Unit) {
    SummaryCard(
        icon = Icons.Default.Timeline,
        iconColor = SuccessGreen,
        title = recent?.type?.name ?: "No events yet",
        subtitle = recent?.description ?: "Waiting for Pi speech, camera, IMU, or button events",
        trailing = recent?.location,
        onClick = onClick,
    )
}

@Composable
fun MedicationSummaryCard(text: String, onClick: () -> Unit) {
    SummaryCard(
        icon = Icons.Default.MedicalServices,
        iconColor = WarningOrange,
        title = text,
        subtitle = "Live from /query/medical",
        trailing = null,
        onClick = onClick,
    )
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (!trailing.isNullOrBlank()) {
                Text(text = trailing, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}
