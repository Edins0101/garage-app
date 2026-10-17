package com.arianna.app.data.prefs

import com.arianna.app.domain.model.AccountPreferences
import com.arianna.app.domain.model.HomeLayout
import com.arianna.app.domain.repository.PreferencesRepository
import com.arianna.app.ui.theme.AccentColor
import com.arianna.app.ui.theme.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Mantiene el inicio y el color iguales en todos los teléfonos de la cuenta.
 * Al entrar se traen del servidor; cada cambio se guarda en el teléfono y se sube.
 * Sin internet no pasa nada: queda lo del teléfono y se sube con el próximo cambio.
 */
class PreferencesSync(
    private val repository: () -> PreferencesRepository,
    private val homePrefs: HomePrefsStore,
    private val appearance: AppearanceStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var userId: String? = null

    init {
        homePrefs.onSaved = { id, prefs -> if (id == userId) upload(AccountPreferences(home = prefs.toLayout())) }
        appearance.onChanged = { accent -> if (userId != null) upload(AccountPreferences(accent = accent.name)) }
    }

    /** Al iniciar sesión (o abrir la app con sesión): aplica lo guardado en la cuenta. */
    fun start(userId: String) {
        this.userId = userId
        scope.launch {
            val remote = runCatching { repository().load() }.getOrNull() ?: return@launch
            val local = homePrefs.observe(userId).value
            val home = remote.home
            if (home != null) {
                homePrefs.save(userId, home.toPrefs(), notify = false)
            } else if (local.configured) {
                // La cuenta aún no tiene nada: se sube lo que ya hay en este teléfono.
                upload(AccountPreferences(home = local.toLayout(), accent = ThemeState.accent.name))
                return@launch
            }
            val accent = AccentColor.entries.find { it.name == remote.accent }
            if (accent != null) {
                appearance.set(accent, notify = false)
            } else {
                upload(AccountPreferences(accent = ThemeState.accent.name))
            }
        }
    }

    fun stop() {
        userId = null
    }

    private fun upload(preferences: AccountPreferences) {
        scope.launch { runCatching { repository().save(preferences) } }
    }
}

private fun HomePrefs.toLayout() = HomeLayout(order.map { it.name }, hidden.map { it.name }, configured)

private fun HomeLayout.toPrefs(): HomePrefs {
    fun parse(names: List<String>) = names.mapNotNull { name -> HomeSection.entries.find { it.name == name } }
    return HomePrefs(order = parse(order), hidden = parse(hidden).toSet(), configured = configured).normalized()
}
