package com.hypermarker.picker

import android.content.Context

/** «Usadas hace poco»: últimas 4 carpetas destino, sólo en preferencias locales del dispositivo. */
class RecentFolders(context: Context) {
    private val preferences = context.getSharedPreferences("picker", Context.MODE_PRIVATE)
    fun load(): List<String> = preferences.getString(KEY, "").orEmpty().split(',').filter { it.matches(Regex("[a-f0-9-]{36}")) }
    fun use(id: String?) { if (id != null) preferences.edit().putString(KEY, ItemPickerState.remember(load(), id).joinToString(",")).apply() }
    private companion object { const val KEY = "recent_destinations" }
}
