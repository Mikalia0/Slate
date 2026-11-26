// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.ParcelFileDescriptor
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

internal fun slateDocuments(context:Context):File=File(context.filesDir,"shared_documents").apply{mkdirs()}
internal fun exportSlateDocument(context:Context,root:File,file:File){
    require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
    val dest=slateDocuments(context)
    val name=file.name
    val tmp=Files.createTempFile(dest.toPath(),".export-",".tmp").toFile()
    try{
        file.inputStream().use{input->tmp.outputStream().use{input.copyTo(it)}}
        for(index in 0..9999){
            val target=File(dest,if(index==0)name else "$index-$name")
            try{Files.move(tmp.toPath(),target.toPath());return}
            catch(_:java.nio.file.FileAlreadyExistsException){}
        }
        error("No available document name")
    }finally{tmp.delete()}
}

/** Read-only SAF root, limited to copies explicitly exported into Slate's private directory. */
class SlateDocumentsProvider:DocumentsProvider(){
    private val rootId="slate"
    private val roots=arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_ICON,DocumentsContract.Root.COLUMN_MIME_TYPES)
    private val docs=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_FLAGS)
    override fun onCreate()=true
    private fun directory()=slateDocuments(requireNotNull(context))
    private fun resolve(id:String):File{
        val root=directory()
        if(id==rootId)return root
        if(!id.startsWith("$rootId:"))throw FileNotFoundException()
        val name=id.substringAfter(':')
        if(!validFileName(name)||name.startsWith(".export-"))throw FileNotFoundException()
        val file=File(root,name)
        if(!safeFile(root,file)||!file.isFile)throw FileNotFoundException()
        return file
    }
    override fun queryRoots(projection:Array<out String>?):Cursor{
        val c=MatrixCursor(projection?.map{it}?.toTypedArray()?:roots)
        val row=mapOf(DocumentsContract.Root.COLUMN_ROOT_ID to rootId,DocumentsContract.Root.COLUMN_DOCUMENT_ID to rootId,DocumentsContract.Root.COLUMN_TITLE to "Slate",DocumentsContract.Root.COLUMN_FLAGS to 0,DocumentsContract.Root.COLUMN_ICON to R.drawable.ic_slate,DocumentsContract.Root.COLUMN_MIME_TYPES to "*/*")
        c.addRow(c.columnNames.map{row[it]})
        return c
    }
    private fun add(c:MatrixCursor,file:File){
        val dir=file==directory()
        val row=mapOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID to if(dir)rootId else "$rootId:${file.name}",DocumentsContract.Document.COLUMN_DISPLAY_NAME to if(dir)"Slate"else file.name,DocumentsContract.Document.COLUMN_MIME_TYPE to if(dir)DocumentsContract.Document.MIME_TYPE_DIR else (MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())?:"application/octet-stream"),DocumentsContract.Document.COLUMN_SIZE to if(dir)null else file.length(),DocumentsContract.Document.COLUMN_LAST_MODIFIED to file.lastModified(),DocumentsContract.Document.COLUMN_FLAGS to 0)
        c.addRow(c.columnNames.map{row[it]})
    }
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor=MatrixCursor(projection?.map{it}?.toTypedArray()?:docs).apply{add(this,resolve(documentId))}
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor{
        if(parentDocumentId!=rootId)throw FileNotFoundException()
        return MatrixCursor(projection?.map{it}?.toTypedArray()?:docs).apply{
            directory().listFiles().orEmpty().filter{!it.name.startsWith(".export-")&&it.isFile&&safeFile(directory(),it)}.sortedBy{it.name.lowercase()}.forEach{add(this,it)}
        }
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor{
        if(mode!="r"||documentId==rootId)throw FileNotFoundException("Read only")
        return ParcelFileDescriptor.open(resolve(documentId),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun isChildDocument(parentDocumentId:String,documentId:String):Boolean=parentDocumentId==rootId&&documentId!=rootId&&runCatching{resolve(documentId)}.isSuccess
}
