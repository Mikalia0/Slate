// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class Volume(val root: File, val label: String)

@Suppress("DEPRECATION")
fun storageVolumes(context: Context): List<Volume> {
    val primary = Environment.getExternalStorageDirectory()
    val volumes = linkedMapOf(primary.absolutePath to Volume(primary, "Internal storage"))
    if (Build.VERSION.SDK_INT >= 30) {
        val manager = context.getSystemService(StorageManager::class.java)
        manager?.storageVolumes?.forEach { volume ->
            if (volume.isPrimary || volume.state !in listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) return@forEach
            val root = volume.directory ?: return@forEach
            volumes[root.absolutePath] = Volume(root, volume.getDescription(context))
        }
    } else {
        // Legacy Android exposes app-specific paths on mounted shared volumes.
        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            if (Environment.getExternalStorageState(appDir) !in listOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) return@forEach
            if (!Environment.isExternalStorageRemovable(appDir)) return@forEach
            val root = appDir.parentFile?.parentFile?.parentFile?.parentFile ?: return@forEach
            volumes[root.absolutePath] = Volume(root, "External storage (" + root.name + ")")
        }
    }
    return volumes.values.toList()
}
