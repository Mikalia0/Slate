// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

internal const val DEFAULT_APK_KEY="slate.apk.clone.v1"
private const val IMPORT_PREFIX="slate.apk.import."
internal enum class ApkScheme(val title:String,val v1:Boolean,val v2:Boolean,val v3:Boolean){
    ALL("V1 + V2 + V3",true,true,true),
    V2V3("V2 + V3",false,true,true),
    V1V2("V1 + V2",true,true,false),
    V2("V2",false,true,false),
    V1("V1",true,false,false)
}
internal data class ApkSignOptions(val key:String=DEFAULT_APK_KEY,val scheme:ApkScheme=ApkScheme.ALL,val v1Name:String="Slate")
internal data class ApkSigningKey(val id:String,val label:String)
private val signingKeyLock=Any()
private fun signingStore()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
private fun keyPrefs(context:Context)=context.getSharedPreferences("apk_signing_keys",Context.MODE_PRIVATE)
private fun optionPrefs(context:Context)=context.getSharedPreferences("apk_signing_options",Context.MODE_PRIVATE)
internal fun validV1Name(value:String)=value.matches(Regex("[A-Za-z0-9_]{1,8}"))
internal fun apkPackage(context:Context,file:File):String?=runCatching{context.packageManager.getPackageArchiveInfo(file.path,0)?.packageName}.getOrNull()
internal fun apkSignOptions(context:Context,pkg:String?):ApkSignOptions{
    if(pkg==null)return ApkSignOptions()
    val prefs=optionPrefs(context)
    val key=prefs.getString("$pkg.key",DEFAULT_APK_KEY)?:DEFAULT_APK_KEY
    val scheme=runCatching{ApkScheme.valueOf(prefs.getString("$pkg.scheme","ALL")?:"ALL")}.getOrDefault(ApkScheme.ALL)
    val name=prefs.getString("$pkg.v1","Slate")?.takeIf(::validV1Name)?:"Slate"
    return ApkSignOptions(key,scheme,name)
}
internal fun rememberApkSignOptions(context:Context,pkg:String,options:ApkSignOptions?){
    val edit=optionPrefs(context).edit()
    if(options==null)edit.remove("$pkg.key").remove("$pkg.scheme").remove("$pkg.v1")
    else edit.putString("$pkg.key",options.key).putString("$pkg.scheme",options.scheme.name).putString("$pkg.v1",options.v1Name)
    edit.apply()
}
internal fun hasApkSignOptions(context:Context,pkg:String?)=pkg!=null&&optionPrefs(context).contains("$pkg.key")
internal fun listApkSigningKeys(context:Context):List<ApkSigningKey> = synchronized(signingKeyLock){
    val store=signingStore()
    listOf(ApkSigningKey(DEFAULT_APK_KEY,""))+store.aliases().toList().filter{it.startsWith(IMPORT_PREFIX)&&store.isKeyEntry(it)}.map{
        ApkSigningKey(it,keyPrefs(context).getString(it,null)?:it.takeLast(8))
    }.sortedBy{it.label.lowercase()}
}
internal fun importApkSigningKey(context:Context,uri:Uri,password:CharArray,alias:String,label:String):String=synchronized(signingKeyLock){
    val source=KeyStore.getInstance("PKCS12")
    checkNotNull(context.contentResolver.openInputStream(uri)).use{source.load(it,password)}
    val selected=alias.ifBlank{source.aliases().toList().filter{source.isKeyEntry(it)}.singleOrNull()?:error("Specify the key alias")}
    val key=source.getKey(selected,password) as PrivateKey
    val chain=checkNotNull(source.getCertificateChain(selected))
    require(key.algorithm in setOf("RSA","EC")){"Only RSA and EC keys are supported"}
    val id=IMPORT_PREFIX+java.util.UUID.randomUUID().toString()
    val protection=KeyProtection.Builder(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
        .setDigests(KeyProperties.DIGEST_SHA1,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
    if(key.algorithm=="RSA")protection.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
    signingStore().setEntry(id,KeyStore.PrivateKeyEntry(key,chain),protection.build())
    keyPrefs(context).edit().putString(id,label.trim().take(80).ifBlank{selected}).apply()
    id
}
internal fun removeApkSigningKey(context:Context,id:String)=synchronized(signingKeyLock){
    require(id.startsWith(IMPORT_PREFIX))
    signingStore().deleteEntry(id)
    keyPrefs(context).edit().remove(id).apply()
    val prefs=optionPrefs(context)
    val edit=prefs.edit()
    prefs.all.filter{it.key.endsWith(".key")&&it.value==id}.keys.forEach{key->
        val pkg=key.removeSuffix(".key")
        edit.remove(key).remove("$pkg.scheme").remove("$pkg.v1")
    }
    edit.apply()
}
private fun signerConfig(options:ApkSignOptions):ApkSigner.SignerConfig=synchronized(signingKeyLock){
    require(validV1Name(options.v1Name))
    require(options.key==DEFAULT_APK_KEY||options.key.startsWith(IMPORT_PREFIX))
    val store=signingStore()
    if(options.key==DEFAULT_APK_KEY&&!store.containsAlias(DEFAULT_APK_KEY)){
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore").apply{
            initialize(KeyGenParameterSpec.Builder(DEFAULT_APK_KEY,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512,KeyProperties.DIGEST_SHA1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=Slate APK clone"))
                .build())
            generateKeyPair()
        }
    }
    require(store.containsAlias(options.key)){"Signing key no longer available"}
    val key=store.getKey(options.key,null) as PrivateKey
    val chain=checkNotNull(store.getCertificateChain(options.key)).map{it as X509Certificate}
    ApkSigner.SignerConfig.Builder(options.v1Name,key,chain).build()
}
internal fun signApkTo(input:File,output:File,options:ApkSignOptions,progress:(ApkWorkStage)->Unit={}){
    progress(ApkWorkStage.SIGNING)
    require(input.canonicalFile!=output.canonicalFile)
    val scheme=options.scheme
    ApkSigner.Builder(listOf(signerConfig(options))).setInputApk(input).setOutputApk(output)
        .setV1SigningEnabled(scheme.v1).setV2SigningEnabled(scheme.v2).setV3SigningEnabled(scheme.v3).build().sign()
    progress(ApkWorkStage.VERIFYING)
    val verified=ApkVerifier.Builder(output).build().verify()
    // Verification checks the schemes required for the APK SDK range; redundant schemes may not be evaluated.
    require(verified.isVerified){
        "APK signature verification failed; select a scheme supported by the APK"
    }
}
internal fun signSlateApk(root:File,file:File,options:ApkSignOptions,progress:(ApkWorkStage)->Unit={}):File{
    require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
    val parent=checkNotNull(file.parentFile)
    require(safeFile(root,parent)&&parent.canWrite())
    val tmp=Files.createTempFile(parent.toPath(),".slate-sign-",".apk").toFile()
    try{
        signApkTo(file,tmp,options,progress)
        progress(ApkWorkStage.SAVING)
        return publishApkCopy(tmp,parent,file.nameWithoutExtension+"-signed")
    }finally{tmp.delete()}
}
internal fun publishApkCopy(staged:File,parent:File,name:String):File{
    for(i in 0..9999){
        val dest=File(parent,name+(if(i==0)""else "-$i")+".apk")
        try{Files.move(staged.toPath(),dest.toPath());return dest}catch(_:java.nio.file.FileAlreadyExistsException){}
    }
    error("No available APK name")
}
internal fun signEditedApk(context:Context,source:File,staged:File,enabled:Boolean,progress:(ApkWorkStage)->Unit={}):File{
    if(!enabled)return staged
    val options=apkSignOptions(context,apkPackage(context,source))
    val signed=Files.createTempFile(checkNotNull(staged.parentFile).toPath(),".slate-auto-sign-",".apk").toFile()
    try{signApkTo(staged,signed,options,progress);return signed}catch(e:Throwable){signed.delete();throw e}
}
