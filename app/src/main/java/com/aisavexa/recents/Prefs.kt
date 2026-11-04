package com.aisavexa.recents

import android.content.Context
import java.io.File

object Prefs {
    const val ACTION_RELOAD = "com.aisavexa.recents.RELOAD_OVERLAY"

    const val KEY_W = "overlay_w_dp"
    const val KEY_H = "overlay_h_dp"
    const val KEY_X = "overlay_x_dp"
    const val KEY_Y = "overlay_y_dp"
    const val KEY_CAL = "calibrating"

    const val DEF_W = 120
    const val DEF_H = 48
    const val DEF_Y = 0

    private const val FILE = "recents_prefs"
    private const val BG_FILE = "recents_bg.jpg"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun screenWidthDp(ctx: Context): Float {
        val m = ctx.resources.displayMetrics
        return m.widthPixels / m.density
    }

    // Samsung 3-button bar mein Recents button left se ~1/6 par hota hai
    fun defaultX(ctx: Context): Int =
        (screenWidthDp(ctx) / 6f - DEF_W / 2f).toInt().coerceAtLeast(0)

    fun getW(ctx: Context): Int = prefs(ctx).getInt(KEY_W, DEF_W)
    fun getH(ctx: Context): Int = prefs(ctx).getInt(KEY_H, DEF_H)
    fun getX(ctx: Context): Int = prefs(ctx).getInt(KEY_X, defaultX(ctx))
    fun getY(ctx: Context): Int = prefs(ctx).getInt(KEY_Y, DEF_Y)

    fun setInt(ctx: Context, key: String, value: Int) {
        prefs(ctx).edit().putInt(key, value).apply()
    }

    fun isCalibrating(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CAL, false)

    fun setCalibrating(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CAL, on).apply()
    }

    fun resetOverlay(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_W).remove(KEY_H).remove(KEY_X).remove(KEY_Y)
            .apply()
    }

    fun bgFile(ctx: Context): File = File(ctx.filesDir, BG_FILE)

    fun resetBackground(ctx: Context) {
        bgFile(ctx).delete()
    }
}
