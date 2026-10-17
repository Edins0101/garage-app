package com.arianna.app.data.fake

import com.arianna.app.domain.model.AccountPreferences
import com.arianna.app.domain.repository.PreferencesRepository

/** Preferencias "en la cuenta" dentro del archivo del servidor simulado. */
class FakePreferencesRepository(private val store: FakeStore) : PreferencesRepository {
    override suspend fun load(): AccountPreferences = store.load().preferences

    override suspend fun save(preferences: AccountPreferences) = store.update { db ->
        val current = db.preferences
        val merged = AccountPreferences(
            home = preferences.home ?: current.home,
            accent = preferences.accent ?: current.accent,
        )
        db.copy(preferences = merged) to Unit
    }
}
