package com.arianna.app.di

import android.content.Context
import com.arianna.app.BuildConfig
import com.arianna.app.data.fake.FakeAuthRepository
import com.arianna.app.data.fake.FakeCountersRepository
import com.arianna.app.data.fake.FakeGroupsRepository
import com.arianna.app.data.fake.FakeListsRepository
import com.arianna.app.data.fake.FakeRemindersRepository
import com.arianna.app.data.fake.FakeSharingRepository
import com.arianna.app.data.fake.FakeStore
import com.arianna.app.data.remote.ApiClient
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
import java.io.File

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
        val counters: CountersRepository,
        val lists: ListsRepository,
        val sharing: SharingRepository,
        val reminders: RemindersRepository,
        val groups: GroupsRepository,
    )

    private fun createData(): DataRepositories =
        if (useFakes) {
            DataRepositories(
                FakeCountersRepository(fakeStore),
                FakeListsRepository(fakeStore, File(appContext.filesDir, "fake-images")),
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
                lists,
                RemoteSharingRepository(api, lists, counters),
                reminders,
                RemoteGroupsRepository(api, lists, counters, reminders),
            )
        }

    @Volatile
    private var data = createData()

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
                delegate.logout()
                data = createData()
            }
        }
    }

    val counters: CountersRepository get() = data.counters
    val lists: ListsRepository get() = data.lists
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
