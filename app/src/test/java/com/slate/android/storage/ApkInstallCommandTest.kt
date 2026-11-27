package com.slate.android.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkInstallCommandTest {
    @Test fun normalInstall(){
        assertEquals(listOf("/system/bin/pm","install","-r","--user","10","-S","12345","-"),apkInstallCommand(10,12345))
    }
    @Test(expected=IllegalArgumentException::class) fun emptyApk(){apkInstallCommand(0,0)}
    @Test(expected=IllegalArgumentException::class) fun invalidUser(){apkInstallCommand(-1,10)}
}
