package com.crystalolympus.crystalolympusgame

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The single edge-to-edge immersive helper; every activity calls this one. */
object ScreenFit {
    fun apply(activity: Activity) {
        val window = activity.window
        // Going through decorView first is what proves it has been created —
        // reaching for the insets controller before setContentView throws.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
