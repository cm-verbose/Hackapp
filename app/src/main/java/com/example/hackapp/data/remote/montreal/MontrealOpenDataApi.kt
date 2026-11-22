package com.example.hackapp.data.remote.montreal

import com.example.hackapp.data.Event
import com.example.hackapp.data.remote.Network
import retrofit2.http.GET
import retrofit2.http.Query

/** CKAN datastore API for https://donnees.montreal.ca/dataset/evenements-publics. */
interface MontrealOpenDataApi {
    @GET("api/3/action/datastore_search")
    suspend fun search(
        @Query("resource_id") resourceId: String = EVENTS_RESOURCE_ID,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): DatastoreResponseDto

    companion object {
        const val BASE_URL = "https://donnees.montreal.ca/"

        /** Resource id of the "Événements" CSV, which is datastore-backed. */
        const val EVENTS_RESOURCE_ID = "6decf611-6f11-4f34-bb36-324d804c9bad"
        const val PAGE_SIZE = 1000
    }
}

/** Fetches every public event from Montréal open data and maps it to [Event]. */
class MontrealOpenDataEventSource {

    private val api: MontrealOpenDataApi =
        Network.retrofit(MontrealOpenDataApi.BASE_URL).create(MontrealOpenDataApi::class.java)

    suspend fun fetchEvents(maxRecords: Int = Int.MAX_VALUE): List<Event> {
        val records = mutableListOf<MontrealEventRecordDto>()
        var offset = 0
        do {
            val response = api.search(offset = offset)
            check(response.success) { "Montréal datastore_search returned success=false" }
            val page = response.result ?: break
            records += page.records
            offset += page.records.size
        } while (page.records.isNotEmpty() && offset < page.total && offset < maxRecords)

        return records.take(maxRecords).map { it.toEvent() }
    }
}
