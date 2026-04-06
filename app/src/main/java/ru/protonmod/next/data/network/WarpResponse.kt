/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WarpResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("privKey") val privateKey: String,
    @SerialName("peer_pub") val peerPublicKey: String,
    @SerialName("client_ipv4") val clientIpv4: String,
    @SerialName("client_ipv6") val clientIpv6: String
)
