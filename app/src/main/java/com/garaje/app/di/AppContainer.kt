package com.garaje.app.di

import android.content.Context
import com.garaje.app.BuildConfig
import com.garaje.app.data.fake.FakeAuthRepository
import com.garaje.app.data.fake.FakeGarageRepository
import com.garaje.app.data.fake.FakeListsRepository
import com.garaje.app.data.fake.FakeSharingRepository
import com.garaje.app.data.fake.FakeStore
import com.garaje.app.data.remote.ApiClient
import com.garaje.app.data.remote.RemoteAuthRepository
import com.garaje.app.data.remote.RemoteGarageRepository
import com.garaje.app.data.remote.RemoteListsRepository
import com.garaje.app.data.remote.RemoteSharingRepository
import com.garaje.app.data.prefs.AppearanceStore
import com.garaje.app.data.prefs.HomePrefsStore
import com.garaje.app.data.session.SessionStore
import com.garaje.app.domain.repository.AuthRepository
import com.garaje.app.domain.repository.GarageRepository
import com.garaje.app.domain.repository.ListsRepository
import com.garaje.app.domain.repository.SharingRepository
import java.io.File

/**
 * Inyección de dependencias manual. Elige repositorios falsos o remotos
 * según BuildConfig.USE_FAKES (definido en app/build.gradle.kts).
 */
class AppContainer(context: Context) {
    val useFakes: Boolean = BuildConfig.USE_FAKES

    private val sessions = SessionStore(context)
    val homePrefs = HomePrefsStore(context)
    val appearance = AppearanceStore(context)
    private val fakeStore by lazy { FakeStore(File(context.filesDir, "fake-db.json")) }
    private val api by lazy {
        ApiClient(
            baseUrl = BuildConfig.API_URL,
            tokenProvider = { sessions.token },
            onUnauthorized = { auth.logout() },
        )
    }

    /** Repositorios de datos de la sesión actual; se recrean al cerrar sesión. */
    private class DataRepositories(
        val garage: GarageRepository,
        val lists: ListsRepository,
        val sharing: SharingRepository,
    )

    private fun createData(): DataRepositories =
        if (useFakes) {
            DataRepositories(
                FakeGarageRepository(fakeStore),
                FakeListsRepository(fakeStore),
                FakeSharingRepository(fakeStore),
            )
        } else {
            val lists = RemoteListsRepository(api)
            DataRepositories(RemoteGarageRepository(api), lists, RemoteSharingRepository(api, lists))
        }

    @Volatile
    private var data = createData()

    val auth: AuthRepository by lazy {
        val delegate = if (useFakes) FakeAuthRepository(fakeStore, sessions) else RemoteAuthRepository(api, sessions)
        // Al cerrar sesión se descarta la caché en memoria del usuario anterior.
        object : AuthRepository by delegate {
            override fun logout() {
                delegate.logout()
                data = createData()
            }
        }
    }

    val garage: GarageRepository get() = data.garage
    val lists: ListsRepository get() = data.lists
    val sharing: SharingRepository get() = data.sharing
}
