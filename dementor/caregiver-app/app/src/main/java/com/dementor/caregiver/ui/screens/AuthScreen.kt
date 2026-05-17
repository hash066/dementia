package com.dementor.caregiver.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.theme.*

@Composable
fun AuthScreen(
    hubViewModel: HubViewModel,
    onAuthenticated: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    /** Android emulator → host machine (uvicorn). Physical device: your PC’s LAN IP. */
    var ipAddress by remember { mutableStateOf("10.0.2.2") }
    val connectError by hubViewModel.connectError.collectAsState()
    val isConnecting by hubViewModel.isConnecting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to Dementor",
            style = MaterialTheme.typography.headlineLarge,
            color = BluePrimary,
        )
        Text(
            text = "Connect to the patient's monitoring hub",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Hub address (IP or URL)") },
            supportingText = {
                Text("Emulator: 10.0.2.2 — Device: your PC’s IP, port optional (defaults to 8000)")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it },
            label = { Text("Security PIN") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation(),
        )

        connectError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                hubViewModel.connect(ipAddress, pin, onAuthenticated)
            },
            enabled = !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Connect Hub", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = { /* mDNS later */ },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Auto-discover Hub", color = BlueSecondary)
        }
    }
}
