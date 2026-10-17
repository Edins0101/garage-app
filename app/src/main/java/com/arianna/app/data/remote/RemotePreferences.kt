package com.arianna.app.data.remote

import com.arianna.app.domain.model.AccountPreferences
import com.arianna.app.domain.repository.PreferencesRepository

/** GET/PUT /me/preferences. */
class RemotePreferencesRepository(private val api: ApiClient) : PreferencesRepository {
    override suspend fun load(): AccountPreferences =
        api.request("GET", "/me/preferences", null, AccountPreferences.serializer()) ?: AccountPreferences()

    override suspend fun save(preferences: AccountPreferences) {
        api.request("PUT", "/me/preferences", api.body(preferences), AccountPreferences.serializer())
    }
}
