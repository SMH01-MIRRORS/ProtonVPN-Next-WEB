# Project Overview: ProtonVPN-Next

This document provides a comprehensive technical context for AI assistants working on the ProtonVPN-Next Android project.

## Core Identity
- **Project Name**: ProtonVPN-Next
- **Package**: `ru.protonmod.next`
- **Goal**: A high-performance, secure VPN client for Android supporting multiple protocols, with a focus on privacy and anti-censorship features.

## Technical Stack
- **Language**: Kotlin (Modern Android practices)
- **UI Framework**: Jetpack Compose (Material 3)
- **Dependency Injection**: Hilt
- **Database**: Room (with Kotlin Symbol Processing - KSP)
- **Networking**: Retrofit & OkHttp (supports DNS-over-HTTPS)
- **VPN Engine**: AmneziaWG (via `amneziawg-android`) and `go-vpn-lib` (Go-based VPN core)
- **Architecture**: Clean Architecture / MVVM
- **Build System**: Gradle Kotlin DSL (`.gradle.kts`)

## Project Structure (Key Packages)
- `ru.protonmod.next.vpn`: Core VPN logic. Contains `ProtonVpnService` (lifecycle), `NextVpnManager` (native bridge), and protocol-specific managers (`AmneziaVpnManager`, `WarpManager`).
- `ru.protonmod.next.ui`: Compose-based UI.
    - `screens/`: Individual features (Dashboard, Countries, Profiles, Settings).
    - `components/`: Reusable UI elements.
    - `theme/`: Material 3 styling and color schemes.
- `ru.protonmod.next.data`: Data layer.
    - `repository/`: Single source of truth for UI.
    - `network/`: API definitions and DTOs.
    - `local/`: Room database and DataStore preferences.
- `ru.protonmod.next.di`: Hilt modules providing dependencies for Network, Database, VPN, and App scope.
- `ru.protonmod.next.ota`: Logic for over-the-air updates.

## Key Components & Logic
### 1. VPN Lifecycle (`ProtonVpnService`)
Handles the `VpnService` implementation, foreground notifications, traffic statistics updates, and interaction with the `GoBackend`.

### 2. Native Security Bridge (`NextVpnManager`)
Uses JNI to communicate with native C++ code for:
- Tamper detection (`isTamperDetectedNative`).
- Integrity checks.
- Protected string retrieval.
- State management synchronized with native layers.

### 3. Build Flavors
- **Channels**: `stable`, `nightly` (updates frequency).
- **Types**:
    - `standard`: Includes Sentry for crash reporting.
    - `privacy`: Zero telemetry, Sentry excluded, additional native obfuscation.

### 4. Build Process Security
The project includes a custom Gradle task `generateSecurityMetadata` that creates a C++ header (`security_metadata.h`) containing obfuscated APK sizes, version codes, and library lists to prevent unauthorized modifications.

## Guidelines for AI
- **UI**: Use Jetpack Compose and Material 3 components from `androidx.compose.material3`.
- **Concurrency**: Prefer Coroutines and Flow for asynchronous operations.
- **State Management**: Use `ViewModel` with `StateFlow` to expose state to screens.
- **Privacy**: Be mindful of the `privacy` flavor; do not add analytics or telemetry to paths used by this flavor.
- **Native**: Always check if a VPN action requires synchronization with `NextVpnManager`.