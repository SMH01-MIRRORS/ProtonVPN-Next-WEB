package ru.protonmod.next.data.model.ota

import kotlinx.serialization.Serializable

@Serializable
data class UpdateResponse(
    val release: UpdateInfo? = null,
    val debug: UpdateInfo? = null
)

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val changelog: String,
    val force: Boolean = false
)
