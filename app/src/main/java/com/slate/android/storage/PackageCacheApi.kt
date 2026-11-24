// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// AOSP IPackageManager cache-only operations, invoked in the Shizuku user service.
// The global target is a requested free-space threshold, not an amount to delete.
internal const val GLOBAL_CACHE_TARGET = 1L shl 40
internal const val FLAG_ALLOCATE_DEFY_ALL_RESERVED = 1 shl 1
private const val OBSERVER_DESCRIPTOR = "android.content.pm.IPackageDataObserver"

internal class PackageCacheApi {
    private val observerType = Class.forName(OBSERVER_DESCRIPTOR)
    private val managerType = Class.forName("android.content.pm.IPackageManager")
    private val manager: Any

    init {
        if (Build.VERSION.SDK_INT >= 28) {
            // Limit hidden API access to the privileged service and these interfaces.
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/ServiceManager;",
                "Landroid/content/pm/IPackageManager",
                "Landroid/content/pm/IPackageDataObserver"
            )
        }
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "package") as IBinder
        manager = checkNotNull(Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder))
    }

    private class CacheObserver : Binder() {
        private val completion = CountDownLatch(1)
        @Volatile private var succeeded = false
        init { attachInterface(null, OBSERVER_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.FIRST_CALL_TRANSACTION) {
                data.enforceInterface(OBSERVER_DESCRIPTOR)
                data.readString()
                succeeded = data.readInt() != 0
                completion.countDown()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }

        fun result(seconds: Long): Int =
            if (!completion.await(seconds, TimeUnit.SECONDS)) -1 else if (succeeded) 0 else 1
    }

    private fun withObserver(seconds: Long, invoke: (Any) -> Unit): Int {
        val callback = CacheObserver()
        val observer = checkNotNull(Class.forName("android.content.pm.IPackageDataObserver\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, callback))
        val identity = Binder.clearCallingIdentity()
        try {
            invoke(observer)
            return callback.result(seconds)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    fun supportsGlobal(): Boolean = runCatching {
        managerType.getMethod("freeStorageAndNotify", String::class.java,
            Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, observerType)
        true
    }.getOrDefault(false)

    fun supportsSelective(): Boolean = runCatching {
        managerType.getMethod("deleteApplicationCacheFilesAsUser",
            String::class.java, Int::class.javaPrimitiveType, observerType)
        true
    }.getOrDefault(false)

    fun trimAll(): Int = withObserver(120) { observer ->
        managerType.getMethod("freeStorageAndNotify", String::class.java,
            Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, observerType)
            .invoke(manager, null, GLOBAL_CACHE_TARGET, FLAG_ALLOCATE_DEFY_ALL_RESERVED, observer)
    }

    fun clearPackage(packageName: String, user: Int): Boolean = withObserver(30) { observer ->
        managerType.getMethod("deleteApplicationCacheFilesAsUser", String::class.java,
            Int::class.javaPrimitiveType, observerType).invoke(manager, packageName, user, observer)
    } == 0
}
