/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import retrofit2.http.GET
import retrofit2.http.Url

interface WarpApi {
    @GET
    suspend fun getWarpData(@Url url: String = "https://warp-generator-config.vercel.app/api/warp-data"): WarpResponse
}
