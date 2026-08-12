package com.resukisu.resukisu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.resukisu.resukisu.domain.model.AppPreferences
import com.resukisu.resukisu.domain.model.PreferenceValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class AppSettingsRepository(
    context: Context,
    private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(
            SharedPreferencesMigration(appContext, "settings"),
            SharedPreferencesMigration(appContext, "theme_prefs"),
            SharedPreferencesMigration(appContext, "card_settings"),
            SharedPreferencesMigration(appContext, "susfs_config"),
            SharedPreferencesMigration(appContext, "kernel_flash_prefs"),
        ),
        scope = CoroutineScope(
            applicationScope.coroutineContext + Dispatchers.IO,
        ),
        produceFile = { appContext.preferencesDataStoreFile("app_preferences") },
    )
    private val cachedPreferences = MutableStateFlow(emptyPreferences())
    private val mutablePreferences = MutableStateFlow(AppPreferences())

    init {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .collect(::setPreferences)
        }
    }

    suspend fun preload() {
        setPreferences(readCurrentPreferences())
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        cachedPreferences.value[booleanPreferencesKey(key)] ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        cachedPreferences.value[intPreferencesKey(key)] ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        cachedPreferences.value[longPreferencesKey(key)] ?: defaultValue

    fun getFloat(key: String, defaultValue: Float): Float =
        cachedPreferences.value[floatPreferencesKey(key)] ?: defaultValue

    fun getString(key: String, defaultValue: String? = null): String? =
        cachedPreferences.value[stringPreferencesKey(key)] ?: defaultValue

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        cachedPreferences.value[stringSetPreferencesKey(key)] ?: defaultValue

    fun contains(key: String): Boolean =
        cachedPreferences.value.asMap().keys.any { it.name == key }

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
            setPreferences(prefs.toMutablePreferences())
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
        applicationScope.launch(Dispatchers.IO) {
            editBlocking(block)
        }
    }

    private fun <T> updateCachedValue(key: Preferences.Key<T>, value: T) {
        val updated = cachedPreferences.value.toMutablePreferences()
        updated[key] = value
        setPreferences(updated)
    }

    private fun <T> remove(key: Preferences.Key<T>) {
        val updated = cachedPreferences.value.toMutablePreferences()
        updated.remove(key)
        setPreferences(updated)
        editAsync { it.remove(key) }
    }

    private fun setPreferences(value: Preferences) {
        cachedPreferences.value = value
        mutablePreferences.value = AppPreferences(
            value.asMap().mapKeys { it.key.name }.mapValues { (_, raw) -> raw.toDomainValue() }
        )
    }

    private fun Any.toDomainValue(): PreferenceValue = when (this) {
        is Boolean -> PreferenceValue.BooleanValue(this)
        is Int -> PreferenceValue.IntValue(this)
        is Long -> PreferenceValue.LongValue(this)
        is Float -> PreferenceValue.FloatValue(this)
        is String -> PreferenceValue.StringValue(this)
        is Set<*> -> PreferenceValue.StringSetValue(filterIsInstance<String>().toSet())
        else -> PreferenceValue.StringValue(toString())
    }
}
