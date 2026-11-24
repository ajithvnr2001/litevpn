package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppRouteRule
import com.example.model.InstalledAppInfo
import com.example.model.NordCountry
import com.example.model.SplitTunnelMode
import com.example.ui.MainUiState
import com.example.ui.components.tvFocusableClickable
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SplitTunnelScreen(
    uiState: MainUiState,
    onSelectSplitMode: (SplitTunnelMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onSetAppCountryRoute: (packageName: String, country: NordCountry, cityName: String?) -> Unit,
    onClearAppCountryRoute: (packageName: String) -> Unit,
    onClearAllAppCountryRoutes: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSelectedOnly: () -> Unit,
    onClearSelected: () -> Unit,
    onApplyReconfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePackages = uiState.activeSplitPackages
    val rulesByPackage = remember(uiState.appRouteRules) {
        uiState.appRouteRules.associateBy { it.packageName }
    }
    val query = uiState.appSearchQuery.trim()
    val multiRouteSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    var pickingForApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    pickingForApp?.let { targetApp ->
        AppCountryRoutePickerDialog(
            appName = targetApp.appName,
            countries = uiState.countries,
            currentRule = rulesByPackage[targetApp.packageName],
            onDismiss = { pickingForApp = null },
            onUseDefault = {
                onClearAppCountryRoute(targetApp.packageName)
                pickingForApp = null
            },
            onSelectCountry = { country, cityName ->
                onSetAppCountryRoute(targetApp.packageName, country, cityName)
                pickingForApp = null
            }
        )
    }

    val filteredApps = remember(
        uiState.installedApps,
        query,
        uiState.showSelectedAppsOnly,
        activePackages,
        rulesByPackage
    ) {
        uiState.installedApps.filter { app ->
            val isRelevant = app.packageName in activePackages || app.packageName in rulesByPackage
            val matchSelected = !uiState.showSelectedAppsOnly || isRelevant
            if (!matchSelected) return@filter false
            if (query.isEmpty()) return@filter true
            app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
    }

    val totalSelectedOrRouted = remember(activePackages, rulesByPackage) {
        (activePackages + rulesByPackage.keys).size
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Split tunnel & per-app country routes",
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

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Active Per-App Country Routes Strip
            if (uiState.appRouteRules.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_app_routes_card"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, EmeraldProtected.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PER-APP ROUTES (${uiState.appRouteRules.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = EmeraldProtected
                            )
                            TextButton(
                                onClick = onClearAllAppCountryRoutes,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("clear_all_app_routes_button")
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.appRouteRules, key = { it.packageName }) { rule ->
                                val appTitle = uiState.installedApps
                                    .firstOrNull { it.packageName == rule.packageName }
                                    ?.appName ?: rule.packageName.substringAfterLast('.')
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(8.dp),
                                            focusColor = ElectricCyan,
                                            onClick = { onClearAppCountryRoute(rule.packageName) }
                                        )
                                        .testTag("app_route_chip_${rule.packageName}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "$appTitle → ${rule.shortTargetLabel}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove route for $appTitle",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (!multiRouteSupported) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CloudflareAmber.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Per-app country routes require Android 10+. Split tunnel works normally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                Column {
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
                                text = "App routes updated while connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onApplyReconfigure,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("split_apply_reconnect_button")
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            OutlinedTextField(
                value = uiState.appSearchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.appSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.showSelectedAppsOnly,
                    onClick = onToggleSelectedOnly,
                    label = {
                        Text("Active ($totalSelectedOrRouted)")
                    },
                    modifier = Modifier.testTag("filter_selected_apps_chip")
                )

                Text(
                    text = "${filteredApps.size} apps",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isLoadingApps) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (filteredApps.isEmpty() && !uiState.isLoadingApps) {
                    item(key = "empty_apps") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.SplitApps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "No matching apps",
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
                        val routeRule = rulesByPackage[app.packageName]
                        AppSplitRow(
                            app = app,
                            splitMode = uiState.splitMode,
                            checked = isChecked,
                            routeRule = routeRule,
                            multiRouteSupported = multiRouteSupported,
                            onPickRoute = { pickingForApp = app },
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
    routeRule: AppRouteRule?,
    multiRouteSupported: Boolean,
    onPickRoute: () -> Unit,
    onToggle: () -> Unit
) {
    val imageBitmap = remember(app.packageName, app.icon) {
        app.icon?.asImageBitmap()
    }
    val hasCustomRoute = routeRule != null
    val borderColor = when {
        hasCustomRoute -> EmeraldProtected.copy(alpha = 0.65f)
        checked && splitMode == SplitTunnelMode.EXCLUDE_APPS -> CloudflareAmber.copy(alpha = 0.65f)
        checked -> EmeraldProtected.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("split_app_row_${app.packageName}"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.SplitApps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (multiRouteSupported) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(8.dp),
                            focusColor = ElectricCyan,
                            onClick = onPickRoute
                        )
                        .testTag("route_picker_${app.packageName}"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasCustomRoute) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (hasCustomRoute) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = routeRule?.shortTargetLabel ?: "Default",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = if (hasCustomRoute) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (hasCustomRoute) EmeraldProtected else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("split_switch_${app.packageName}")
            )
        }
    }
}

@Composable
private fun AppCountryRoutePickerDialog(
    appName: String,
    countries: List<NordCountry>,
    currentRule: AppRouteRule?,
    onDismiss: () -> Unit,
    onUseDefault: () -> Unit,
    onSelectCountry: (NordCountry, String?) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(countries, search) {
        val q = search.trim()
        if (q.isEmpty()) countries
        else countries.filter { c ->
            c.name.contains(q, ignoreCase = true) ||
                c.code.contains(q, ignoreCase = true) ||
                c.cities.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Route $appName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Country or city (e.g. India, US)…") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_route_country_search")
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(8.dp),
                            focusColor = ElectricCyan,
                            onClick = onUseDefault
                        )
                        .testTag("app_route_use_default"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (currentRule == null) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = "Use Default Tunnel",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (currentRule == null) EmeraldProtected else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { country ->
                        val selected = currentRule?.countryId == country.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusColor = ElectricCyan,
                                    onClick = { onSelectCountry(country, null) }
                                )
                                .testTag("pick_route_country_${country.code.lowercase()}"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (selected) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = country.flagEmoji)
                                    Text(
                                        text = country.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = country.code,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
