package com.aisavexa.recents

import android.content.Context
import java.io.File

object Prefs {
    const val ACTION_RELOAD = "com.aisavexa.recents.RELOAD_OVERLAY"

    const val KEY_W = "overlay_w_dp"
    const val KEY_H = "overlay_h_dp"
    const val KEY_X = "overlay_x_dp"
    const val KEY_Y = "overlay_y_dp"

    const val DEF_W = 120
    const val DEF_H = 48
    const val DEF_X = 0
    const val DEF_Y = 0

    private const val FILE = "recents_prefs"
    private const val BG_FILE = "recents_bg.jpg"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getInt(ctx: Context, key: String, def: Int): Int = prefs(ctx).getInt(key, def)

    fun setInt(ctx: Context, key: String, value: Int) {
        prefs(ctx).edit().putInt(key, value).apply()
    }

    fun resetOverlay(ctx: Context) {
        setInt(ctx, KEY_W, DEF_W)
        setInt(ctx, KEY_H, DEF_H)
        setInt(ctx, KEY_X, DEF_X)
        setInt(ctx, KEY_Y, DEF_Y)
    }

    fun bgFile(ctx: Context): File = File(ctx.filesDir, BG_FILE)

    fun resetBackground(ctx: Context) {
        bgFile(ctx).delete()
    }
}
