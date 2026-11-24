package com.slate.android.storage;
import android.os.ParcelFileDescriptor;
interface IProtectedReadService {
    ParcelFileDescriptor listDirectory(String relative) = 0;
    ParcelFileDescriptor openReadOnly(String relative) = 1;
    ParcelFileDescriptor createNewFile(String relative) = 2;
    boolean createDirectory(String relative) = 3;
    boolean renameEntry(String source, String destination) = 4;
    boolean deleteEntry(String relative) = 5;
    void destroy() = 16777114;
}
