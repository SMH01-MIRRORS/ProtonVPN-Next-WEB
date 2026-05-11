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

package ru.protonmod.next.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedWifiScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    val currentNetworkName = remember(hasLocationPermission, uiState.trustedWifiNetworks) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            if (!hasLocationPermission) return@remember null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = caps.transportInfo as? WifiInfo
                wifiInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
            }
        } else if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.settings_trusted_wifi),
                        onBack = onBack
                    )
                }

                if (!hasLocationPermission) {
                    item(contentType = "PermissionBanner") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.notificationWarning.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.trusted_wifi_permission_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.notificationWarning,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.trusted_wifi_permission_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.notificationWarning
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.notificationWarning)
                                ) {
                                    Text(stringResource(R.string.btn_allow))
                                }
                            }
                        }
                    }
                }

                item(contentType = "Info") {
                    InfoCard(text = stringResource(R.string.trusted_wifi_desc))
                }

                item(contentType = "Toggle") {
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_auto_connect_untrusted),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textNorm
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_connect_untrusted_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textWeak
                                )
                            }
                            Switch(
                                checked = uiState.autoConnectOnUntrusted,
                                onCheckedChange = { viewModel.setAutoConnectOnUntrusted(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.textInverted,
                                    checkedTrackColor = colors.brandNorm
                                )
                            )
                        }
                    }
                }

                item(contentType = "SubHeader") {
                    CategoryHeader(title = stringResource(R.string.trusted_wifi_title))
                }

                if (currentNetworkName != null && !uiState.trustedWifiNetworks.contains(currentNetworkName)) {
                    item(contentType = "AddCurrent") {
                        Button(
                            onClick = { viewModel.addTrustedWifi(currentNetworkName) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.trusted_wifi_add, currentNetworkName))
                        }
                    }
                }

                if (uiState.trustedWifiNetworks.isEmpty()) {
                    item(contentType = "EmptyState") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.trusted_wifi_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textWeak
                            )
                        }
                    }
                } else {
                    items(uiState.trustedWifiNetworks.toList(), key = { it }, contentType = { "Network" }) { ssid ->
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, tint = colors.brandNorm)
                                    Spacer(Modifier.width(16.dp))
                                    Text(text = ssid, color = colors.textNorm, fontWeight = FontWeight.Medium)
                                }
                                IconButton(onClick = { viewModel.removeTrustedWifi(ssid) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.notificationError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
