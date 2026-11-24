// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.slate.android.storage.Entry
import com.slate.android.storage.FileCategory
import com.slate.android.storage.fileCategory
import java.io.File

class SharedFileProvider:FileProvider()

internal fun fileIcon(entry:Entry):Int=if(entry.directory)R.drawable.ic_storage_folder else when(fileCategory(entry.file.extension)) {
    FileCategory.IMAGES->R.drawable.ic_device_camera
    FileCategory.VIDEOS->R.drawable.ic_file_video
    FileCategory.AUDIO->R.drawable.ic_file_audio
    FileCategory.INSTALLERS->R.drawable.ic_file_apk
    FileCategory.ARCHIVES->R.drawable.ic_file_archive
    else->R.drawable.ic_storage_file
}
internal fun openSharedFile(context:Context,root:File,file:File,share:Boolean):Boolean=runCatching {
    val relative=com.slate.android.storage.protectedRelative(file)
    if(relative!=null&&com.slate.android.storage.shizukuGranted()){
        val uri=android.net.Uri.Builder().scheme("content").authority(context.packageName+".protected").apply{relative.split('/').forEach{appendPath(it)}}.build()
        val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())?:"application/octet-stream"
        val intent=if(share)Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri)}else Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,mime)}
        intent.clipData=ClipData.newRawUri(file.name,uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent,if(share)"Share file" else "Open with"))
        return@runCatching true
    }
    val canonicalRoot=root.canonicalFile
    val target=file.canonicalFile
    require(target.toPath().startsWith(canonicalRoot.toPath()) && target!=canonicalRoot && target.isFile)
    val uri=FileProvider.getUriForFile(context,context.packageName+".files",target)
    val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(target.extension.lowercase()) ?: "application/octet-stream"
    val intent=if(share)Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri)}
    else Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,mime)}
    intent.clipData=ClipData.newRawUri(target.name,uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent,if(share)"Share file" else "Open with"))
    true
}.getOrDefault(false)
