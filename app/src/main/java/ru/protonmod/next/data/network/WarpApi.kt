/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

interface WarpApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.1.7585.101 Safari/537.36",
        "Accept: */*",
        "Accept-Language: ru",
        "Referer: https://warp-generator.github.io/",
        "Origin: https://warp-generator.github.io",
        "Sec-Fetch-Dest: empty",
        "Sec-Fetch-Mode: cors",
        "Sec-Fetch-Site: cross-site"
    )
    @GET
    suspend fun getWarpData(@Url url: String = "https://warp-vercel-murex.vercel.app/api/warp-data"): WarpResponse
}
