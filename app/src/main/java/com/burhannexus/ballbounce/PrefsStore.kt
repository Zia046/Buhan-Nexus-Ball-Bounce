package com.burhannexus.ballbounce

import android.content.Context
import android.content.SharedPreferences
import com.burhannexus.ballbounce.engine.KeyValueStore

/** SharedPreferences-backed storage. Uses commit() so scores are on disk before we continue. */
class PrefsStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("burhan_nexus_ball_bounce", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
    }

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).commit()
        }

    companion object {
        private const val KEY_SOUND = "sound_on"
    }
}
