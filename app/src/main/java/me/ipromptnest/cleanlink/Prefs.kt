package me.ipromptnest.cleanlink

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cleanlink")

enum class Mode(val id: String) {
    CARD("card"),
    SILENT("silent");

    companion object {
        fun from(id: String?): Mode = entries.firstOrNull { it.id == id } ?: CARD
    }
}

data class HistoryEntry(
    val time: Long,
    val host: String,
    val url: String,
    val removed: Int,
    val short: Boolean,
)

/**
 * 只存两件事：用户选的行为模式，和最近 100 条。没有账号、没有上报。
 */
class Prefs(context: Context) {

    private val store = context.applicationContext.dataStore

    val mode: Flow<Mode> = store.data.map { Mode.from(it[KEY_MODE]) }

    val history: Flow<List<HistoryEntry>> = store.data.map { prefs ->
        prefs[KEY_HISTORY]
            ?.lineSequence()
            ?.mapNotNull { decode(it) }
            ?.toList()
            ?: emptyList()
    }

    suspend fun currentMode(): Mode = mode.first()

    suspend fun setMode(value: Mode) {
        store.edit { it[KEY_MODE] = value.id }
    }

    suspend fun record(entry: HistoryEntry) {
        store.edit { prefs ->
            val old = prefs[KEY_HISTORY]?.lineSequence()?.toList() ?: emptyList()
            val merged = (listOf(encode(entry)) + old).take(MAX_HISTORY)
            prefs[KEY_HISTORY] = merged.joinToString("\n")
        }
    }

    suspend fun clearHistory() {
        store.edit { it.remove(KEY_HISTORY) }
    }

    /** 上次检查更新的时间。只用来限频，不参与任何别的事。 */
    val lastCheck: Flow<Long> = store.data.map { it[KEY_LAST_CHECK] ?: 0L }

    suspend fun setLastCheck(time: Long) {
        store.edit { it[KEY_LAST_CHECK] = time }
    }

    private fun encode(e: HistoryEntry): String =
        listOf(e.time.toString(), e.removed.toString(), if (e.short) "1" else "0", e.host, e.url)
            .joinToString("|")

    private fun decode(line: String): HistoryEntry? {
        val parts = line.split("|", limit = 5)
        if (parts.size < 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        return HistoryEntry(
            time = time,
            host = parts[3],
            url = parts[4],
            removed = parts[1].toIntOrNull() ?: 0,
            short = parts[2] == "1",
        )
    }

    private companion object {
        const val MAX_HISTORY = 100
        val KEY_MODE = stringPreferencesKey("mode")
        val KEY_HISTORY = stringPreferencesKey("history")
        val KEY_LAST_CHECK = longPreferencesKey("last_check")
    }
}
