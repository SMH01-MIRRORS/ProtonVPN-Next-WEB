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

package ru.protonmod.next

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.nav.Screen
import ru.protonmod.next.ui.nav.appNavGraph
import ru.protonmod.next.ui.screens.LoginScreen
import ru.protonmod.next.ui.screens.WelcomeScreen
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.ProvideDeviceType
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String>("")
    val startDestination: StateFlow<String> = _startDestination.asStateFlow()

    val appTheme: StateFlow<AppTheme> = settingsManager.appTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.DARK
        )

    init {
        viewModelScope.launch {
            val session = sessionDao.getSession()
            if (session != null && session.accessToken.isNotEmpty()) {
                _startDestination.value = Screen.Home.route
            } else {
                _startDestination.value = "welcome"
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val viewModel: MainViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsState()

            ProtonNextTheme(appTheme = appTheme) {
                ProvideDeviceType(windowSizeClass.widthSizeClass) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ProtonNextTheme.colors.backgroundNorm)
                    ) {
                        LaunchedEffect(Unit) {
                            checkAndRequestNotificationPermission()
                        }
                        ProtonNextAppNavHost(viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Intercept KEYCODE_CALL events to prevent the Android framework's
    // PhoneFallbackEventHandler from broadcasting ACTION_CLOSE_SYSTEM_DIALOGS,
    // which requires a signature-level permission on Android 12+ (API 31+) and
    // causes a SecurityException in third-party apps.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_CALL) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun ProtonNextAppNavHost(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val startDestination by viewModel.startDestination.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentTarget = when (currentRoute) {
        Screen.Home.route -> MainTarget.Home
        Screen.Countries.route -> MainTarget.Countries
        Screen.Profiles.route -> MainTarget.Profiles
        Screen.Settings.route -> MainTarget.Settings
        else -> null
    }

    if (startDestination.isEmpty()) return

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("welcome") {
                WelcomeScreen(
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToRegister = { /* TODO: Registration flow */ },
                    onNavigateToHome = {
                        // Clear the entire backstack and navigate to home (dashboard)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0)
                        }
                    },
                    onNavigateToApiBypassSettings = {
                        navController.navigate(Screen.ApiBypass.route)
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onBackClick = { navController.popBackStack() },
                    onLoginSuccess = {
                        // Clear the entire backstack and navigate to home (dashboard)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0)
                        }
                    }
                )
            }

            appNavGraph(navController = navController)
        }

        AnimatedVisibility(
            visible = currentTarget != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                navigateTo = { target ->
                    val route = when (target) {
                        MainTarget.Home -> Screen.Home.route
                        MainTarget.Countries -> Screen.Countries.route
                        MainTarget.Profiles -> Screen.Profiles.route
                        MainTarget.Settings -> Screen.Settings.route
                    }
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}
