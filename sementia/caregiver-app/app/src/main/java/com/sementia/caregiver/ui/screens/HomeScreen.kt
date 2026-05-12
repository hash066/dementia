package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sementia.caregiver.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToMedical: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground)
    ) {
        DashboardHeader()
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("Patient Status")
                StatusGrid()
            }
            
            item {
                SectionHeader("Quick Actions")
                QuickActions(
                    onChatClick = onNavigateToChat,
                    onTimelineClick = onNavigateToTimeline,
                    onMedicalClick = onNavigateToMedical
                )
            }
            
            item {
                SectionHeader("Recent Activity")
                ActivitySummaryCard(onClick = onNavigateToTimeline)
            }
            
            item {
                SectionHeader("Next Medication")
                MedicationSummaryCard(onClick = onNavigateToMedical)
            }
        }
    }
}

@Composable
fun DashboardHeader() {
    Surface(
        color = BluePrimary,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Hello, Caregiver",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Patient: John Doe is currently resting.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
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
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun StatusGrid() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusCard("Heart Rate", "72 bpm", Icons.Default.Favorite, Color(0xFFE74C3C), Modifier.weight(1f))
        StatusCard("Sleep", "7h 20m", Icons.Default.Bedtime, Color(0xFF9B59B6), Modifier.weight(1f))
    }
}

@Composable
fun StatusCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun QuickActions(
    onChatClick: () -> Unit,
    onTimelineClick: () -> Unit,
    onMedicalClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActionButton("Call", Icons.Default.Call, BluePrimary, onClick = { /* Handle call */ })
        ActionButton("Chat", Icons.Default.Chat, TealAccent, onClick = onChatClick)
        ActionButton("SOS", Icons.Default.Warning, CriticalRed, onClick = { /* Handle SOS */ })
        ActionButton("More", Icons.Default.MoreHoriz, TextSecondary, onClick = { /* Handle More */ })
    }
}

@Composable
fun ActionButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(64.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = label, 
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
fun ActivitySummaryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = SuccessGreen)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Movement Detected", fontWeight = FontWeight.Bold)
                Text(text = "Living Room • 10 mins ago", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
fun MedicationSummaryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WarningOrange.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = WarningOrange)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Donepezil", fontWeight = FontWeight.Bold)
                Text(text = "5mg • Due at 8:00 PM", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text(text = "Upcoming", color = WarningOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
