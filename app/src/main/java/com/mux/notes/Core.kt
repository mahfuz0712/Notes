package com.mux.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Prefs {
    private lateinit var sp: SharedPreferences
    var accent by mutableIntStateOf(0)
    var hasPin by mutableStateOf(false)

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("mux_notes", Context.MODE_PRIVATE)
        accent = sp.getInt("accent", 0)
        hasPin = !sp.getString("pin", "").isNullOrEmpty()
    }

    fun updateAccent(i: Int) {
        accent = i
        sp.edit().putInt("accent", i).apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        sp.edit().putString("salt", salt).putString("pin", hash(salt, pin)).apply()
        hasPin = true
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("salt", "") ?: ""
        return hash(salt, pin) == sp.getString("pin", "")
    }

    fun clearPin() {
        sp.edit().remove("pin").remove("salt").apply()
        hasPin = false
    }
}

fun f1(v: Float): String = String.format(Locale.US, "%.1f", v)

fun fmtTime(ms: Long): String {
    if (ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "JUST NOW"
        d < 3_600_000 -> "${d / 60_000} MIN AGO"
        d < 86_400_000 -> "${d / 3_600_000} H AGO"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ms)).uppercase()
    }
}
