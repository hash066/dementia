package com.sementia.caregiver.ui.screens

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
import androidx.compose.ui.tooling.preview.Preview
import com.sementia.caregiver.ui.theme.*

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    MaterialTheme {
        Surface {
            AuthScreen(onAuthenticated = {})
        }
    }
}

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("192.168.1.50") } // Default/Discovery placeholder
    var isConnecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Sementia",
            style = MaterialTheme.typography.headlineLarge,
            color = BluePrimary
        )
        Text(
            text = "Connect to the patient's monitoring hub",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Hub IP Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it },
            label = { Text("Security PIN") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                isConnecting = true
                // In a real app, verify PIN and connection here
                onAuthenticated()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Connect Hub", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = { /* Start mDNS discovery */ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Auto-discover Hub", color = BlueSecondary)
        }
    }
}
