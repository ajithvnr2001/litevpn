package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ServerCodeMatcher
import com.example.model.NordCountry
import com.example.model.NordServer
import com.example.model.RegionFilter
import com.example.ui.MainUiState
import com.example.ui.components.tvFocusableClickable
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected

private data class CitySearchMatch(
    val countryId: Int,
    val countryName: String,
    val countryFlag: String,
    val cityName: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationsScreen(
    uiState: MainUiState,
    onQueryChange: (String) -> Unit,
    onSelectRegion: (RegionFilter) -> Unit,
    onSelectLocation: (countryId: Int?, cityName: String?) -> Unit,
    onConnectLocation: (countryId: Int?, cityName: String?) -> Unit,
    onLoadCountryServers: (countryId: Int) -> Unit,
    onConnectServer: (server: NordServer, countryId: Int?) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onRetryServerCodeSearch: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = uiState.locationQuery.trim()
    val parsedCodeQuery = remember(query) { ServerCodeMatcher.parseQuery(query) }
    val isCodeSearchActive = uiState.activeServerCodeQuery != null || parsedCodeQuery != null
    val effectiveTextQuery = if (isCodeSearchActive) {
        parsedCodeQuery?.locationTextHint.orEmpty()
    } else {
        query
    }

    val filteredCountries = remember(
        uiState.countries,
        effectiveTextQuery,
        isCodeSearchActive,
        uiState.selectedRegion,
        uiState.favoriteCountryIds
    ) {
        if (isCodeSearchActive && effectiveTextQuery.isEmpty()) {
            emptyList()
        } else {
            uiState.countries.filter { country ->
                val regionOk = when (uiState.selectedRegion) {
                    RegionFilter.ALL -> true
                    RegionFilter.FAVORITES -> country.id in uiState.favoriteCountryIds
                    else -> country.region == uiState.selectedRegion
                }
                if (!regionOk) return@filter false
                if (effectiveTextQuery.isEmpty()) return@filter true
                country.name.contains(effectiveTextQuery, ignoreCase = true) ||
                    country.code.contains(effectiveTextQuery, ignoreCase = true) ||
                    country.cities.any { it.name.contains(effectiveTextQuery, ignoreCase = true) }
            }
        }
    }

    val cityMatches = remember(uiState.countries, effectiveTextQuery) {
        if (effectiveTextQuery.length < 2) emptyList()
        else {
            val out = ArrayList<CitySearchMatch>()
            for (country in uiState.countries) {
                for (city in country.cities) {
                    if (city.name.contains(effectiveTextQuery, ignoreCase = true)) {
                        out.add(
                            CitySearchMatch(
                                countryId = country.id,
                                countryName = country.name,
                                countryFlag = country.flagEmoji,
                                cityName = city.name
                            )
                        )
                        if (out.size >= 12) break
                    }
                }
                if (out.size >= 12) break
            }
            out
        }
    }

    var expandedCountryId by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
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
                        text = "Nord Locations",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val syncTag = if (uiState.isLiveDirectorySynced) "Live Nord API" else "Full Catalog"
                    Text(
                        text = "$syncTag • ${uiState.countries.size} countries • ${uiState.countries.sumOf { it.cities.size }} cities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_locations_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Nord locations"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = uiState.locationQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text("Search country, city, or #code (e.g. 13084, #12790, Tirana)…")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (uiState.locationQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag("clear_location_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(RegionFilter.entries) { region ->
                    FilterChip(
                        selected = uiState.selectedRegion == region,
                        onClick = { onSelectRegion(region) },
                        label = { Text(region.label) },
                        modifier = Modifier.testTag("region_chip_${region.name.lowercase()}")
                    )
                }
            }

            if (uiState.isLoadingCountries) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Live NordVPN Server Number / #Code Lookup Results
                if (isCodeSearchActive) {
                    item(key = "server_code_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isSearchingServerCode) {
                                        "Searching Server Code ${uiState.activeServerCodeQuery ?: query}…"
                                    } else {
                                        "Server Code Matches (${uiState.serverCodeResults.size})"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                                if (uiState.indexedServerCount > 0) {
                                    Text(
                                        text = "${uiState.indexedServerCount} live NordLynx servers indexed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!uiState.isSearchingServerCode) {
                                OutlinedButton(
                                    onClick = onRetryServerCodeSearch,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("refresh_server_code_button")
                                ) {
                                    Text("Resync")
                                }
                            }
                        }
                    }

                    if (uiState.isSearchingServerCode) {
                        item(key = "server_code_loading") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Scanning live NordVPN NordLynx fleet for ${uiState.activeServerCodeQuery ?: query}…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    } else if (uiState.serverCodeResults.isNotEmpty()) {
                        items(
                            items = uiState.serverCodeResults,
                            key = { "code_${it.id}_${it.hostname}" }
                        ) { srv ->
                            val resolvedCountryId = uiState.countries.firstOrNull {
                                it.code.equals(srv.countryCode, ignoreCase = true)
                            }?.id
                            ServerCodeMatchCard(
                                server = srv,
                                onConnect = { onConnectServer(srv, resolvedCountryId) }
                            )
                        }
                    } else {
                        item(key = "server_code_empty") {
                            EmptyServerCodeState(
                                query = uiState.activeServerCodeQuery ?: query,
                                indexedCount = uiState.indexedServerCount,
                                errorText = uiState.serverCodeSearchError,
                                onRetry = onRetryServerCodeSearch
                            )
                        }
                    }
                }

                // Direct City Search Quick-Connect Strip
                if (cityMatches.isNotEmpty()) {
                    item(key = "city_quick_matches") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Quick Connect by City",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cityMatches, key = { "${it.countryId}_${it.cityName}" }) { match ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .tvFocusableClickable(
                                                shape = RoundedCornerShape(14.dp),
                                                onClick = { onConnectLocation(match.countryId, match.cityName) }
                                            ),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = match.countryFlag)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = match.cityName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Connect • ${match.countryName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Nearest Geo-Recommended Nord Locations
                if (query.isEmpty() && uiState.selectedRegion == RegionFilter.ALL && uiState.nearestServers.isNotEmpty()) {
                    item(key = "nearest_geo_strip") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Nearest Low-Load Nord Locations",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldProtected
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.nearestServers, key = { it.id }) { srv ->
                                    val countryId = uiState.countries.firstOrNull {
                                        it.code.equals(srv.countryCode, ignoreCase = true)
                                    }?.id
                                    NearestServerTile(
                                        server = srv,
                                        onConnect = { onConnectServer(srv, countryId) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (query.isEmpty() && uiState.selectedRegion == RegionFilter.ALL) {
                    item(key = "fastest_auto") {
                        FastestLocationBanner(
                            selected = uiState.selectedCountryId == null,
                            onSelect = { onSelectLocation(null, null) },
                            onConnectNow = { onConnectLocation(null, null) }
                        )
                    }
                }

                if (filteredCountries.isEmpty() && !uiState.isLoadingCountries && !isCodeSearchActive) {
                    item(key = "empty_locations") {
                        EmptyLocationsState(
                            hasError = uiState.countriesError != null,
                            errorText = uiState.countriesError,
                            onRefresh = onRefresh
                        )
                    }
                } else if (filteredCountries.isNotEmpty()) {
                    items(
                        items = filteredCountries,
                        key = { it.id }
                    ) { country ->
                        val isSelected = uiState.selectedCountryId == country.id
                        val isFavorite = country.id in uiState.favoriteCountryIds
                        val isExpanded = expandedCountryId == country.id ||
                            (effectiveTextQuery.isNotEmpty() && country.cities.any {
                                it.name.contains(effectiveTextQuery, ignoreCase = true)
                            })
                        val countryServers = if (uiState.browsedCountryId == country.id) {
                            uiState.browsedServers
                        } else {
                            emptyList()
                        }
                        val loadingServers = uiState.isLoadingBrowsedServers && uiState.browsedCountryId == country.id

                        CountryLocationCard(
                            country = country,
                            query = effectiveTextQuery,
                            isSelected = isSelected,
                            selectedCityName = if (isSelected) uiState.selectedCityName else null,
                            isFavorite = isFavorite,
                            isExpanded = isExpanded,
                            liveServers = countryServers,
                            isLoadingLiveServers = loadingServers,
                            onSelectCountry = { onSelectLocation(country.id, null) },
                            onConnectCountry = { onConnectLocation(country.id, null) },
                            onConnectCity = { cityName -> onConnectLocation(country.id, cityName) },
                            onLoadLiveServers = { onLoadCountryServers(country.id) },
                            onConnectSpecificServer = { srv -> onConnectServer(srv, country.id) },
                            onToggleExpand = {
                                expandedCountryId = if (expandedCountryId == country.id) null else country.id
                            },
                            onToggleFavorite = { onToggleFavorite(country.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearestServerTile(
    server: NordServer,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .tvFocusableClickable(shape = RoundedCornerShape(16.dp), onClick = onConnect)
            .testTag("nearest_server_${server.hostname}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EmeraldProtected.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = server.flagEmoji, style = MaterialTheme.typography.titleLarge)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldProtected.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${server.loadPercent}% load",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldProtected,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = server.cityName.ifBlank { server.countryName },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = server.hostname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FastestLocationBanner(
    selected: Boolean,
    onSelect: () -> Unit,
    onConnectNow: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .tvFocusableClickable(shape = RoundedCornerShape(18.dp), onClick = onSelect)
            .testTag("fastest_location_item"),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) ElectricCyan else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElectricCyan.copy(alpha = 0.16f)
            ) {
                Icon(
                    imageVector = AppIcons.Bolt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fastest Available Server",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Auto-pick lowest-load NordLynx server globally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConnectNow,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("connect_fastest_button")
            ) {
                Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryLocationCard(
    country: NordCountry,
    query: String,
    isSelected: Boolean,
    selectedCityName: String?,
    isFavorite: Boolean,
    isExpanded: Boolean,
    liveServers: List<NordServer>,
    isLoadingLiveServers: Boolean,
    onSelectCountry: () -> Unit,
    onConnectCountry: () -> Unit,
    onConnectCity: (String) -> Unit,
    onLoadLiveServers: () -> Unit,
    onConnectSpecificServer: (NordServer) -> Unit,
    onToggleExpand: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .testTag("country_item_${country.code.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(18.dp),
                        scaleOnFocus = 1.01f,
                        onClick = onSelectCountry
                    )
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = country.flagEmoji,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = country.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        append(country.code)
                        if (country.cities.isNotEmpty()) {
                            append(" • ")
                            append("${country.cities.size} ")
                            append(if (country.cities.size == 1) "city" else "cities")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else AppIcons.StarOutline,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (isFavorite) CloudflareAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (isExpanded) "Collapse details" else "Expand cities and servers"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConnectCountry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_country_${country.code.lowercase()}")
                        ) {
                            Text("Connect ${country.code}")
                        }
                        OutlinedButton(
                            onClick = onLoadLiveServers,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browse_servers_${country.code.lowercase()}")
                        ) {
                            Text(if (isLoadingLiveServers) "Loading…" else "Live Servers")
                        }
                    }

                    if (country.cities.isNotEmpty()) {
                        val matchingCities = if (query.isBlank()) {
                            country.cities
                        } else {
                            val filtered = country.cities.filter { it.name.contains(query, ignoreCase = true) }
                            filtered.ifEmpty { country.cities }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            matchingCities.forEach { city ->
                                val citySelected = selectedCityName.equals(city.name, ignoreCase = true)
                                AssistChip(
                                    onClick = { onConnectCity(city.name) },
                                    label = { Text("⚡ ${city.name}") },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (citySelected) ElectricCyan else MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }

                    if (isLoadingLiveServers) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (liveServers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Live NordLynx Servers (${liveServers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                            liveServers.take(15).forEach { srv ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(12.dp),
                                            scaleOnFocus = 1.01f,
                                            onClick = { onConnectSpecificServer(srv) }
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = ElectricCyan.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = srv.serverCodeTag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ElectricCyan,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = srv.cityName.ifBlank { srv.countryName },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${srv.hostname} • ${srv.stationIp} • Load ${srv.loadPercent}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { onConnectSpecificServer(srv) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldProtected.copy(alpha = 0.2f),
                                                contentColor = EmeraldProtected
                                            )
                                        ) {
                                            Text("Use")
                                        }
                                    }
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
private fun EmptyLocationsState(
    hasError: Boolean,
    errorText: String?,
    onRefresh: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = AppIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (hasError) {
                    errorText ?: "Could not load Nord locations"
                } else {
                    "No locations match your search or filter."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasError) {
                Button(onClick = onRefresh) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ServerCodeMatchCard(
    server: NordServer,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(18.dp),
                scaleOnFocus = 1.015f,
                onClick = onConnect
            )
            .testTag("server_code_match_${server.hostname}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, ElectricCyan.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = server.flagEmoji,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = ElectricCyan.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = server.serverCodeTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = server.locationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${server.name.ifBlank { server.hostname }} • ${server.hostname}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldProtected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${server.stationIp} • Load ${server.loadPercent}% • WireGuard NordLynx",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConnect,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("connect_code_${server.hostname}")
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun EmptyServerCodeState(
    query: String,
    indexedCount: Int,
    errorText: String?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = errorText ?: "No live NordLynx server found for \"$query\"" +
                    if (indexedCount > 0) " across $indexedCount active servers." else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Try a server number (e.g. 13084 or #12790), a range (#12790-12792), or a city name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("retry_server_code_search")
            ) {
                Text("Refresh Live Directory")
            }
        }
    }
}
