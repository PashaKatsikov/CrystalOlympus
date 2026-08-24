package com.crystalolympus.crystalolympusgame.bearing

import android.util.Log
import com.crystalolympus.crystalolympusgame.BuildConfig

/**
 * The launch flow's only logger. Debug builds print; release builds print
 * nothing, because the guard reads a compile-time constant and R8 folds the
 * dead branch out of the class entirely.
 *
 * Funnelling everything through one object is what makes that claim checkable
 * with a single grep, and it leaves one place to prefix a tag, mute a chatty
 * area, or tee output to a file while bringing a handset up.
 */
internal object Journal {
    fun i(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.i(tag, msg) }
    fun w(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.w(tag, msg) }
    fun w(tag: String, msg: String, t: Throwable) { if (BuildConfig.DEBUG) Log.w(tag, msg, t) }
    fun e(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.e(tag, msg) }
    fun e(tag: String, msg: String, t: Throwable) { if (BuildConfig.DEBUG) Log.e(tag, msg, t) }
}
