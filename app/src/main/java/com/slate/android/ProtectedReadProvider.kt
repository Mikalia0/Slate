// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.slate.android.storage.*
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking

/** Non-exported provider; Android grants read access only to the exact chosen URI. */
class ProtectedReadProvider:ContentProvider(){
    override fun onCreate()=true
    private fun relative(uri:Uri):String{
        val path=uri.pathSegments.joinToString("/")
        require(validReadRelative(path)&&uri.pathSegments.none{it.contains('/')})
        return path
    }
    override fun getType(uri:Uri):String=MimeTypeMap.getSingleton().getMimeTypeFromExtension(relative(uri).substringAfterLast('.',"").lowercase())?:"application/octet-stream"
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{
        if(mode!="r")throw FileNotFoundException("Read only")
        val ctx=context?:throw FileNotFoundException()
        val path=relative(uri)
        return try{runBlocking{withProtectedRead(ctx){it.open(path)}}}
        catch(_:Exception){throw FileNotFoundException("Protected file unavailable")}
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor{
        val path=relative(uri)
        val columns=projection?.toList()?.toTypedArray()?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply{addRow(columns.map{if(it==OpenableColumns.DISPLAY_NAME)path.substringAfterLast('/')else null})}
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException("Read only")
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("Read only")
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("Read only")
}
