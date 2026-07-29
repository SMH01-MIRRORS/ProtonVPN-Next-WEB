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

// Root configuration for Proton VPN-Next project
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://clojars.org/repo/") }
    }
}

rootProject.name = "ProtonVpnNext"

// Automatically generate local.properties if it's missing but ANDROID_HOME is set.
// This ensures that Android Gradle Plugin can find the SDK in CI environments
// without needing manual local.properties creation.
val localPropertiesFile = file("local.properties")
if (!localPropertiesFile.exists()) {
    val androidHome = providers.environmentVariable("ANDROID_HOME").orNull
        ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
    if (androidHome != null) {
        println("Settings: Auto-generating local.properties with sdk.dir=$androidHome")
        localPropertiesFile.writeText("sdk.dir=$androidHome\n")
    } else {
        println("Settings: local.properties missing and ANDROID_HOME not set. AGP might fail.")
    }
} else {
    println("Settings: Using existing local.properties")
}

// The generated gomobile AAR is intentionally not committed. Settings scripts are evaluated
// during Android Studio sync as well as regular Gradle invocations, so a fresh checkout prepares
// the pinned AWGBox core before :app resolves its local file dependency. The shell script exits
// immediately when the existing artifact matches the committed checksum.
val shouldSkipAwgBox = providers.gradleProperty("SKIP_AWGBOX_BUILD").orNull == "true" ||
        providers.environmentVariable("SKIP_AWGBOX_BUILD").orNull == "true" ||
        startParameter.taskNames.any { it.contains("dependency", ignoreCase = true) }

if (!shouldSkipAwgBox) {
    val prepareAwgBox = ProcessBuilder(
        "bash",
        file("scripts/build-awgbox-lib.sh").absolutePath
    )
        .directory(rootDir)
        .inheritIO()
        .start()
        .waitFor()
    check(prepareAwgBox == 0) {
        "Unable to prepare the AWGBox AAR. Check Android SDK/NDK, Go, git and python3."
    }
} else {
    println("Skipping AWGBox AAR preparation (requested or dependency scan task detected)")
}

// Include main application module
include(":app")