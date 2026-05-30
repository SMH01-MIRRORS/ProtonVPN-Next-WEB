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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorReportingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.settings_error_reporting),
                        onBack = onBack
                    )
                }

                item(contentType = "Description") {
                    Text(
                        text = stringResource(R.string.settings_error_reporting_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        modifier = Modifier.padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                    )
                }

                item(contentType = "Category") {
                    SettingsCategory(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.settings_privacy)
                    ) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_crash_reports),
                            subtitle = stringResource(R.string.settings_crash_reports_desc),
                            icon = Icons.Rounded.BugReport,
                            checked = uiState.isCrashReportsEnabled,
                            onCheckedChange = { viewModel.setCrashReportsEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_non_fatal),
                            subtitle = stringResource(R.string.settings_sentry_non_fatal_desc),
                            icon = Icons.Rounded.ReportProblem,
                            checked = uiState.isSentryNonFatalEnabled,
                            onCheckedChange = { viewModel.setSentryNonFatalEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_anr),
                            subtitle = stringResource(R.string.settings_sentry_anr_desc),
                            icon = Icons.Rounded.HourglassBottom,
                            checked = uiState.isSentryAnrEnabled,
                            onCheckedChange = { viewModel.setSentryAnrEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_metrics),
                            subtitle = stringResource(R.string.settings_sentry_metrics_desc),
                            icon = Icons.Rounded.QueryStats,
                            checked = uiState.isSentryMetricsEnabled,
                            onCheckedChange = { viewModel.setSentryMetricsEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_logs),
                            subtitle = stringResource(R.string.settings_sentry_logs_desc),
                            icon = Icons.Rounded.Insights,
                            checked = uiState.isSentryLogsEnabled,
                            onCheckedChange = { viewModel.setSentryLogsEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_performance),
                            subtitle = stringResource(R.string.settings_sentry_performance_desc),
                            icon = Icons.Rounded.Speed,
                            checked = uiState.isSentryPerformanceEnabled,
                            onCheckedChange = { viewModel.setSentryPerformanceEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_analytics),
                            subtitle = stringResource(R.string.settings_analytics_desc),
                            icon = Icons.Rounded.Insights,
                            checked = uiState.isAnalyticsEnabled,
                            onCheckedChange = { viewModel.setAnalyticsEnabled(it) }
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_session_replay),
                            subtitle = stringResource(R.string.settings_sentry_session_replay_desc),
                            icon = Icons.Rounded.Replay,
                            checked = uiState.isSentrySessionReplayEnabled,
                            onCheckedChange = { viewModel.setSentrySessionReplayEnabled(it) }
                        )
                    }
                }

                item(contentType = "Sentry") {
                    SentryPoweredBy()
                }
            }
        }
    }
}
