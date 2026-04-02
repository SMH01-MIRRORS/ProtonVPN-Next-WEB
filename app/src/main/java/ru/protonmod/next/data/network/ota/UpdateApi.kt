package ru.protonmod.next.data.network.ota

import ru.protonmod.next.data.model.ota.UpdateResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface UpdateApi {
    @GET
    suspend fun getUpdateMetadata(@Url url: String): UpdateResponse
}
