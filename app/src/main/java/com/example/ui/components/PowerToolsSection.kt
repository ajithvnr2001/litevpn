package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionMode
import com.example.model.DnsPreset
import com.example.model.LeakCheckResult
import com.example.model.SavedVpnProfile
import com.example.model.isValidIpv4Address
import com.example.ui.MainUiState
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.JetBrainsMonoFamily

private val MTU_PRESETS = listOf(1280, 1360, 1400, 1420)

@Composable
fun DnsPresetsCard(
    uiState: MainUiState,
    onSelectDnsPreset: (DnsPreset) -> Unit,
    onSaveCustomDns: (String, String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showCustomDnsDialog by remember { mutableStateOf(false) }

    if (showCustomDnsDialog) {
        CustomDnsDialog(
            initialPrimary = uiState.customDnsPrimary.ifBlank { "94.140.14.14" },
            initialSecondary = uiState.customDnsSecondary.ifBlank { "94.140.15.15" },
            onDismiss = { showCustomDnsDialog = false },
            onSave = { primary, secondary ->
                if (onSaveCustomDns(primary, secondary)) {
                    showCustomDnsDialog = false
                }
            }
        )
    }

    val (activePrimary, activeSecondary) = uiState.effectiveDns

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dns_presets_card"),
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
                    text = "DNS FILTER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (activePrimary == activeSecondary) activePrimary else "$activePrimary / $activeSecondary",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = ElectricCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnsPresetChip(
                    title = "Mode Default",
                    subtitle = uiState.selectedMode.primaryDns,
                    selected = uiState.dnsPreset == DnsPreset.MODE_DEFAULT,
                    onClick = { onSelectDnsPreset(DnsPreset.MODE_DEFAULT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dns_preset_mode_default")
                )
                DnsPresetChip(
                    title = "AdGuard",
                    subtitle = "94.140.14.14",
                    selected = uiState.dnsPreset == DnsPreset.ADGUARD_ADS,
                    onClick = { onSelectDnsPreset(DnsPreset.ADGUARD_ADS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dns_preset_adguard")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnsPresetChip(
                    title = "Quad9",
                    subtitle = "9.9.9.9",
                    selected = uiState.dnsPreset == DnsPreset.QUAD9_SECURITY,
                    onClick = { onSelectDnsPreset(DnsPreset.QUAD9_SECURITY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dns_preset_quad9")
                )
                DnsPresetChip(
                    title = "Custom IPv4",
                    subtitle = uiState.customDnsPrimary.ifBlank { "Set IP" },
                    selected = uiState.dnsPreset == DnsPreset.CUSTOM,
                    onClick = { showCustomDnsDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dns_preset_custom")
                )
            }
        }
    }
}

@Composable
private fun DnsPresetChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(10.dp),
                focusColor = ElectricCyan,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected DNS",
                    tint = EmeraldProtected,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SavedProfilesCard(
    profiles: List<SavedVpnProfile>,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (SavedVpnProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveProfile(name)
                showSaveDialog = false
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_profiles_card"),
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
                    text = "PROFILES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("save_current_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (profiles.isEmpty()) {
                Text(
                    text = "No saved profiles yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { profile ->
                        val mode = ConnectionMode.fromId(profile.modeId)
                        val preset = DnsPreset.fromId(profile.dnsPresetId)
                        val detailParts = buildList {
                            add(mode.shortTitle)
                            if (preset != DnsPreset.MODE_DEFAULT) add(preset.shortLabel)
                            profile.pinnedServerCode?.let { add(it) }
                                ?: profile.cityName?.let { add(it) }
                            if (profile.lanBypassEnabled) add("LAN")
                            add("${profile.mtu}")
                        }.joinToString(" · ")

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusColor = ElectricCyan,
                                    onClick = { onApplyProfile(profile) }
                                )
                                .testTag("saved_profile_${profile.id}"),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = detailParts,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_profile_${profile.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${profile.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkTuningCard(
    uiState: MainUiState,
    onToggleLanBypass: (Boolean) -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onSelectMtu: (Int) -> Unit,
    onHopLowerLoadServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_tuning_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETWORK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "MTU ${uiState.customMtu}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = ElectricCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAN Bypass",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Keep local Cast, Plex, NAS & printers direct",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.lanBypassEnabled,
                    onCheckedChange = onToggleLanBypass,
                    modifier = Modifier.testTag("lan_bypass_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Failover",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Re-handshake on Wi-Fi/5G switch or stall",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.autoFailoverEnabled,
                    onCheckedChange = onToggleAutoFailover,
                    modifier = Modifier.testTag("auto_failover_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MTU (bytes)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MTU_PRESETS.forEach { mtuValue ->
                        val selected = uiState.customMtu == mtuValue
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusColor = ElectricCyan,
                                    onClick = { onSelectMtu(mtuValue) }
                                )
                                .testTag("mtu_chip_$mtuValue"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = "$mtuValue",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onHopLowerLoadServer,
                enabled = uiState.selectedMode.usesVpn && !uiState.isHoppingServer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hop_lower_load_server_button")
            ) {
                if (uiState.isHoppingServer) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Finding lower-load server…")
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hop to Lower-Load Server")
                }
            }
        }
    }
}

@Composable
fun IpLeakCheckCard(
    isRunning: Boolean,
    result: LeakCheckResult?,
    isVpnConnected: Boolean,
    onRunLeakCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ip_leak_check_card"),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IP & LEAK CHECK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Exit IP, IPv6 leak block & DNS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRunLeakCheck,
                    enabled = !isRunning,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("run_leak_check_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing…")
                    } else {
                        Text("Run Test")
                    }
                }
            }

            if (result != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (result.errorMessage != null) MaterialTheme.colorScheme.error
                        else if (result.ipMatchesNordServer || (isVpnConnected && result.ipv6LeakBlocked))
                            EmeraldProtected.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leak_check_result_panel")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (result.errorMessage != null) {
                            Text(
                                text = result.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.exitIp,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${result.countryCode} · ${result.colo}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val serverMatchLabel = when {
                                result.expectedServerIp == null -> "Direct DNS mode"
                                result.ipMatchesNordServer -> "Matches Nord server ${result.expectedServerIp}"
                                isVpnConnected -> "VPN active (expected ${result.expectedServerIp})"
                                else -> "Disconnected"
                            }
                            Text(
                                text = serverMatchLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.ipMatchesNordServer) EmeraldProtected
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (result.ipv6LeakBlocked) "IPv6 BLOCKED" else "IPv6 OPEN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (result.ipv6LeakBlocked) EmeraldProtected else CloudflareAmber
                                )
                                Text(
                                    text = result.activeDnsSummary,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigBackupCard(
    onExportBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit,
    onGetBackupJson: () -> String,
    onCopyBackupJson: (String) -> Unit,
    onImportBackupJson: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showJsonDialog by remember { mutableStateOf(false) }

    if (showJsonDialog) {
        JsonTransferDialog(
            exportedJson = onGetBackupJson(),
            onDismiss = { showJsonDialog = false },
            onCopy = { json -> onCopyBackupJson(json) },
            onImport = { raw ->
                if (onImportBackupJson(raw)) {
                    showJsonDialog = false
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("config_backup_card"),
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
                    text = "BACKUP JSON",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "SECRETS EXCLUDED",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = EmeraldProtected
                )
            }

            Text(
                text = "Export or import profiles, app routes, split lists, DNS, LAN bypass & MTU. API keys stay on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportBackupFile,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_backup_file_button")
                ) {
                    Text("Export")
                }
                OutlinedButton(
                    onClick = onImportBackupFile,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_backup_file_button")
                ) {
                    Text("Import")
                }
            }

            TextButton(
                onClick = { showJsonDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_json_paste_dialog_button")
            ) {
                Icon(
                    imageVector = AppIcons.Copy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy / Paste JSON (TV)")
            }
        }
    }
}

@Composable
private fun CustomDnsDialog(
    initialPrimary: String,
    initialSecondary: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var primary by remember { mutableStateOf(initialPrimary) }
    var secondary by remember { mutableStateOf(initialSecondary) }
    val primaryValid = isValidIpv4Address(primary)
    val secondaryValid = secondary.isBlank() || isValidIpv4Address(secondary)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom IPv4 DNS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text("Primary IPv4") },
                    placeholder = { Text("94.140.14.14") },
                    singleLine = true,
                    isError = primary.isNotBlank() && !primaryValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dns_primary_input")
                )
                OutlinedTextField(
                    value = secondary,
                    onValueChange = { secondary = it },
                    label = { Text("Secondary IPv4 (optional)") },
                    placeholder = { Text("94.140.15.15") },
                    singleLine = true,
                    isError = !secondaryValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dns_secondary_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(primary, secondary) },
                enabled = primaryValid && secondaryValid,
                modifier = Modifier.testTag("save_custom_dns_confirm")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaveProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text("Name") },
                placeholder = { Text("e.g. US TV + AdGuard") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_save_profile_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun JsonTransferDialog(
    exportedJson: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onImport: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf(exportedJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config JSON") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("JSON") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_json_text_field")
                )
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = { onCopy(exportedJson) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_backup_json_button")
                ) {
                    Text("Copy Current JSON")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonText) },
                enabled = jsonText.trim().startsWith("{"),
                modifier = Modifier.testTag("confirm_import_json_button")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
