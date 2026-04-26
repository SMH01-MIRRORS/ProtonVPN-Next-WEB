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

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.utils.Base32
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DohResponse(
    @SerialName("Status") val status: Int,
    @SerialName("Answer") val answer: List<DohAnswer>? = null
)

@Serializable
data class DohAnswer(
    @SerialName("name") val name: String,
    @SerialName("type") val type: Int,
    @SerialName("data") val data: String
)

@Singleton
class DohClient @Inject constructor(
    private val json: Json
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val providers = listOf(
        "https://dns.google/resolve",
        "https://cloudflare-dns.com/dns-query"
    )

    suspend fun getAlternativeHosts(sessionId: String?, originalHost: String): List<String> {
        val base32Host = Base32.encode(originalHost.toByteArray())
        val sessionPrefix = if (sessionId != null) "$sessionId." else ""
        val queryDomain = "${sessionPrefix}d$base32Host.protonpro.xyz"
        
        ProtonLogger.d("DohClient", "Querying alternative hosts for: $queryDomain")

        for (provider in providers) {
            try {
                val url = provider.toHttpUrl().newBuilder()
                    .addQueryParameter("name", queryDomain)
                    .addQueryParameter("type", "TXT")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body
                        val body = responseBody.string()
                        val dohResponse = json.decodeFromString<DohResponse>(body)
                        if (dohResponse.status == 0 && dohResponse.answer != null) {
                            return dohResponse.answer
                                .filter { it.type == 16 } // TXT record
                                .map { it.data.trim('"') }
                                .filter { it.isNotEmpty() }
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.w("DohClient", "Failed to query DoH provider $provider: ${e.message}")
            }
        }

        return emptyList()
    }
}
