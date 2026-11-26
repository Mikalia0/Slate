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
    val verification=ApkVerifier.Builder(file).build().verify()
    val fingerprints=verification.signerCertificates.map{certificate->
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":"){"%02X".format(it.toInt() and 255)}
    }
    val entries=java.util.zip.ZipFile(file).use{zip->zip.entries().asSequence().take(100000).map{it.name}.toList()}
    val components=info.activities.orEmpty().map{"Activity · "+it.name}+info.services.orEmpty().map{"Service · "+it.name}+info.receivers.orEmpty().map{"Receiver · "+it.name}+info.providers.orEmpty().map{"Provider · "+it.name}
    return ApkInspection(app.loadLabel(pm).toString(),info.packageName,info.versionName.orEmpty()+" ("+(if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())+")",app.minSdkVersion,app.targetSdkVersion,info.requestedPermissions.orEmpty().sorted(),fingerprints,entries.filter{it.matches(Regex("classes[0-9]*[.]dex"))},entries.filter{it.startsWith("lib/")&&it.endsWith(".so")},components.sorted(),verification.isVerified)
}

internal fun signSlateApk(context:Context,root:File,file:File,uri:Uri,password:CharArray,alias:String):File{
    require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
    val store=KeyStore.getInstance("PKCS12")
    checkNotNull(context.contentResolver.openInputStream(uri)).use{store.load(it,password)}
    val keyAlias=alias.ifBlank{store.aliases().toList().single{store.isKeyEntry(it)}}
    val key=store.getKey(keyAlias,password) as PrivateKey
    val chain=checkNotNull(store.getCertificateChain(keyAlias)).map{it as X509Certificate}
    val parent=checkNotNull(file.parentFile)
    require(safeFile(root,parent)&&parent.canWrite())
    val tmp=Files.createTempFile(parent.toPath(),".slate-sign-",".apk").toFile()
    try{
        val signer=ApkSigner.SignerConfig.Builder(keyAlias,key,chain).build()
        ApkSigner.Builder(listOf(signer)).setInputApk(file).setOutputApk(tmp).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign()
        require(ApkVerifier.Builder(tmp).build().verify().isVerified)
        for(index in 0..9999){
            val suffix=if(index==0)"-signed"else "-signed-$index"
            val dest=File(parent,file.nameWithoutExtension+suffix+".apk")
            try{Files.move(tmp.toPath(),dest.toPath());return dest}
            catch(_:java.nio.file.FileAlreadyExistsException){}
        }
        error("No available APK name")
    }finally{tmp.delete()}
}
