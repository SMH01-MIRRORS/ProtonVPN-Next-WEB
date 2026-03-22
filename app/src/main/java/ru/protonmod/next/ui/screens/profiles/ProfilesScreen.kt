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

package ru.protonmod.next.ui.screens.profiles

import android.app.Activity
import android.net.VpnService
import ru.protonmod.next.utils.ProtonLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateToHome: () -> Unit,
    onCreateNewProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val isTablet = isTablet()

    // Collect profiles from ViewModel
    val profiles by viewModel.profiles.collectAsState()

    // VPN Permission Launcher
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("ProfilesScreen", "VPN permission granted")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
        }
    }

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
            // Fallback if AppOps permission is missing, proceed anyway
            connectAction()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!isTablet) {
                FloatingActionButton(
                    onClick = onCreateNewProfile,
                    containerColor = colors.brandNorm,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 130.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.desc_create_profile))
                }
            }
        },
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration (immersive)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profiles_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm
                    )

                    if (isTablet) {
                        Button(
                            onClick = onCreateNewProfile,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.desc_create_profile))
                        }
                    }
                }

                if (profiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyProfilesState()
                    }
                } else if (isTablet) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileCardItem(
                                profile = profile,
                                onConnect = {
                                    checkVpnAndConnect {
                                        viewModel.connectWithProfile(profile)
                                        onNavigateToHome()
                                    }
                                },
                                onEdit = { onEditProfile(profile.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileCardItem(
                                profile = profile,
                                onConnect = {
                                    checkVpnAndConnect {
                                        viewModel.connectWithProfile(profile)
                                        onNavigateToHome()
                                    }
                                },
                                onEdit = { onEditProfile(profile.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCardItem(
    profile: VpnProfileUiModel,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.4f, shadowElevation = 0.dp)
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Icon / Target Indicator
            Box(
                modifier = Modifier
                    .size(48.dp, 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.brandNorm.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    profile.targetCountry != null -> {
                        val flagResId = CountryUtils.getFlagResource(context, profile.targetCountry)
                        if (flagResId != 0) {
                            FlagIcon(
                                countryFlag = flagResId,
                                size = DpSize(48.dp, 32.dp)
                            )
                        } else {
                            Text(
                                text = CountryUtils.getFlagForCountry(profile.targetCountry),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    else -> {
                        FlagIcon(
                            countryFlag = R.drawable.flag_fastest,
                            size = DpSize(48.dp, 32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Profile Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle with protocol, port, and target info
                val portStr = if (profile.port == 0) stringResource(R.string.settings_port_auto) else profile.port.toString()
                val targetName = when {
                    profile.targetServerId != null -> stringResource(R.string.profile_server_info, profile.targetServerName ?: profile.targetServerId)
                    profile.targetCity != null -> stringResource(R.string.profile_city_info, profile.targetCity, CountryUtils.getCountryName(context, profile.targetCountry!!))
                    profile.targetCountry != null -> {
                        val flagEmoji = CountryUtils.getFlagForCountry(profile.targetCountry)
                        val localizedCountryName = CountryUtils.getCountryName(context, profile.targetCountry)
                        stringResource(R.string.profile_country_info, flagEmoji, localizedCountryName)
                    }
                    else -> stringResource(R.string.profile_fastest_info, stringResource(R.string.location_fastest))
                }

                Text(
                    text = "${profile.protocol} • $portStr • $targetName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak
                )

                // Show indicators if special features are enabled
                if (profile.isObfuscationEnabled || !profile.autoOpenUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.isObfuscationEnabled) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_obfuscation))
                        }
                        if (!profile.autoOpenUrl.isNullOrEmpty()) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_connect_go))
                        }
                    }
                }
            }

            // Edit Profile Button
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.desc_edit_profile),
                    tint = colors.iconWeak
                )
            }
        }
    }
}

@Composable
fun FeatureBadge(text: String) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.brandNorm.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.brandNorm
        )
    }
}

@Composable
fun EmptyProfilesState(modifier: Modifier = Modifier) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.VpnKey,
            contentDescription = null,
            tint = colors.iconWeak.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textWeak
        )
        Text(
            text = stringResource(R.string.profiles_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}
