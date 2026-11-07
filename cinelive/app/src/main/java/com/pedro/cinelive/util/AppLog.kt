package com.pedro.cinelive.util

import android.util.Log
import com.pedro.cinelive.BuildConfig

object AppLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null, debugOnly: Boolean = false) {
        if (debugOnly && !BuildConfig.DEBUG) return
        if (tr != null && BuildConfig.DEBUG) {
            Log.w(tag, msg, tr)
        } else {
            Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null, debugOnly: Boolean = false) {
        if (debugOnly && !BuildConfig.DEBUG) return
        if (tr != null && BuildConfig.DEBUG) {
            Log.e(tag, msg, tr)
        } else {
            Log.e(tag, msg)
        }
    }
}
