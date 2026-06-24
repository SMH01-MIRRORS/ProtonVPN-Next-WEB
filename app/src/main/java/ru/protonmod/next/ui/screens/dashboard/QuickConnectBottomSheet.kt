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

package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ru.protonmod.next.R
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.ServerCard
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectBottomSheet(
    onDismiss: () -> Unit,
    currentStrategy: String,
    currentTargetId: String?,
    profiles: ImmutableList<VpnProfileEntity>,
    recentServers: ImmutableList<ru.protonmod.next.data.network.LogicalServer>,
    onStrategySelect: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.backgroundNorm,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.iconWeak) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.title_quick_connect_config),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textNorm,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(contentType = "Strategy") {
                    StrategyItem(
                        title = stringResource(R.string.qc_strategy_fastest),
                        description = stringResource(R.string.qc_strategy_fastest_desc),
                        flagResId = R.drawable.flag_fastest,
                        isSelected = currentStrategy == "fastest",
                        onClick = {
                            onStrategySelect("fastest", null)
                            onDismiss()
                        }
                    )
                }

                item(contentType = "Strategy") {
                    StrategyItem(
                        title = stringResource(R.string.qc_strategy_recent),
                        description = stringResource(R.string.qc_strategy_recent_desc),
                        icon = Icons.Rounded.History,
                        isSelected = currentStrategy == "recent",
                        onClick = {
                            onStrategySelect("recent", null)
                            onDismiss()
                        }
                    )
                }

                if (profiles.isNotEmpty()) {
                    item(contentType = "Header") {
                        Text(
                            text = stringResource(R.string.qc_header_profiles),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.brandNorm,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }

                    items(profiles, key = { it.id }, contentType = { "ProfileStrategy" }) { profile ->
                        StrategyItem(
                            title = profile.name,
                            description = stringResource(R.string.qc_strategy_profile_desc),
                            icon = Icons.Rounded.Star,
                            isSelected = currentStrategy == "profile" && currentTargetId == profile.id,
                            onClick = {
                                onStrategySelect("profile", profile.id)
                                onDismiss()
                            }
                        )
                    }
                }

                if (recentServers.isNotEmpty()) {
                    item(contentType = "Header") {
                        Text(
                            text = stringResource(R.string.qc_header_recent),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.brandNorm,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }

                    items(recentServers, key = { it.id }, contentType = { "ServerStrategy" }) { server ->
                        ServerCard(
                            server = server,
                            isConnected = currentStrategy == "server" && currentTargetId == server.id,
                            isConnecting = false,
                            onClick = {
                                onStrategySelect("server", server.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyItem(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    flagResId: Int = 0
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = if (isSelected) 0.3f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundNorm),
                contentAlignment = Alignment.Center
            ) {
                if (flagResId != 0) {
                    FlagIcon(
                        countryFlag = flagResId,
                        size = DpSize(28.dp, 20.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) colors.brandNorm else colors.iconNorm,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
