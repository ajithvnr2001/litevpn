package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ApiKeySource
import com.example.model.DnsPreset
import com.example.model.NordCredentialsState
import com.example.ui.MainUiState
import com.example.ui.components.ConfigBackupCard
import com.example.ui.components.DnsPresetsCard
import com.example.ui.components.IpLeakCheckCard
import com.example.ui.components.NetworkTuningCard
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ToolsScreen(
    uiState: MainUiState,
    isAndroidTv: Boolean = false,
    isWideLayout: Boolean = false,
    onApplyReconfigure: () -> Unit,
    onSelectDnsPreset: (DnsPreset) -> Unit,
    onSaveCustomDns: (String, String) -> Boolean,
    onToggleLanBypass: (Boolean) -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onSelectMtu: (Int) -> Unit,
    onHopLowerLoadServer: () -> Unit,
    onRunLeakCheck: () -> Unit,
    onExportBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit,
    onGetBackupJson: () -> String,
    onCopyBackupJson: (String) -> Unit,
    onImportBackupJson: (String) -> Boolean,
    onToggleAutoConnectOnBoot: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    onRetryCredentials: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearCustomApiKey: () -> Unit,
    onCopyWireGuardConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSwapKeyDialog by remember { mutableStateOf(false) }

    if (showSwapKeyDialog) {
        SwapApiKeyDialog(
            apiKeySource = uiState.apiKeySource,
            maskedPreview = uiState.maskedApiKeyPreview,
            onDismiss = { showSwapKeyDialog = false },
            onSave = { newKey ->
                showSwapKeyDialog = false
                onSaveApiKey(newKey)
            },
            onClearCustom = {
                showSwapKeyDialog = false
                onClearCustomApiKey()
            }
        )
    }

    val useTwoColumns = isAndroidTv || isWideLayout

    if (useTwoColumns) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = if (isAndroidTv) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ToolsHeader()
                }
                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        ToolsReconfigureBar(onApply = onApplyReconfigure)
                    }
                }
                item {
                    DnsPresetsCard(
                        uiState = uiState,
                        onSelectDnsPreset = onSelectDnsPreset,
                        onSaveCustomDns = onSaveCustomDns
                    )
                }
                item {
                    NetworkTuningCard(
                        uiState = uiState,
                        onToggleLanBypass = onToggleLanBypass,
                        onToggleAutoFailover = onToggleAutoFailover,
                        onSelectMtu = onSelectMtu,
                        onHopLowerLoadServer = onHopLowerLoadServer
                    )
                }
                item {
                    IpLeakCheckCard(
                        isRunning = uiState.isRunningLeakCheck,
                        result = uiState.leakCheckResult,
                        isVpnConnected = uiState.isConnected && uiState.vpnSession.isWireGuardVerified,
                        onRunLeakCheck = onRunLeakCheck
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    NordApiKeyCard(
                        uiState = uiState,
                        onSwapApiKey = { showSwapKeyDialog = true },
                        onRetryCredentials = onRetryCredentials,
                        onCopyWireGuardConfig = onCopyWireGuardConfig
                    )
                }
                item {
                    AlwaysOnAndBatteryCard(
                        isUnrestricted = uiState.isIgnoringBatteryOptimizations,
                        autoConnectOnBoot = uiState.autoConnectOnBoot,
                        cacheHits = uiState.vpnSession.dnsCacheHits,
                        totalQueries = uiState.vpnSession.dnsQueriesForwarded,
                        onToggleAutoConnectOnBoot = onToggleAutoConnectOnBoot,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenSystemVpnSettings = onOpenSystemVpnSettings
                    )
                }
                item {
                    ConfigBackupCard(
                        onExportBackupFile = onExportBackupFile,
                        onImportBackupFile = onImportBackupFile,
                        onGetBackupJson = onGetBackupJson,
                        onCopyBackupJson = onCopyBackupJson,
                        onImportBackupJson = onImportBackupJson
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ToolsHeader()
                }
                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        ToolsReconfigureBar(onApply = onApplyReconfigure)
                    }
                }
                item {
                    NordApiKeyCard(
                        uiState = uiState,
                        onSwapApiKey = { showSwapKeyDialog = true },
                        onRetryCredentials = onRetryCredentials,
                        onCopyWireGuardConfig = onCopyWireGuardConfig
                    )
                }
                item {
                    DnsPresetsCard(
                        uiState = uiState,
                        onSelectDnsPreset = onSelectDnsPreset,
                        onSaveCustomDns = onSaveCustomDns
                    )
                }
                item {
                    NetworkTuningCard(
                        uiState = uiState,
                        onToggleLanBypass = onToggleLanBypass,
                        onToggleAutoFailover = onToggleAutoFailover,
                        onSelectMtu = onSelectMtu,
                        onHopLowerLoadServer = onHopLowerLoadServer
                    )
                }
                item {
                    IpLeakCheckCard(
                        isRunning = uiState.isRunningLeakCheck,
                        result = uiState.leakCheckResult,
                        isVpnConnected = uiState.isConnected && uiState.vpnSession.isWireGuardVerified,
                        onRunLeakCheck = onRunLeakCheck
                    )
                }
                item {
                    AlwaysOnAndBatteryCard(
                        isUnrestricted = uiState.isIgnoringBatteryOptimizations,
                        autoConnectOnBoot = uiState.autoConnectOnBoot,
                        cacheHits = uiState.vpnSession.dnsCacheHits,
                        totalQueries = uiState.vpnSession.dnsQueriesForwarded,
                        onToggleAutoConnectOnBoot = onToggleAutoConnectOnBoot,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenSystemVpnSettings = onOpenSystemVpnSettings
                    )
                }
                item {
                    ConfigBackupCard(
                        onExportBackupFile = onExportBackupFile,
                        onImportBackupFile = onImportBackupFile,
                        onGetBackupJson = onGetBackupJson,
                        onCopyBackupJson = onCopyBackupJson,
                        onImportBackupJson = onImportBackupJson
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "API key, DNS, MTU, leak check & backup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolsReconfigureBar(onApply: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, CloudflareAmber.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Settings changed while connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("tools_apply_reconfigure_button")
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun AlwaysOnAndBatteryCard(
    isUnrestricted: Boolean,
    autoConnectOnBoot: Boolean,
    cacheHits: Long,
    totalQueries: Long,
    onToggleAutoConnectOnBoot: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("background_optimization_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALWAYS-ON & BACKGROUND",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isUnrestricted) "UNRESTRICTED" else "OPTIMIZED",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (isUnrestricted) EmeraldProtected else CloudflareAmber
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Connect on Boot",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reconnect saved location on TV/phone power-up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoConnectOnBoot,
                    onCheckedChange = onToggleAutoConnectOnBoot,
                    modifier = Modifier.testTag("auto_connect_boot_switch")
                )
            }

            if (totalQueries > 0L) {
                val hitRate = ((cacheHits * 100L) / totalQueries).coerceIn(0L, 100L)
                Text(
                    text = "DNS cache hits: $cacheHits / $totalQueries ($hitRate%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSystemVpnSettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("os_always_on_vpn_button")
                ) {
                    Text("OS Always-On")
                }
                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_unrestricted_button")
                ) {
                    Text(if (isUnrestricted) "Battery OK" else "Unrestrict")
                }
            }
        }
    }
}

