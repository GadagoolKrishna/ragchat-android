package com.ragchat.sample.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragchat.api.config.ModelRoutingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    currentRoutingMode: ModelRoutingMode,
    onRoutingModeChanged: (ModelRoutingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Settings & Routing") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        ) {
            Text(text = "Model Routing Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Choose how queries are dispatched between on-device hardware and cloud endpoints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            RoutingOptionsList(currentRoutingMode, onRoutingModeChanged)
        }
    }
}

@Composable
private fun RoutingOptionsList(
    currentRoutingMode: ModelRoutingMode,
    onRoutingModeChanged: (ModelRoutingMode) -> Unit,
) {
    ModelRoutingMode.entries.forEach { mode ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .selectable(
                        selected = (mode == currentRoutingMode),
                        onClick = { onRoutingModeChanged(mode) },
                        role = Role.RadioButton,
                    ).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = (mode == currentRoutingMode), onClick = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = mode.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = modeDescription(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

private fun modeDescription(mode: ModelRoutingMode): String =
    when (mode) {
        ModelRoutingMode.LOCAL_FIRST -> "Tries Gemini Nano first; falls back to Cloud API if unavailable."
        ModelRoutingMode.LOCAL_ONLY -> "100% on-device private execution. Zero network requests."
        ModelRoutingMode.CLOUD_FIRST -> "Uses cloud LLM; falls back to device if offline."
        ModelRoutingMode.CLOUD_ONLY -> "Remote cloud execution only."
        ModelRoutingMode.AUTO -> "Dynamic routing based on device thermals, battery, and query complexity."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PrivacyScreen(modifier: Modifier = Modifier) {
    var localConsent by remember { mutableStateOf(true) }
    var cloudConsent by remember { mutableStateOf(true) }
    var telemetryConsent by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Privacy & Governance") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Consent Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Control processing per DPDP and GDPR requirements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ConsentRow("Local On-Device Processing", "Allow query execution on device.", localConsent) { localConsent = it }
            }
            item {
                ConsentRow("Cloud Inference Processing", "Allow sending snippets to cloud LLM.", cloudConsent) { cloudConsent = it }
            }
            item {
                ConsentRow("Sanitized Telemetry", "Share token metrics (no prompts).", telemetryConsent) { telemetryConsent = it }
            }
            item { AuditSection() }
        }
    }
}

@Composable
private fun AuditSection() {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Tamper-Evident Audit Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Hash-chained records verifying access without logging confidential content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Seq #104 | QUERY_PROCESSED | gemini-nano\nHash: a7f8...9b12 (Verified)\nTimestamp: 10s ago",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Seq #103 | DOCUMENT_INGESTED | doc_finance.pdf\nHash: c4e2...81aa (Verified)\nTimestamp: 2m ago",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DebugRoutingSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(text = "Live Model Routing Inspection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Zero-PII telemetry: inspect decisions, reason codes, and fallback triggers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Latest Decision: ON-DEVICE (gemini-nano)", fontWeight = FontWeight.Bold)
                    Text(text = "Routing Strategy: LOCAL_FIRST", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Reason Codes: [LOCAL_AVAILABLE, CONTEXT_FITS_WINDOW]", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Fallback Triggered: false", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Confidence Score: 0.94", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)) {
                Text("Close Inspector")
            }
        }
    }
}
