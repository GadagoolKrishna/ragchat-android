package com.ragchat.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ModelsScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("On-Device Models") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DeviceProfileCard() }
            item { Text("Available Model Weights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { GeminiNanoCard() }
            item { GemmaDownloadCard() }
        }
    }
}

@Composable
private fun DeviceProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device Profiler Assessment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Hardware Tier: HIGH (8GB RAM, NPU Accelerated)", style = MaterialTheme.typography.bodyMedium)
            Text("Thermal Status: NOMINAL | Battery: 88%", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Recommended: Gemma 2B (4-bit int4 quantized) or Gemini Nano",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GeminiNanoCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gemini Nano (AICore)", fontWeight = FontWeight.Bold)
                Text("System Bundled", color = MaterialTheme.colorScheme.primary)
            }
            Text("Context Window: 4,096 tokens | Latency: ~18ms/tok", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Status: Available and Ready",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GemmaDownloadCard() {
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0.45f) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gemma 2B LiteRT", fontWeight = FontWeight.Bold)
                Text("1.3 GB", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("SHA-256 Verified ECDSA Signed | GPU/CPU", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (isDownloading) {
                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text("Downloading & Verifying (45%)...", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = { isDownloading = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                    Text("Download to App Storage")
                }
            }
        }
    }
}