@Composable
private fun NordApiKeyCard(
    uiState: MainUiState,
    onSwapApiKey: () -> Unit,
    onRetryCredentials: () -> Unit,
    onCopyWireGuardConfig: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nord_diagnostics_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NORD API KEY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val (badgeLabel, badgeColor) = when (uiState.credentialsState) {
                    is NordCredentialsState.Verified -> "VERIFIED" to EmeraldProtected
                    is NordCredentialsState.Loading -> "CHECKING" to ElectricCyan
                    is NordCredentialsState.Error -> "ERROR" to MaterialTheme.colorScheme.error
                    is NordCredentialsState.NotConfigured -> "NOT SET" to CloudflareAmber
                }
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = badgeColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (uiState.apiKeySource) {
                        ApiKeySource.CUSTOM_USER_KEY -> "Keystore"
                        ApiKeySource.BUILD_SECRET -> "Build Env"
                        ApiKeySource.NONE -> "None"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.maskedApiKeyPreview ?: "—",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            when (val cred = uiState.credentialsState) {
                is NordCredentialsState.Verified -> {
                    Text(
                        text = "NordLynx key ${cred.keyFingerprint}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = EmeraldProtected
                    )
                }
                is NordCredentialsState.Error -> {
                    Text(
                        text = cred.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is NordCredentialsState.NotConfigured -> {
                    Text(
                        text = "Add a NordVPN access token to connect WireGuard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NordCredentialsState.Loading -> {
                    Text(
                        text = "Verifying NordLynx credentials…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSwapApiKey,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("swap_api_key_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Swap Key")
                }

                OutlinedButton(
                    onClick = onCopyWireGuardConfig,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_wireguard_config_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy .conf")
                }
            }

            if (uiState.credentialsState is NordCredentialsState.Error) {
                TextButton(
                    onClick = onRetryCredentials,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("retry_credentials_button")
                ) {
                    Text("Retry Verification")
                }
            }
        }
    }
}

@Composable
private fun SwapApiKeyDialog(
    apiKeySource: ApiKeySource,
    maskedPreview: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClearCustom: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nord API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Encrypted on-device via Android Keystore (AES-256-GCM).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (maskedPreview != null) {
                    Text(
                        text = "Active: $maskedPreview",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = ElectricCyan
                    )
                }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Nord Access Token") },
                    placeholder = { Text("Paste hex token…") },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { revealKey = !revealKey }) {
                            Text(if (revealKey) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input_field")
                )
                if (apiKeySource == ApiKeySource.CUSTOM_USER_KEY) {
                    TextButton(
                        onClick = onClearCustom,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("clear_custom_api_key_button")
                    ) {
                        Text("Remove Stored Key")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKeyInput) },
                enabled = apiKeyInput.trim().length >= 16,
                modifier = Modifier.testTag("save_api_key_confirm_button")
            ) {
                Text("Save & Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
