package com.sementia.caregiver.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.domain.model.Severity
import com.sementia.caregiver.ui.HubViewModel
import com.sementia.caregiver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    hubViewModel: HubViewModel,
    viewModel: TimelineViewModel = viewModel(factory = TimelineViewModelFactory(hubViewModel)),
) {
    val events by viewModel.events.collectAsState()
    val error by viewModel.error.collectAsState()
    val baseUrl by hubViewModel.baseUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground)
            .padding(16.dp),
    ) {
        Text(
            text = "Activity Timeline",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (baseUrl == null) {
            Text(
                text = "Connect to the hub from Settings (forget hub) or restart the app to sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = events,
                key = { it.id },
                contentType = { it.type },
            ) { event ->
                EventCard(
                    event = event,
                    onAcknowledge = { viewModel.acknowledge(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: EventEnvelope,
    onAcknowledge: (EventEnvelope) -> Unit,
) {
    val color = when (event.priority) {
        Severity.CRITICAL -> CriticalRed
        Severity.HIGH -> WarningOrange
        Severity.NORMAL -> BluePrimary
        Severity.LOW -> SuccessGreen
    }

    val icon = when (event.priority) {
        Severity.CRITICAL, Severity.HIGH -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = event.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = event.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Detailed log: ${event.payload ?: "No additional data."}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (event.type == com.sementia.caregiver.domain.model.EventType.EMERGENCY ||
                    event.priority == Severity.CRITICAL
                ) {
                    Button(
                        onClick = { onAcknowledge(event) },
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = color.copy(alpha = 0.1f),
                            contentColor = color,
                        ),
                    ) {
                        Text("Acknowledge emergency")
                    }
                }
            }
        }
    }
}
