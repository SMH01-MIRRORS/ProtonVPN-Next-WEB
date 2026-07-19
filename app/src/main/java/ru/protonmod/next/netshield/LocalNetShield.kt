/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.netshield

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNetShield @Inject constructor(
    @ApplicationContext context: Context,
) {
    private data class Source(val category: NetShieldCategory, val url: String, val fileName: String)

    private val directory = File(context.filesDir, "netshield").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("netshield", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val updateMutex = Mutex()

    @Volatile private var domains = loadDomains()
    private val _stats = MutableStateFlow(NetShieldStats())
    val stats: StateFlow<NetShieldStats> = _stats.asStateFlow()
    private val _listState = MutableStateFlow(
        NetShieldListState(
            lastUpdatedAt = preferences.getLong(KEY_UPDATED_AT, 0),
            domainCount = preferences.getInt(KEY_DOMAIN_COUNT, 0),
        )
    )
    val listState: StateFlow<NetShieldListState> = _listState.asStateFlow()

    fun resetSessionStats() { _stats.value = NetShieldStats() }

    fun activeRuleSets(level: NetShieldLevel): List<NetShieldRuleSet> {
        val categories = when (level) {
            NetShieldLevel.DISABLED -> emptySet()
            NetShieldLevel.MALWARE -> setOf(NetShieldCategory.MALWARE)
            NetShieldLevel.ADS_TRACKERS -> setOf(NetShieldCategory.MALWARE, NetShieldCategory.ADS, NetShieldCategory.TRACKERS)
            NetShieldLevel.ADS_TRACKERS_ADULT -> NetShieldCategory.entries.toSet()
        }
        return categories.mapNotNull { category ->
            val source = SOURCES.first { it.category == category }
            File(directory, source.fileName + ".json").takeIf(File::isFile)?.let {
                NetShieldRuleSet("netshield-${category.name.lowercase(Locale.ROOT)}", it.absolutePath, category)
            }
        }
    }

    suspend fun updateLists(): Result<Int> = updateMutex.withLock {
        _listState.update { it.copy(isUpdating = true, error = null) }
        withContext(Dispatchers.IO) {
            runCatching {
                // Download and validate every source before replacing any active file.
                val updated = SOURCES.associate { source ->
                    val request = Request.Builder().url(source.url).header("User-Agent", "ProtonVPN-Next/NetShield").build()
                    val body = client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "${source.category}: HTTP ${response.code}" }
                        response.body.string()
                    }
                    val parsed = NetShieldDomainParser.parse(body)
                    check(parsed.isNotEmpty()) { "${source.category}: empty rule list" }
                    source.category to parsed
                }
                SOURCES.forEach { source -> writeRuleSet(source, updated.getValue(source.category)) }
                domains = updated
                val count = updated.values.sumOf(Set<String>::size)
                val now = System.currentTimeMillis()
                preferences.edit().putLong(KEY_UPDATED_AT, now).putInt(KEY_DOMAIN_COUNT, count).apply()
                _listState.value = NetShieldListState(lastUpdatedAt = now, domainCount = count)
                count
            }.onFailure { error ->
                _listState.update { it.copy(isUpdating = false, error = error.message ?: error.javaClass.simpleName) }
            }
        }
    }

    fun recordEngineLog(message: String) {
        val match = REJECTED_DNS.find(message) ?: return
        val host = match.groupValues[1].trimEnd('.').lowercase(Locale.ROOT)
        val category = classify(host) ?: return
        _stats.update { current ->
            when (category) {
                NetShieldCategory.ADS -> current.copy(
                    adsBlocked = current.adsBlocked + 1,
                    savedBytes = current.savedBytes + ESTIMATED_AD_BYTES,
                )
                NetShieldCategory.TRACKERS -> current.copy(
                    trackersBlocked = current.trackersBlocked + 1,
                    savedBytes = current.savedBytes + ESTIMATED_TRACKER_BYTES,
                )
                NetShieldCategory.MALWARE, NetShieldCategory.ADULT -> current
            }
        }
    }

    private fun classify(host: String): NetShieldCategory? {
        val snapshot = domains
        val order = listOf(NetShieldCategory.TRACKERS, NetShieldCategory.ADS, NetShieldCategory.MALWARE, NetShieldCategory.ADULT)
        val suffixes = generateSequence(host) { value -> value.substringAfter('.', "").takeIf(String::isNotEmpty) }.toList()
        return order.firstOrNull { category -> suffixes.any(snapshot[category].orEmpty()::contains) }
    }

    private fun loadDomains(): Map<NetShieldCategory, Set<String>> = SOURCES.associate { source ->
        source.category to File(directory, source.fileName + ".domains")
            .takeIf(File::isFile)?.readLines()?.filter(String::isNotBlank)?.toSet().orEmpty()
    }

    private fun writeRuleSet(source: Source, values: Set<String>) {
        val sorted = values.sorted()
        val json = JsonObject(mapOf(
            "version" to JsonPrimitive(3),
            "rules" to JsonArray(listOf(JsonObject(mapOf(
                "domain_suffix" to JsonArray(sorted.map(::JsonPrimitive))
            ))))
        ))
        val jsonFile = File(directory, source.fileName + ".json")
        val domainFile = File(directory, source.fileName + ".domains")
        val jsonTmp = File(directory, source.fileName + ".json.tmp")
        val domainTmp = File(directory, source.fileName + ".domains.tmp")
        jsonTmp.writeText(Json.encodeToString(JsonObject.serializer(), json))
        domainTmp.writeText(sorted.joinToString("\n"))
        check(jsonTmp.renameTo(jsonFile) || jsonTmp.copyTo(jsonFile, overwrite = true).let { jsonTmp.delete(); true })
        check(domainTmp.renameTo(domainFile) || domainTmp.copyTo(domainFile, overwrite = true).let { domainTmp.delete(); true })
    }


    private companion object {
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_DOMAIN_COUNT = "domain_count"
        const val ESTIMATED_AD_BYTES = 150_000L
        const val ESTIMATED_TRACKER_BYTES = 4_000L
        val REJECTED_DNS = Regex("rejected\\s+(?:A|AAAA|HTTPS|SVCB)\\s+([^\\s]+)", RegexOption.IGNORE_CASE)
        val SOURCES = listOf(
            Source(NetShieldCategory.MALWARE, "https://urlhaus.abuse.ch/downloads/hostfile/", "malware"),
            Source(NetShieldCategory.ADS, "https://easylist.to/easylist/easylist.txt", "ads"),
            Source(NetShieldCategory.TRACKERS, "https://easylist.to/easylist/easyprivacy.txt", "trackers"),
            Source(NetShieldCategory.ADULT, "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts", "adult"),
        )
    }
}
