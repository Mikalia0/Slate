// SPDX-License-Identifier: GPL-3.0-only
package com.slate.payload;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Locale;

/** User-granted, read-only SAF access to the host app's external files directory.
 * Never traverses into internal private data or another app's directory.
 */
public final class ExternalDocumentsProvider extends DocumentsProvider {
    private static final String ID = "files";
    private static final String[] ROOT_COLUMNS = {
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_AVAILABLE_BYTES
    };
    private static final String[] DOC_COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
    };
    @Override public boolean onCreate() { return true; }
    private File root() throws IOException {
        File external = getContext().getExternalFilesDir(null);
        if (external == null) throw new IOException("External files unavailable");
        return external.getCanonicalFile();
    }
    private File resolve(String id) throws FileNotFoundException {
        try {
            File root = root();
            if (ID.equals(id)) return root;
            if (id == null || !id.startsWith(ID + "/")) throw new IOException("Invalid document ID");
            String rel = id.substring(ID.length() + 1);
            for (String part : rel.split("/", -1)) {
                if (part.isEmpty() || part.equals(".") || part.equals("..") || part.indexOf('\\') >= 0) throw new IOException("Invalid path");
            }
            File file = new File(root, rel).getCanonicalFile();
            if (!file.getPath().startsWith(root.getPath() + File.separator) || !file.exists()) throw new IOException("Outside root");
            return file;
        } catch (IOException | RuntimeException e) {
            throw new FileNotFoundException("Document unavailable");
        }
    }
    private String idFor(File file) throws IOException {
        String root = root().getPath();
        String path = file.getCanonicalPath();
        if (path.equals(root)) return ID;
        if (!path.startsWith(root + File.separator)) throw new IOException("Outside root");
        return ID + "/" + path.substring(root.length()+1);
    }
    private String mime(File file) {
        if (file.isDirectory()) return Document.MIME_TYPE_DIR;
        String name=file.getName();
        int dot=name.lastIndexOf('.');
        String type=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(Locale.ROOT));
        return type==null?"application/octet-stream":type;
    }
    private void addDoc(MatrixCursor cursor, File file) throws IOException {
        if(!file.isDirectory()&&!file.isFile())return;
        String id=idFor(file);
        MatrixCursor.RowBuilder row=cursor.newRow();
        for(String col:cursor.getColumnNames()){
            Object value=null;
            if(Document.COLUMN_DOCUMENT_ID.equals(col)) value=id;
            else if(Document.COLUMN_DISPLAY_NAME.equals(col))value=ID.equals(id)?"External files":file.getName();
            else if(Document.COLUMN_MIME_TYPE.equals(col))value=mime(file);
            else if(Document.COLUMN_SIZE.equals(col))value=file.isFile()?file.length():null;
            else if(Document.COLUMN_LAST_MODIFIED.equals(col))value=file.lastModified();
            else if(Document.COLUMN_FLAGS.equals(col))value=0;
            row.add(value);
        }
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor=new MatrixCursor(projection==null?ROOT_COLUMNS:projection);
        try {
            File root=root();
            if(!root.isDirectory())return cursor;
            String title=getContext().getApplicationInfo().loadLabel(getContext().getPackageManager()).toString();
            MatrixCursor.RowBuilder row=cursor.newRow();
            for(String col:cursor.getColumnNames()){
                Object value=null;
                if(Root.COLUMN_ROOT_ID.equals(col)||Root.COLUMN_DOCUMENT_ID.equals(col))value=ID;
                else if(Root.COLUMN_TITLE.equals(col))value=title;
                else if(Root.COLUMN_SUMMARY.equals(col))value="External files · read only";
                else if(Root.COLUMN_FLAGS.equals(col))value=Root.FLAG_LOCAL_ONLY;
                else if(Root.COLUMN_AVAILABLE_BYTES.equals(col))value=root.getUsableSpace();
                row.add(value);
            }
        }catch(IOException ignored){}
        return cursor;
    }
    @Override public Cursor queryDocument(String id,String[] projection) throws FileNotFoundException {
        MatrixCursor cursor=new MatrixCursor(projection==null?DOC_COLUMNS:projection);
        try{addDoc(cursor,resolve(id));}catch(IOException e){throw new FileNotFoundException("Document unavailable");}
        return cursor;
    }
    @Override public Cursor queryChildDocuments(String id,String[] projection,String sortOrder) throws FileNotFoundException {
        File parent=resolve(id);
        if(!parent.isDirectory())throw new FileNotFoundException("Not a directory");
        MatrixCursor cursor=new MatrixCursor(projection==null?DOC_COLUMNS:projection);
        File[] children=parent.listFiles();
        if(children!=null) for(File child:children){
            try{
                // Skip symlinks and aliases, including paths redirected outside the host.
                if(!child.getAbsoluteFile().equals(child.getCanonicalFile()))continue;
                addDoc(cursor,child);
            }catch(IOException ignored){}
        }
        return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        if(signal!=null)signal.throwIfCanceled();
        File file=resolve(id);
        if(!file.isFile())throw new FileNotFoundException("Not a file");
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public boolean isChildDocument(String parentId,String documentId) {
        try {return resolve(documentId).getPath().startsWith(resolve(parentId).getPath()+File.separator);}
        catch(FileNotFoundException e){return false;}
    }
}
