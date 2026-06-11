package com.resukisu.resukisu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resukisu.resukisu.KernelSUApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "settings"),
            SharedPreferencesMigration(context, "theme_prefs"),
            SharedPreferencesMigration(context, "card_settings"),
            SharedPreferencesMigration(context, "susfs_config"),
            SharedPreferencesMigration(context, "kernel_flash_prefs"),
        )
    }
)

class AppPreferencesRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.appPreferencesDataStore
    private val _preferences = MutableStateFlow(emptyPreferences())

    suspend fun preload() {
        _preferences.value = readCurrentPreferences()
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .collect { prefs ->
                    _preferences.value = prefs
                }
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        _preferences.value[booleanPreferencesKey(key)] ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        _preferences.value[intPreferencesKey(key)] ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        _preferences.value[longPreferencesKey(key)] ?: defaultValue

    fun getFloat(key: String, defaultValue: Float): Float =
        _preferences.value[floatPreferencesKey(key)] ?: defaultValue

    fun getString(key: String, defaultValue: String? = null): String? =
        _preferences.value[stringPreferencesKey(key)] ?: defaultValue

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        _preferences.value[stringSetPreferencesKey(key)] ?: defaultValue

    fun putBoolean(key: String, value: Boolean) {
        updateCachedValue(booleanPreferencesKey(key), value)
        editAsync { it[booleanPreferencesKey(key)] = value }
    }

    fun putInt(key: String, value: Int) {
        updateCachedValue(intPreferencesKey(key), value)
        editAsync { it[intPreferencesKey(key)] = value }
    }

    fun putLong(key: String, value: Long) {
        updateCachedValue(longPreferencesKey(key), value)
        editAsync { it[longPreferencesKey(key)] = value }
    }

    fun putFloat(key: String, value: Float) {
        updateCachedValue(floatPreferencesKey(key), value)
        editAsync { it[floatPreferencesKey(key)] = value }
    }

    fun putString(key: String, value: String?) {
        val preferenceKey = stringPreferencesKey(key)
        if (value == null) {
            remove(preferenceKey)
        } else {
            updateCachedValue(preferenceKey, value)
            editAsync { it[preferenceKey] = value }
        }
    }

    fun putStringSet(key: String, value: Set<String>) {
        updateCachedValue(stringSetPreferencesKey(key), value)
        editAsync { it[stringSetPreferencesKey(key)] = value }
    }

    fun remove(key: String) {
        remove(booleanPreferencesKey(key))
        remove(intPreferencesKey(key))
        remove(longPreferencesKey(key))
        remove(floatPreferencesKey(key))
        remove(stringPreferencesKey(key))
        remove(stringSetPreferencesKey(key))
    }

    suspend fun editBlocking(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            block(prefs)
            _preferences.value = prefs.toMutablePreferences()
        }
    }

    private suspend fun readCurrentPreferences(): Preferences =
        withContext(Dispatchers.IO) {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .first()
        }

    private fun editAsync(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        (appContext as? KernelSUApplication)?.applicationScope?.launch(Dispatchers.IO) {
            editBlocking(block)
        }
    }

    private fun <T> updateCachedValue(key: Preferences.Key<T>, value: T) {
        val mutablePreferences = _preferences.value.toMutablePreferences()
        mutablePreferences[key] = value
        _preferences.value = mutablePreferences
    }

    private fun <T> remove(key: Preferences.Key<T>) {
        val mutablePreferences = _preferences.value.toMutablePreferences()
        mutablePreferences.remove(key)
        _preferences.value = mutablePreferences
        editAsync { it.remove(key) }
    }
}

val Context.appPreferences: AppPreferencesRepository
    get() = (applicationContext as KernelSUApplication).ensurePreferencesRepository()
