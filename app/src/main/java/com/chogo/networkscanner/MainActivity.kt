package com.chogo.networkscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chogo.networkscanner.ui.theme.NetworkScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkScannerTheme {
                ScannerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: ScannerViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Network Scanner") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            val ip = state.localIp
            Text(
                text = if (ip != null) {
                    "Your IP: $ip  |  Scanning ${ip.substringBeforeLast(".")}.1 to .254"
                } else {
                    "Tap the button to scan your network"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { if (state.isScanning) vm.stopScan() else vm.startScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(if (state.isScanning) "Stop scan" else "Scan network")
            }

            if (state.isScanning) {
                LinearProgressIndicator(
                    progress = { state.scanned / state.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${state.scanned} / ${state.total} addresses checked",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = "${state.devices.size} device(s) found",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn {
                items(state.devices, key = { it.ip }) { DeviceCard(it) }
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.ip, style = MaterialTheme.typography.titleMedium)
            Text(device.hostname, style = MaterialTheme.typography.bodyMedium)
            Text("Response: ${device.responseMs} ms", style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (device.openPorts.isEmpty()) "Open ports: none found"
                else "Open ports: ${device.openPorts.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}