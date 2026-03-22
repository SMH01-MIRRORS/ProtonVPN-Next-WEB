/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens.countries

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.LoadIndicator
import ru.protonmod.next.ui.components.LoadProgressBar
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet
import ru.protonmod.next.utils.ProtonLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onNavigateToHome: () -> Unit,
    onBack: () -> Unit,
    viewModel: CountriesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val context = LocalContext.current
    val isTablet = isTablet()

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("CountriesScreen", "VPN permission granted")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
        }
    }

    val errorAppOpsMsg = stringResource(R.string.error_system_appops)

    val checkVpnAndConnect: (() -> Unit) -> Unit = { connectAction ->
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingAction = connectAction
                vpnPermissionLauncher.launch(intent)
            } else {
                connectAction()
            }
        } catch (_: SecurityException) {
            Toast.makeText(context, errorAppOpsMsg, Toast.LENGTH_LONG).show()
            connectAction()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.backgroundNorm,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.backgroundNorm.copy(alpha = 0.1f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Immersive Navigation Header
                AnimatedContent(
                    targetState = uiState,
                    label = "navigation_header"
                ) { state ->
                    val navigationModifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)

                    when (state) {
                        is CountriesUiState.CountriesList -> {
                            Text(
                                text = stringResource(R.string.countries_title),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textNorm,
                                modifier = navigationModifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
                            )
                        }
                        else -> {
                            Row(
                                modifier = navigationModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    when (uiState) {
                                        is CountriesUiState.CitiesList -> viewModel.backToCountries()
                                        is CountriesUiState.ServersList -> viewModel.backToCities()
                                        else -> onBack()
                                    }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.desc_back_button),
                                        tint = colors.textNorm
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val title = when (state) {
                                    is CountriesUiState.CitiesList -> state.country
                                    is CountriesUiState.ServersList -> "${state.country}, ${state.city}"
                                    else -> ""
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textNorm
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = uiState,
                    label = "countries_state",
                    modifier = Modifier.weight(1f)
                ) { state ->
                    when (state) {
                        is CountriesUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.brandNorm)
                            }
                        }
                        is CountriesUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.message, color = colors.notificationError)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.loadServers() },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.interactionNorm)
                                    ) {
                                        Text(stringResource(R.string.btn_retry), color = colors.textInverted)
                                    }
                                }
                            }
                        }
                        is CountriesUiState.CountriesList -> {
                            CountriesListContent(
                                countries = state.countries,
                                connectedServer = connectedServer,
                                isTablet = isTablet,
                                onCountryClick = { country ->
                                    checkVpnAndConnect {
                                        viewModel.selectCountry(country.code)
                                        onNavigateToHome()
                                    }
                                },
                                onCountryMore = { country ->
                                    viewModel.expandCitiesForCountry(country.code)
                                }
                            )
                        }
                        is CountriesUiState.CitiesList -> {
                            CitiesListContent(
                                countryName = state.country,
                                cities = state.cities,
                                connectedServer = connectedServer,
                                isTablet = isTablet,
                                onCityClick = { city ->
                                    checkVpnAndConnect {
                                        viewModel.selectCity(city.name)
                                        onNavigateToHome()
                                    }
                                },
                                onCityMore = { city ->
                                    viewModel.expandServersForCity(city.name)
                                }
                            )
                        }
                        is CountriesUiState.ServersList -> {
                            ServersListContent(
                                servers = state.servers,
                                connectedServer = connectedServer,
                                isTablet = isTablet,
                                onServerClick = { server ->
                                    checkVpnAndConnect {
                                        viewModel.selectServer(server)
                                        onNavigateToHome()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountriesListContent(
    countries: List<CountryDisplayItem>,
    connectedServer: LogicalServer?,
    isTablet: Boolean = false,
    onCountryClick: (CountryDisplayItem) -> Unit,
    onCountryMore: (CountryDisplayItem) -> Unit
) {
    if (isTablet) {
        val configuration = LocalConfiguration.current
        val columns = (configuration.screenWidthDp / 300).coerceAtLeast(2)
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 140.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(countries) { country ->
                CountryCard(
                    country = country,
                    isConnected = connectedServer?.exitCountry == country.code,
                    onClick = { onCountryClick(country) },
                    onMoreClick = { onCountryMore(country) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(countries) { country ->
                CountryCard(
                    country = country,
                    isConnected = connectedServer?.exitCountry == country.code,
                    onClick = { onCountryClick(country) },
                    onMoreClick = { onCountryMore(country) }
                )
            }
        }
    }
}

@Composable
fun CountryCard(
    country: CountryDisplayItem,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val flagResId = CountryUtils.getFlagResource(context, country.code)
    val localizedName = CountryUtils.getCountryName(context, country.code)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (flagResId != 0) {
                        FlagIcon(
                            countryFlag = flagResId,
                            size = DpSize(36.dp, 24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp, 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .offset(x = 4.dp, y = 4.dp)
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                                .padding(2.dp)
                                .background(colors.backgroundNorm, CircleShape)
                                .padding(1.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = localizedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = country.averageLoad)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = country.averageLoad)
        }
    }
}

@Composable
fun CitiesListContent(
    countryName: String,
    cities: List<CityDisplayItem>,
    connectedServer: LogicalServer?,
    isTablet: Boolean = false,
    onCityClick: (CityDisplayItem) -> Unit,
    onCityMore: (CityDisplayItem) -> Unit
) {
    if (isTablet) {
        val configuration = LocalConfiguration.current
        val columns = (configuration.screenWidthDp / 300).coerceAtLeast(2)

        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(cities) { city ->
                    CityCard(
                        city = city,
                        isConnected = (connectedServer?.city == city.name && connectedServer.exitCountry == countryName),
                        onClick = { onCityClick(city) },
                        onMoreClick = { onCityMore(city) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cities) { city ->
                CityCard(
                    city = city,
                    isConnected = (connectedServer?.city == city.name && connectedServer.exitCountry == countryName),
                    onClick = { onCityClick(city) },
                    onMoreClick = { onCityMore(city) }
                )
            }
        }
    }
}

@Composable
fun CityCard(
    city: CityDisplayItem,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.backgroundNorm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = null,
                            tint = colors.iconNorm,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .offset(x = 4.dp, y = 4.dp)
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                                .padding(2.dp)
                                .background(colors.backgroundNorm, CircleShape)
                                .padding(1.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = city.averageLoad)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = city.averageLoad)
        }
    }
}

@Composable
fun ServersListContent(
    servers: List<LogicalServer>,
    connectedServer: LogicalServer?,
    isTablet: Boolean = false,
    onServerClick: (LogicalServer) -> Unit
) {
    if (isTablet) {
        val configuration = LocalConfiguration.current
        val columns = (configuration.screenWidthDp / 300).coerceAtLeast(2)

        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(servers) { server ->
                    ServerItemCard(
                        server = server,
                        isConnected = connectedServer?.id == server.id,
                        onClick = { onServerClick(server) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(servers) { server ->
                ServerItemCard(
                    server = server,
                    isConnected = connectedServer?.id == server.id,
                    onClick = { onServerClick(server) }
                )
            }
        }
    }
}

@Composable
fun ServerItemCard(
    server: LogicalServer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadIndicator(load = server.averageLoad)
                    if (isConnected) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }
            }
            LoadProgressBar(load = server.averageLoad)
        }
    }
}
