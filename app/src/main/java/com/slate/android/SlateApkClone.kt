// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

internal fun validClonePackage(name:String):Boolean =
    name.length<=255&&name.split('.').size>=2&&name.split('.').all{it.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))}

// ARSCLib releases use either getValueAsString or getDataAsString.
private fun cloneAttributeString(attribute:ResXmlAttribute):String?{
    val method=attribute.javaClass.methods.firstOrNull{it.name=="getValueAsString"&&it.parameterCount==0}
        ?:attribute.javaClass.methods.firstOrNull{it.name=="getDataAsString"&&it.parameterCount==0}
        ?:error("Unsupported binary XML reader")
    return method.invoke(attribute) as? String
}
private fun cloneOwnedName(value:String,old:String,new:String):String =
    if(value==old||value.startsWith("$old."))new+value.removePrefix(old)else value

@Suppress("DEPRECATION")
internal fun cloneSlateApk(context:Context,root:File,file:File,newPackage:String,autoSign:Boolean):File{
    require(validClonePackage(newPackage)){"Invalid package name"}
    require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
    val parent=checkNotNull(file.parentFile)
    require(safeFile(root,parent)&&parent.canWrite())
    val pm=context.packageManager
    val original=checkNotNull(pm.getPackageArchiveInfo(file.path,PackageManager.GET_PROVIDERS))
    val old=original.packageName
    require(old!=newPackage){"Choose a different package name"}
    require(original.sharedUserId.isNullOrEmpty()){"APK uses a shared UID and cannot be cloned safely"}
    val stage=Files.createTempFile(parent.toPath(),".slate-clone-",".apk").toFile()
    var signed:File?=null
    try{
        val module=ApkModule.loadApkFile(file)
        try{
            val manifest=checkNotNull(module.androidManifest)
            val element=checkNotNull(manifest.manifestElement)
            val componentTags=setOf("application","activity","activity-alias","service","receiver","provider","instrumentation")
            val classAttributes=setOf("targetActivity","parentActivityName","backupAgent","appComponentFactory","manageSpaceActivity","zygotePreloadName")
            fun walk(node:ResXmlElement){
                val tag=node.name
                val attrs=node.attributes
                while(attrs.hasNext()){
                    val attr=attrs.next()
                    val name=attr.name
                    val isClass=(name=="name"&&tag in componentTags)||name in classAttributes
                    val isOwned=(name=="name"&&tag in setOf("permission","permission-group","permission-tree","uses-permission","uses-permission-sdk-23","uses-permission-sdk-m"))||name in setOf("permission","readPermission","writePermission","permissionGroup","process","taskAffinity","targetPackage")
                    if(!isClass&&!isOwned&&name!="authorities")continue
                    val value=cloneAttributeString(attr)?:continue
                    val replacement=when{
                        isClass&&value.startsWith(".")->old+value
                        isClass&&'.' !in value&&value.isNotBlank()->"$old.$value"
                        isClass->value
                        name=="authorities"->value.split(';').joinToString(";"){cloneOwnedName(it,old,newPackage)}
                        else->cloneOwnedName(value,old,newPackage)
                    }
                    if(replacement!=value)attr.setValueAsString(replacement)
                }
                val children=node.iterator()
                while(children.hasNext()){val child=children.next();if(child is ResXmlElement)walk(child)}
            }
            walk(element)
            manifest.setPackageName(newPackage)
            manifest.refresh()
            val entries=ZipFile(file).use{zip->zip.entries().asSequence().take(100001).map{it.name}.toList()}
            require(entries.size<=100000)
            entries.filter{it.uppercase(java.util.Locale.ROOT).let{n->n.startsWith("META-INF/")&&(n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")||n.endsWith(".EC")||n=="META-INF/MANIFEST.MF")}}.forEach{module.removeInputSource(it)}
            module.setApkSignatureBlock(null)
            module.writeApk(stage)
        }finally{module.close()}
        val result=checkNotNull(pm.getPackageArchiveInfo(stage.path,0))
        require(result.packageName==newPackage){"Invalid cloned manifest"}
        val output=if(autoSign){
            val target=signEditedApk(context,file,stage,true)
            signed=target
            target
        }else stage
        for(i in 0..9999){
            val dest=File(parent,file.nameWithoutExtension+"-clone"+(if(i==0)""else "-$i")+".apk")
            try{Files.move(output.toPath(),dest.toPath());return dest}catch(_:java.nio.file.FileAlreadyExistsException){}
        }
        error("No available APK name")
    }finally{stage.delete();signed?.delete()}
}
