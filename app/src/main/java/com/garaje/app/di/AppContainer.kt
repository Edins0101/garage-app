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

    private val fakeStore by lazy { FakeStore(File(context.filesDir, "fake-db.json")) }
    private val api by lazy { ApiClient(BuildConfig.API_URL) }

    val auth: AuthRepository by lazy {
        if (useFakes) FakeAuthRepository(fakeStore) else RemoteAuthRepository(api)
    }
    val garage: GarageRepository by lazy {
        if (useFakes) FakeGarageRepository(fakeStore) else RemoteGarageRepository(api)
    }
    val lists: ListsRepository by lazy {
        if (useFakes) FakeListsRepository(fakeStore) else RemoteListsRepository(api)
    }
    val sharing: SharingRepository by lazy {
        if (useFakes) FakeSharingRepository(fakeStore) else RemoteSharingRepository(api, lists)
    }
}
