package com.pocketremote.freerdp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 저장된 호스트(호기) 목록을 읽고 쓰는 저장소.
 * 비밀번호가 포함된 민감정보라 Android Keystore 기반 EncryptedSharedPreferences를 쓴다.
 */
class HostRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "pocket_remote_freerdp_hosts_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _hosts = MutableStateFlow(loadFromDisk())
    val hosts: StateFlow<List<HostProfile>> = _hosts.asStateFlow()

    private val _defaultCredentials = MutableStateFlow(loadDefaultsFromDisk())
    val defaultCredentials: StateFlow<DefaultCredentials> = _defaultCredentials.asStateFlow()

    private fun loadFromDisk(): List<HostProfile> {
        val raw = prefs.getString(KEY_HOSTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<HostProfile>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadDefaultsFromDisk(): DefaultCredentials {
        val raw = prefs.getString(KEY_DEFAULT_CREDENTIALS, null) ?: return DefaultCredentials()
        return try {
            json.decodeFromString<DefaultCredentials>(raw)
        } catch (e: Exception) {
            DefaultCredentials()
        }
    }

    fun saveDefaultCredentials(defaults: DefaultCredentials) {
        _defaultCredentials.value = defaults
        prefs.edit().putString(KEY_DEFAULT_CREDENTIALS, json.encodeToString(defaults)).apply()
    }

    private fun persist(list: List<HostProfile>) {
        _hosts.value = list
        prefs.edit().putString(KEY_HOSTS, json.encodeToString(list)).apply()
    }

    fun upsert(profile: HostProfile) {
        val current = _hosts.value.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        persist(current)
    }

    fun delete(id: String) {
        persist(_hosts.value.filterNot { it.id == id })
    }

    companion object {
        private const val KEY_HOSTS = "hosts_json"
        private const val KEY_DEFAULT_CREDENTIALS = "default_credentials_json"

        @Volatile private var instance: HostRepository? = null
        fun getInstance(context: Context): HostRepository =
            instance ?: synchronized(this) {
                instance ?: HostRepository(context.applicationContext).also { instance = it }
            }
    }
}
