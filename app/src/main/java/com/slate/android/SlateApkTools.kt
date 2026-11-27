// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.MessageDigest
import java.security.cert.X509Certificate

internal data class ApkInspection(val label:String,val pkg:String,val version:String,val minSdk:Int,val targetSdk:Int,val permissions:List<String>,val fingerprints:List<String>,val dex:List<String>,val libraries:List<String>,val components:List<String>,val verified:Boolean)
@Suppress("DEPRECATION")
internal fun inspectSlateApk(context:Context,file:File):ApkInspection{
    val pm=context.packageManager
    val info=checkNotNull(pm.getPackageArchiveInfo(file.path,PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS))
    val app=checkNotNull(info.applicationInfo).apply{sourceDir=file.path;publicSourceDir=file.path}
    val verification=runCatching{ApkVerifier.Builder(file).build().verify()}.getOrNull()
    val fingerprints=verification?.signerCertificates.orEmpty().map{certificate->
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":"){"%02X".format(it.toInt() and 255)}
    }
    val entries=java.util.zip.ZipFile(file).use{zip->zip.entries().asSequence().take(100000).map{it.name}.toList()}
    val components=info.activities.orEmpty().map{"Activity · "+it.name}+info.services.orEmpty().map{"Service · "+it.name}+info.receivers.orEmpty().map{"Receiver · "+it.name}+info.providers.orEmpty().map{"Provider · "+it.name}
    return ApkInspection(app.loadLabel(pm).toString(),info.packageName,info.versionName.orEmpty()+" ("+(if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())+")",app.minSdkVersion,app.targetSdkVersion,info.requestedPermissions.orEmpty().sorted(),fingerprints,entries.filter{it.matches(Regex("classes[0-9]*[.]dex"))},entries.filter{it.startsWith("lib/")&&it.endsWith(".so")},components.sorted(),verification?.isVerified==true)
}

