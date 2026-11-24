package com.videomerger.tablet.util

import android.os.Environment
import android.os.StatFs

object StorageSpace {
    fun availableBytes(): Long? = try {
        StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
    } catch (_: Exception) {
        null
    }

    fun totalBytes(): Long? = try {
        StatFs(Environment.getExternalStorageDirectory().absolutePath).totalBytes
    } catch (_: Exception) {
        null
    }
}
