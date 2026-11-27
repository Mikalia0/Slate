package com.slate.android.storage;
import android.os.ParcelFileDescriptor;
interface IApkInstallService {
    String install(in ParcelFileDescriptor apk, long size) = 0;
    void destroy() = 16777114;
}
