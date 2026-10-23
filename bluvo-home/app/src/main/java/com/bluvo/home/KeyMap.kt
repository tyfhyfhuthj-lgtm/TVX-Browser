package com.bluvo.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent

/** Remote-button → app mappings, shared by the launcher and the background service. */
object KeyMap {
    private const val PREFS = "bluvo_keys"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(c: Context, code: Int): String? = prefs(c).getString("k_$code", null)

    fun set(c: Context, code: Int, pkg: String) {
        prefs(c).edit().putString("k_$code", pkg).apply()
    }

    fun remove(c: Context, code: Int) {
        prefs(c).edit().remove("k_$code").apply()
    }

    fun all(c: Context): List<Pair<Int, String>> =
        prefs(c).all.mapNotNull { (k, v) ->
            val code = k.removePrefix("k_").toIntOrNull()
            if (code != null && v is String) code to v else null
        }.sortedBy { it.first }

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    fun serviceEnabled(c: Context): Boolean {
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return list?.contains(c.packageName + "/") == true
    }

    fun launch(c: Context, pkg: String) {
        val pm = c.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (ignored: Exception) {
        }
    }
}
