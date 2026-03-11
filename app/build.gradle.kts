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

import javax.inject.Inject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class FixLibboxAarTask @Inject constructor() : DefaultTask() {

    @get:InputFile
    abstract val rawAar: RegularFileProperty

    @get:OutputFile
    abstract val fixedAar: RegularFileProperty

    @TaskAction
    fun fixAar() {
        val raw = rawAar.get().asFile
        val fixed = fixedAar.get().asFile

        println("Fixing gomobile duplicate classes in ${raw.name}...")

        val tmpDir = File(project.layout.buildDirectory.asFile.get(), "tmp/fixLibbox").apply {
            deleteRecursively()
            mkdirs()
        }

        // 1. Unpack AAR
        project.copy {
            from(project.zipTree(raw))
            into(tmpDir)
        }

        val classesJar = File(tmpDir, "classes.jar")
        val fixedClassesJar = File(tmpDir, "classes-fixed.jar")

        if (classesJar.exists()) {
            // 2. Process classes.jar to remove 'go/' package
            ZipInputStream(classesJar.inputStream()).use { zipIn ->
                ZipOutputStream(fixedClassesJar.outputStream()).use { zipOut ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.name.startsWith("go/")) {
                            zipOut.putNextEntry(ZipEntry(entry.name))
                            zipIn.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                        entry = zipIn.nextEntry
                    }
                }
            }
            classesJar.delete()
            fixedClassesJar.renameTo(classesJar)
        }

        // 3. Repack AAR
        ZipOutputStream(fixed.outputStream()).use { zipOutAar ->
            tmpDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(tmpDir).path
                    zipOutAar.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zipOutAar) }
                    zipOutAar.closeEntry()
                }
            }
        }

        tmpDir.deleteRecursively()
        println("Successfully created clean libbox.aar!")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.sentry)
}

val fixLibboxAar = tasks.register<FixLibboxAarTask>("fixLibboxAar") {
    rawAar.set(layout.projectDirectory.file("libs/libbox-raw.aar"))
    fixedAar.set(layout.buildDirectory.file("intermediates/fixed_aar/libbox.aar"))
}

android {
    namespace = "ru.protonmod.next"
    compileSdk = 36

    // Force AGP to use a specific NDK version instead of the default one
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "ru.protonmod.next"
        minSdk = 29
        targetSdk = 36
        versionCode = 605159512
        versionName = "12.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val keyFile = System.getenv("SIGNING_KEY_FILE") ?: ""
            if (keyFile.isNotEmpty()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                // Fallback to debug for local builds without env vars
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            buildConfigField("boolean", "ALLOW_LOGCAT", "true")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ALLOW_LOGCAT", "false")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    configurations.all {
        exclude(group = "me.proton.crypto", module = "android-golib")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin.compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }

    packaging {
        jniLibs {
            keepDebugSymbols.addAll(listOf(
                "**/libam-go.so",
                "**/libam-quick.so",
                "**/libam.so",
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
                "**/libgojni.so",
                "**/libhev-socks5-tunnel.so",
                "**/libsentry-android.so",
                "**/libsentry.so"
            ))
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

sentry {
    includeProguardMapping = true
    autoUploadProguardMapping = true
    uploadNativeSymbols = true
    includeNativeSources = true
}

dependencies {
    // AndroidX & Core UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.android.svg)

    // Jetpack Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Local Database (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network & Serialization
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)

    // VPN Protocols
    implementation(libs.amneziawg.android)
    implementation(files(fixLibboxAar))
    implementation(libs.go.vpn.lib)

    // Debug Tools
    debugImplementation(libs.leakcanary.android)

    // Sentry
    implementation(libs.sentry.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
