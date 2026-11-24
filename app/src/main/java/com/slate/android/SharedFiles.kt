// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.slate.android.storage.Entry
import java.io.File

class SharedFileProvider:FileProvider()

internal fun fileIcon(entry:Entry):Int=if(entry.directory)R.drawable.ic_storage_folder else when(entry.file.extension.lowercase()) {
    "png","jpg","jpeg","gif","webp","bmp","heic","dng"->R.drawable.ic_device_camera
    "mp4","mkv","webm","avi","mov","3gp"->R.drawable.ic_file_video
    "mp3","wav","flac","ogg","m4a","aac"->R.drawable.ic_file_audio
    "zip","7z","rar","gz","tar","xz","apk"->R.drawable.ic_file_archive
    else->R.drawable.ic_storage_file
}
internal fun openSharedFile(context:Context,root:File,file:File,share:Boolean):Boolean=runCatching {
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
