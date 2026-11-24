package com.slate.android.storage;
import android.os.ParcelFileDescriptor;
interface IProtectedReadService {
    ParcelFileDescriptor listDirectory(String relative) = 0;
    ParcelFileDescriptor openReadOnly(String relative) = 1;
    void destroy() = 16777114;
}
