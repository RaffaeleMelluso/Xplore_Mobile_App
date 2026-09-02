package com.example.progmob_progetto_melluso.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Single preferences DataStore instance for the app, backed by
 * `user_preferences.preferences_pb on disk.
 */
private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * User-level preferences and profile data persisted with Jetpack DataStore
 * (the preferences requirement: preferences must be stored persistently and reactively).
 *
 * How it works: every preference is a key in the preferences DataStore and is
 * exposed as an immutable [UserPreferences] through a continuous [Flow], so the
 * profile screen observes it reactively and any toggle is persisted on disk.
 *
 * @param context any context; the store is bound to the application context.
 * @param userIdProvider Supplies the signed-in user id (Firebase uid): every
 *   preference and the avatar are keyed per user, so switching account yields
 *   the new user's own settings and picture instead of the previous one's.
 */
class UserPreferencesRepository(
    context: Context,
    private val userIdProvider: () -> String = { "local-user" }
) {

    private val store = context.applicationContext.userPreferencesStore

    /** Reactive snapshot of the current user's preferences/profile fields. */
    val preferences: Flow<UserPreferences> = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                anonymous = prefs[anonymousKey()] ?: false,
                showTravelPaths = prefs[showTravelPathsKey()] ?: true,
                avatarUri = prefs[avatarKey()],
                themeMode = prefs[themeModeKey()]?.let { value ->
                    ThemeMode.entries.firstOrNull { it.name == value }
                } ?: ThemeMode.SYSTEM
            )
        }

    /** Sets the anonymous-mode preference and persists it (per user). */
    suspend fun setAnonymous(value: Boolean) {
        store.edit { it[anonymousKey()] = value }
    }

    /** Sets the travel-path visibility preference and persists it (per user). */
    suspend fun setShowTravelPaths(value: Boolean) {
        store.edit { it[showTravelPathsKey()] = value }
    }

    /** Sets the app theme mode preference and persists it (per user). */
    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeModeKey()] = mode.name }
    }

    /** Stores the local file URI of the avatar (null removes it, per user). */
    suspend fun setAvatarUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(avatarKey()) else prefs[avatarKey()] = uri
        }
    }

    private fun anonymousKey() = booleanPreferencesKey(KEY_PREFIX_ANONYMOUS + userIdProvider())
    private fun showTravelPathsKey() = booleanPreferencesKey(KEY_PREFIX_SHOW_PATHS + userIdProvider())
    private fun avatarKey() = stringPreferencesKey(KEY_PREFIX_AVATAR + userIdProvider())
    private fun themeModeKey() = stringPreferencesKey(KEY_PREFIX_THEME + userIdProvider())

    private companion object {    /** Per-user key prefixes; the uid is appended at access time. */
    const val KEY_PREFIX_ANONYMOUS = "anonymous_mode_"
    const val KEY_PREFIX_SHOW_PATHS = "show_travel_paths_"
    const val KEY_PREFIX_AVATAR = "avatar_uri_"
    const val KEY_PREFIX_THEME = "theme_mode_"
    }
}

/**
 * The application theme the user wants.
 *
 * @property SYSTEM follow the system night/day setting.
 * @property LIGHT force the light (day) theme.
 * @property DARK force the dark (night) theme.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Immutable user preferences/profile snapshot displayed on the Profile screen.
 *
 * @param anonymous whether the user wants to appear anonymous to others.
 * @param showTravelPaths whether the user's travel paths are visible to others.
 * @param avatarUri local file URI of the profile picture, or null for none.
 * @param themeMode the app theme chosen by the user (system by default).
 */
data class UserPreferences(
    val anonymous: Boolean = false,
    val showTravelPaths: Boolean = true,
    val avatarUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)