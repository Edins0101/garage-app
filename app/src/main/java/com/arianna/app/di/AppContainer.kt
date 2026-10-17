package com.arianna.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.arianna.app.BuildConfig
import com.arianna.app.data.UndoableLists
import com.arianna.app.data.fake.FakeAuthRepository
import com.arianna.app.data.fake.FakeCountersRepository
import com.arianna.app.data.fake.FakeGroupsRepository
import com.arianna.app.data.fake.FakeListsRepository
import com.arianna.app.data.fake.FakeRemindersRepository
import com.arianna.app.data.fake.FakeSharingRepository
import com.arianna.app.data.fake.FakeStore
import com.arianna.app.data.remote.ApiClient
import com.arianna.app.data.remote.ResponseCache
import com.arianna.app.data.remote.RemoteAuthRepository
import com.arianna.app.data.remote.RemoteCountersRepository
import com.arianna.app.data.remote.RemoteGroupsRepository
import com.arianna.app.data.remote.RemoteListsRepository
import com.arianna.app.data.remote.RemoteRemindersRepository
import com.arianna.app.data.remote.RemoteSharingRepository
import com.arianna.app.data.prefs.AppearanceStore
import com.arianna.app.data.push.PushRegistrar
import com.arianna.app.data.reminders.ReminderSnapshotStore
import com.arianna.app.data.reminders.ReminderWorker
import com.arianna.app.data.prefs.HomePrefsStore
import com.arianna.app.data.prefs.PreferencesSync
import com.arianna.app.data.fake.FakePreferencesRepository
import com.arianna.app.data.remote.RemotePreferencesRepository
import com.arianna.app.data.security.AppLock
import com.arianna.app.data.session.SessionStore
import com.arianna.app.domain.repository.AuthRepository
import com.arianna.app.domain.repository.CountersRepository
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.domain.repository.ListsRepository
import com.arianna.app.domain.repository.RemindersRepository
import com.arianna.app.domain.repository.ReminderDraft
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.repository.SharingRepository
import com.arianna.app.domain.repository.UndoableDeletes
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Inyección de dependencias manual. Elige repositorios falsos o remotos
 * según BuildConfig.USE_FAKES (definido en app/build.gradle.kts).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val useFakes: Boolean = BuildConfig.USE_FAKES

    private val sessions = SessionStore(context)
    val homePrefs = HomePrefsStore(context)
    val appearance = AppearanceStore(context)
    /** Bloqueo con huella, rostro o PIN (opción de este teléfono). */
    val appLock = AppLock(context)
    private val fakeStore by lazy { FakeStore(File(context.filesDir, "fake-db.json")) }
    /** Copia local de las respuestas para funcionar sin conexión. */
    private val responseCache = ResponseCache(File(context.filesDir, "api-cache"))
    private val api by lazy {
        ApiClient(
            baseUrl = BuildConfig.API_URL,
            tokenProvider = { sessions.token },
            onUnauthorized = { auth.logout() },
            cache = responseCache,
            hasInternet = ::hasInternet,
        )
    }

    /** true mientras se muestran datos guardados porque no hay conexión. */
    val offline: StateFlow<Boolean> by lazy { if (useFakes) MutableStateFlow(false) else api.offline }

    /** Al volver el internet se recarga todo: se ven los datos al día y se quita el aviso. */
    fun watchConnection() {
        if (useFakes) return
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!offline.value || sessions.token == null) return
                scope.launch {
                    runCatching { lists.refresh() }
                    runCatching { counters.refresh() }
                    runCatching { reminders.refresh() }
                    runCatching { groups.refresh() }
                }
            }
        })
    }

    private fun hasInternet(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Repositorios de datos de la sesión actual; se recrean al cerrar sesión. */
    private class DataRepositories(
        val counters: CountersRepository,
        val lists: UndoableLists,
        val sharing: SharingRepository,
        val reminders: RemindersRepository,
        val groups: GroupsRepository,
    )

    private fun createData(): DataRepositories =
        if (useFakes) {
            DataRepositories(
                FakeCountersRepository(fakeStore),
                UndoableLists(FakeListsRepository(fakeStore, File(appContext.filesDir, "fake-images"))),
                FakeSharingRepository(fakeStore),
                FakeRemindersRepository(fakeStore),
                FakeGroupsRepository(fakeStore),
            )
        } else {
            val lists = RemoteListsRepository(api)
            val counters = RemoteCountersRepository(api)
            val reminders = RemoteRemindersRepository(api)
            DataRepositories(
                counters,
                UndoableLists(lists),
                RemoteSharingRepository(api, lists, counters),
                reminders,
                RemoteGroupsRepository(api, lists, counters, reminders),
            )
        }

    @Volatile
    private var data = createData()

    /** Inicio y color guardados en la cuenta. */
    val preferencesSync: PreferencesSync by lazy {
        val repository by lazy { if (useFakes) FakePreferencesRepository(fakeStore) else RemotePreferencesRepository(api) }
        PreferencesSync({ repository }, homePrefs, appearance)
    }

    /** Notificaciones push: registra este teléfono en el backend (no aplica en modo datos falsos). */
    val push: PushRegistrar by lazy {
        PushRegistrar(appContext, api.takeUnless { useFakes }, sessionToken = { sessions.token })
    }

    val auth: AuthRepository by lazy {
        val delegate = if (useFakes) FakeAuthRepository(fakeStore, sessions) else RemoteAuthRepository(api, sessions)
        // Al cerrar sesión se descarta la caché en memoria del usuario anterior.
        object : AuthRepository by delegate {
            override fun logout() {
                push.unregister(closingSessionToken = sessions.token)
                ReminderWorker.cancel(appContext)
                ReminderSnapshotStore(appContext).clear()
                preferencesSync.stop()
                responseCache.clear()
                appLock.unlockForLogout()
                delegate.logout()
                data = createData()
            }
        }
    }

    val counters: CountersRepository get() = data.counters
    val lists: ListsRepository get() = data.lists
    /** Borrar listas y elementos con "Deshacer". */
    val undoableDeletes: UndoableDeletes get() = data.lists
    val sharing: SharingRepository get() = data.sharing
    val reminders: RemindersRepository get() = data.reminders.withAlarmRefresh()
    val groups: GroupsRepository get() = data.groups

    /** Tras crear, editar, marcar o borrar, se vuelven a calcular los avisos (incluidas las alarmas con hora). */
    private fun RemindersRepository.withAlarmRefresh(): RemindersRepository {
        val delegate = this
        val refresh = { ReminderWorker.refreshNow(appContext) }
        return object : RemindersRepository by delegate {
            override suspend fun create(kind: ReminderKind, draft: ReminderDraft, spaceId: String?) =
                delegate.create(kind, draft, spaceId).also { refresh() }

            override suspend fun update(reminder: Reminder, draft: ReminderDraft) =
                delegate.update(reminder, draft).also { refresh() }

            override suspend fun delete(reminder: Reminder) = delegate.delete(reminder).also { refresh() }

            override suspend fun markDone(reminder: Reminder, amount: Double?, note: String?, odometer: Int?) =
                delegate.markDone(reminder, amount, note, odometer).also { refresh() }

            override suspend fun undo(reminder: Reminder) = delegate.undo(reminder).also { refresh() }
        }
    }
}
