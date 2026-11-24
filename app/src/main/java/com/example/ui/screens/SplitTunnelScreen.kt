package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.InstalledAppInfo
import com.example.model.SplitTunnelMode
import com.example.ui.MainUiState
import com.example.ui.components.tvFocusableClickable
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected

@Composable
fun SplitTunnelScreen(
    uiState: MainUiState,
    onSelectSplitMode: (SplitTunnelMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSelectedOnly: () -> Unit,
    onClearSelected: () -> Unit,
    onApplyReconfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePackages = uiState.activeSplitPackages
    val query = uiState.appSearchQuery.trim()

    val filteredApps = remember(
        uiState.installedApps,
        query,
        uiState.showSelectedAppsOnly,
        activePackages
    ) {
        uiState.installedApps.filter { app ->
            val matchSelected = !uiState.showSelectedAppsOnly || app.packageName in activePackages
            if (!matchSelected) return@filter false
            if (query.isEmpty()) return@filter true
            app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Split Tunneling",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Control which apps bypass or use the tunnel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.splitMode != SplitTunnelMode.ALL_APPS && activePackages.isNotEmpty()) {
                    TextButton(
                        onClick = onClearSelected,
                        modifier = Modifier.testTag("clear_split_apps_button")
                    ) {
                        Text("Clear (${activePackages.size})")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Way Mode Selector: All Apps | Exclude | Include Only
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SplitTunnelMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.splitMode == mode,
                        onClick = { onSelectSplitMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SplitTunnelMode.entries.size
                        ),
                        modifier = Modifier.testTag("split_mode_${mode.id}")
                    ) {
                        Text(
                            text = mode.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val modeAccent = when (uiState.splitMode) {
                SplitTunnelMode.ALL_APPS -> ElectricCyan
                SplitTunnelMode.EXCLUDE_APPS -> CloudflareAmber
                SplitTunnelMode.INCLUDE_ONLY_APPS -> EmeraldProtected
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = modeAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, modeAccent.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.SplitRoute,
                        contentDescription = null,
                        tint = modeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.splitMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Split rules changed while active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onApplyReconfigure,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("split_apply_reconnect_button")
                            ) {
                                Text("Apply Now")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.appSearchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Search installed apps…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.appSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear app search"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.showSelectedAppsOnly,
                    onClick = onToggleSelectedOnly,
                    label = {
                        Text("Selected Only (${activePackages.size})")
                    },
                    modifier = Modifier.testTag("filter_selected_apps_chip")
                )

                Text(
                    text = "${filteredApps.size} apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isLoadingApps) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (filteredApps.isEmpty() && !uiState.isLoadingApps) {
                    item(key = "empty_apps") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.SplitApps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No matching apps found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        val isChecked = app.packageName in activePackages
                        AppSplitRow(
                            app = app,
                            splitMode = uiState.splitMode,
                            checked = isChecked,
                            onToggle = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSplitRow(
    app: InstalledAppInfo,
    splitMode: SplitTunnelMode,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val imageBitmap = remember(app.packageName, app.icon) {
        app.icon?.asImageBitmap()
    }
    val highlightColor = when (splitMode) {
        SplitTunnelMode.EXCLUDE_APPS -> CloudflareAmber
        SplitTunnelMode.INCLUDE_ONLY_APPS -> EmeraldProtected
        SplitTunnelMode.ALL_APPS -> ElectricCyan
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(16.dp),
                focusColor = highlightColor,
                scaleOnFocus = 1.015f,
                onClick = onToggle
            )
            .testTag("split_app_row_${app.packageName}"),
        shape = RoundedCornerShape(16.dp),
        color = if (checked) {
            highlightColor.copy(alpha = 0.11f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (checked) 1.3.dp else 1.dp,
            color = if (checked) highlightColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.SplitApps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
