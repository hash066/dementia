package com.dementor.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dementor.caregiver.ui.theme.BluePrimary

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    // Step 1: Patient Profile
    var patientName by remember { mutableStateOf("") }
    var patientAge by remember { mutableStateOf("") }
    var primaryCondition by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }

    // Step 2: Behavior & Safety
    var wanderingRisk by remember { mutableStateOf(false) }
    var aggression by remember { mutableStateOf(false) }
    var sundowning by remember { mutableStateOf(false) }
    var specialInstructions by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step == 1) {
            Text(
                text = "Patient Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = BluePrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tell us about the person you're caring for.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                color = Color.Gray
            )

            OutlinedTextField(
                value = patientName,
                onValueChange = { patientName = it },
                label = { Text("Patient Name") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = patientAge,
                onValueChange = { patientAge = it },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = primaryCondition,
                onValueChange = { primaryCondition = it },
                label = { Text("Primary Condition (e.g. Alzheimer's)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = emergencyContact,
                onValueChange = { emergencyContact = it },
                label = { Text("Emergency Contact Number") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { step = 2 },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Next", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "Behavior & Safety",
                style = MaterialTheme.typography.headlineMedium,
                color = BluePrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Help us customize the AI for their specific needs.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                color = Color.Gray
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wanderingRisk, onCheckedChange = { wanderingRisk = it })
                Text("High Wandering Risk", modifier = Modifier.padding(start = 8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = aggression, onCheckedChange = { aggression = it })
                Text("Prone to Agitation/Aggression", modifier = Modifier.padding(start = 8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sundowning, onCheckedChange = { sundowning = it })
                Text("Experiences Sundowning Syndrome", modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedTextField(
                value = specialInstructions,
                onValueChange = { specialInstructions = it },
                label = { Text("Triggers or Special Instructions") },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 32.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Complete Setup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
