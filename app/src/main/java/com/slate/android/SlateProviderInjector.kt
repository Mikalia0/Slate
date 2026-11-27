// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.pm.PackageManager
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** Adds a read-only external files provider to an explicitly selected APK.
 * The input is never changed. The output must be signed by its owner.
 */
@Suppress("DEPRECATION")
internal fun injectExternalDocumentsProvider(context:Context,root:File,file:File,autoSign:Boolean=true,progress:(ApkWorkStage)->Unit={}):File{
    require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
    val parent=checkNotNull(file.parentFile)
    require(safeFile(root,parent)&&parent.canWrite())
    val info=checkNotNull(context.packageManager.getPackageArchiveInfo(file.path,PackageManager.GET_PROVIDERS))
    val app=checkNotNull(info.applicationInfo)
    // The payload uses platform multidex (Android 5+) and targets Android 8+.
    require(app.minSdkVersion>=26){"Requires an APK with minimum SDK 26 or higher"}
    val authority=info.packageName+".slate.externaldocuments"
    require(info.providers.orEmpty().none{it.authority.orEmpty().split(';').contains(authority)}){"Provider already present"}
    val entries=ZipFile(file).use{zip->zip.entries().asSequence().take(100001).map{it.name}.toList()}
    require(entries.size<=100000)
    val dex=entries.mapNotNull{Regex("""classes([0-9]*)[.]dex""").matchEntire(it)?.groupValues?.get(1)?.let{n->if(n.isEmpty())1 else n.toIntOrNull()}}.maxOrNull()?:0
    require(dex<100)
    val dexName=if(dex==0)"classes.dex"else "classes${dex+1}.dex"
    val payload=context.assets.open("slate-provider/classes.dex").use{it.readBytes()}
    require(payload.size in 100..262144)
    val tmp=Files.createTempFile(parent.toPath(),".slate-provider-",".apk").toFile()
    var signed:File?=null
    try{
        progress(ApkWorkStage.READING)
        val module=ApkModule.loadApkFile(file)
        progress(ApkWorkStage.EDITING)
        try{
            val manifest=checkNotNull(module.androidManifest)
            val application=checkNotNull(manifest.applicationElement)
            val provider=application.newElement("provider")
            provider.getOrCreateAndroidAttribute("name",0x01010003).setValueAsString("com.slate.payload.ExternalDocumentsProvider")
            provider.getOrCreateAndroidAttribute("authorities",0x01010018).setValueAsString(authority)
            provider.getOrCreateAndroidAttribute("permission",0x01010006).setValueAsString("android.permission.MANAGE_DOCUMENTS")
            provider.getOrCreateAndroidAttribute("exported",0x01010010).setValueAsBoolean(true)
            provider.getOrCreateAndroidAttribute("grantUriPermissions",0x0101001b).setValueAsBoolean(true)
            provider.newElement("intent-filter").newElement("action").getOrCreateAndroidAttribute("name",0x01010003).setValueAsString("android.content.action.DOCUMENTS_PROVIDER")
            manifest.refresh()
            module.add(ByteInputSource(payload,dexName))
            entries.filter{it.uppercase().let{n->n.startsWith("META-INF/")&&(n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")||n.endsWith(".EC")||n=="META-INF/MANIFEST.MF")}}.forEach{module.removeInputSource(it)}
            module.setApkSignatureBlock(null)
            progress(ApkWorkStage.REBUILDING)
            module.writeApk(tmp)
        }finally{module.close()}
        val updated=checkNotNull(context.packageManager.getPackageArchiveInfo(tmp.path,PackageManager.GET_PROVIDERS))
        require(updated.packageName==info.packageName&&updated.providers.orEmpty().any{it.authority==authority&&it.permissionIsDocumentsProvider()})
        ZipFile(tmp).use{require(it.getEntry(dexName)!=null)}
        val output=signEditedApk(context,file,tmp,autoSign,progress)
        if(output!=tmp)signed=output
        progress(ApkWorkStage.SAVING)
        return publishApkCopy(output,parent,file.nameWithoutExtension+"-documents")
    }finally{tmp.delete();signed?.delete()}
}
private fun android.content.pm.ProviderInfo.permissionIsDocumentsProvider()=readPermission=="android.permission.MANAGE_DOCUMENTS"&&exported&&grantUriPermissions
