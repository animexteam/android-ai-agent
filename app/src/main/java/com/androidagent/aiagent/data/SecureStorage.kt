package com.androidagent.aiagent.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

class SecureStorage(private val context: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "secure_agent_storage"
        const val KEY_API_KEY = "secure_api_key"
        private const val TAG = "SecureStorage"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Security exception creating encrypted preferences", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "IO exception creating encrypted preferences", e)
            null
        }
    }

    fun getString(key: String): String? {
        return try {
            prefs?.getString(key, null)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Security exception reading key: $key", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "IO exception reading key: $key", e)
            null
        }
    }

    fun putString(key: String, value: String) {
        try {
            prefs?.edit()?.putString(key, value)?.apply()
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Security exception writing key: $key", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO exception writing key: $key", e)
        }
    }

    fun remove(key: String) {
        try {
            prefs?.edit()?.remove(key)?.apply()
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Security exception removing key: $key", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO exception removing key: $key", e)
        }
    }

    fun contains(key: String): Boolean {
        return try {
            prefs?.contains(key) ?: false
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Security exception checking key: $key", e)
            false
        } catch (e: IOException) {
            Log.e(TAG, "IO exception checking key: $key", e)
            false
        }
    }
}