package com.arianna.app.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.arianna.app.ui.theme.AccentColor
import com.arianna.app.ui.theme.ThemeState

/** Guarda el color principal elegido (para todo el teléfono, también en la pantalla de acceso). */
class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    /** Aplica el color guardado al iniciar la app. */
    fun restore() {
        val saved = prefs.getString(KEY, null)
        ThemeState.accent = AccentColor.entries.find { it.name == saved } ?: AccentColor.VIOLET
    }

    fun set(accent: AccentColor) {
        ThemeState.accent = accent
        prefs.edit { putString(KEY, accent.name) }
    }

    private companion object {
        const val KEY = "accent"
    }
}
