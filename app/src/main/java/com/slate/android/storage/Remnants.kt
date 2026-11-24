// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Candidates only: retained user data or another profile may still own these folders.
@Suppress("DEPRECATION")
internal suspend fun findMediaRemnants(context:Context,root:File,excluded:Set<String>):List<File> = withContext(Dispatchers.IO){
    val media=File(root,"Android/media")
    if(Files.isSymbolicLink(File(root,"Android").toPath()) || Files.isSymbolicLink(media.toPath()))return@withContext emptyList()
    val installed=context.packageManager.getInstalledPackages(0).map{it.packageName}.toSet()
    if(installed.isEmpty())return@withContext emptyList()
    val pattern=Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    val rows=ArrayList<File>()
    for(folder in media.listFiles().orEmpty()){
        currentCoroutineContext().ensureActive()
        val relative=folder.relativeTo(root).path
        if(excluded.any{relative==it || relative.startsWith("$it/") || it.startsWith("$relative/")})continue
        if(pattern.matches(folder.name) && folder.name !in installed && Files.isDirectory(folder.toPath(),LinkOption.NOFOLLOW_LINKS))rows.add(folder)
    }
    rows.sortedBy{it.name}
}
