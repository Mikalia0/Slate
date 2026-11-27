// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val RELEASES = "https://api.github.com/repos/stevenmx2/Slate/releases?per_page=30"
private const val UPDATE_MANIFEST = "https://raw.githubusercontent.com/stevenmx2/Slate/updates/update.json"
private const val MANIFEST_CACHE_MS = 6L * 60 * 60 * 1000
private const val MANUAL_CACHE_MS = 60L * 1000
private val updateCheckMutex = Mutex()
private const val MAX_APK_BYTES = 200L * 1024 * 1024
private data class Update(val code: Long, val attempt: Long, val tag: String, val url: String, val size: Long, val digest: String?, val notes: String)

internal fun updateVersion(tag: String, notes: String = ""): Pair<Long,Long>? {
    if (Regex("""v[0-9]+\.[0-9]+\.[0-9]+""").matches(tag)) {
        val code = Regex("""(?m)^Version code: ([1-9][0-9]*)\s*$""").findAll(notes).toList().singleOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return if (code in 1001L..2_100_000_000L) code to 1L else null
    }
    val m = Regex("""dev-([1-9][0-9]*)-([1-9][0-9]*)""").matchEntire(tag) ?: return null
    val run = m.groupValues[1].toLongOrNull() ?: return null
    val attempt = m.groupValues[2].toLongOrNull() ?: return null
    if (run > 2_100_000_000L - 1000) return null
    return (1000L + run) to attempt
}

private fun trusted(url: URL): Boolean =
    url.protocol == "https" && (url.port == -1 || url.port == 443) && url.userInfo == null &&
    url.host.lowercase(java.util.Locale.ROOT) in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","raw.githubusercontent.com")

private class UpdateHttpException(val status: Int, val retryAt: Long): java.io.IOException(
    if(status==403 || status==429) "GitHub rate limit or access restriction" else "GitHub HTTP $status"
)

private fun connect(url: String, accept: String): HttpURLConnection {
    var next = URL(url)
    repeat(6) {
        require(trusted(next)) { "Unexpected download host" }
        val conn = next.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept",accept)
        conn.setRequestProperty("User-Agent","Slate-Android")
        try {
            val status = conn.responseCode
            if (status in listOf(301,302,303,307,308)) {
                val location = conn.getHeaderField("Location") ?: error("Missing redirect")
                next = URL(next,location)
                conn.disconnect()
            } else {
                if(status != 200) {
                    val now=System.currentTimeMillis()
                    val retry=conn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(0L,86400L)?.let{now+it*1000}
                    val reset=conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()?.takeIf{it>now/1000&&it<now/1000+86400}?.times(1000)
                    throw UpdateHttpException(status,maxOf(retry?:0L,reset?:0L,now+MANUAL_CACHE_MS))
                }
                return conn
            }
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
    }
    error("Too many redirects")
}

private suspend fun findLegacyUpdate(installed: Long): Update? = withContext(Dispatchers.IO) {
    val conn = connect(RELEASES,"application/vnd.github+json")
    val data = try {
        conn.inputStream.buffered().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if(n == -1) break
                check(out.size()+n <= 2*1024*1024) { "Unexpected release response size" }
                out.write(buf,0,n)
            }
            JSONArray(out.toString("UTF-8"))
        }
    } finally { conn.disconnect() }
    val releases = mutableListOf<Update>()
    for(i in 0 until data.length()) {
        val release = data.getJSONObject(i)
        if(release.optBoolean("draft")) continue
        val tag = release.optString("tag_name")
        val (code,attempt) = updateVersion(tag, release.optString("body")) ?: continue
        if(code <= installed) continue
        val assets = release.optJSONArray("assets") ?: continue
        for(j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j)
            if(asset.optString("name") != "Slate.apk" || asset.optString("state") != "uploaded") continue
            val size = asset.optLong("size")
            val url = asset.optString("browser_download_url")
            if(url != "https://github.com/stevenmx2/Slate/releases/download/$tag/Slate.apk" || size !in 1..MAX_APK_BYTES) continue
            val digest = asset.optString("digest").takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(":")
            releases += Update(code,attempt,tag,url,size,digest,release.optString("body").take(12000))
        }
    }
    val latest=releases.maxWithOrNull(compareBy<Update>{it.code}.thenBy{it.attempt}) ?: return@withContext null
    val notes=try{
        val notesConn=connect("https://github.com/stevenmx2/Slate/releases/download/${latest.tag}/changelog.txt","application/octet-stream")
        try{
            notesConn.inputStream.buffered().use{input->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(4096)
                while(true){
                    currentCoroutineContext().ensureActive()
                    val n=input.read(buffer)
                    if(n<0)break
                    check(output.size()+n<=65536)
                    output.write(buffer,0,n)
                }
                output.toString("UTF-8").trim()
            }
        }finally{notesConn.disconnect()}
    }catch(e:CancellationException){throw e}
    catch(_:Exception){latest.notes}
    latest.copy(notes=notes)
}


internal fun manifestVersion(tag:String,code:Long,attempt:Long,notes:String):Boolean =
    updateVersion(tag,notes)?.let{it.first==code&&it.second==attempt}==true

private fun parseUpdateManifest(raw:String):Update {
    val json=JSONObject(raw)
    require(json.getInt("schema")==1){"Unsupported update manifest"}
    val tag=json.getString("tag")
    val code=json.getLong("versionCode")
    val attempt=json.getLong("attempt")
    val notes=json.getString("notes")
    val size=json.getLong("size")
    val url=json.getString("url")
    val digest=json.getString("sha256")
    require(manifestVersion(tag,code,attempt,json.getString("versionNotes"))){"Invalid update version"}
    require(url=="https://github.com/stevenmx2/Slate/releases/download/$tag/Slate.apk")
    require(size in 1..MAX_APK_BYTES&&digest.matches(Regex("[0-9a-fA-F]{64}"))){"Invalid update metadata"}
    return Update(code,attempt,tag,url,size,digest,notes.take(12000))
}

private suspend fun fetchUpdateManifest():String {
    val conn=connect(UPDATE_MANIFEST,"application/json")
    try {
        return conn.inputStream.buffered().use{input->
            val out=java.io.ByteArrayOutputStream()
            val buf=ByteArray(8192)
            while(true){
                currentCoroutineContext().ensureActive()
                val n=input.read(buf)
                if(n<0)break
                require(out.size()+n<=65536){"Unexpected update manifest size"}
                out.write(buf,0,n)
            }
            out.toString("UTF-8")
        }
    }finally{conn.disconnect()}
}

private suspend fun findUpdate(context:Context,installed:Long,manual:Boolean):Update? = withContext(Dispatchers.IO){
    updateCheckMutex.withLock {
        val prefs=context.getSharedPreferences("slate_update_metadata",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        val cached=runCatching{parseUpdateManifest(prefs.getString("manifest",null).orEmpty())}.getOrNull()
        val age=now-prefs.getLong("manifest_time",0L)
        val ttl=if(manual)MANUAL_CACHE_MS else MANIFEST_CACHE_MS
        if(cached!=null&&age in 0 until ttl)return@withLock cached.takeIf{it.code>installed}
        try{
            val raw=fetchUpdateManifest()
            val latest=parseUpdateManifest(raw)
            // Ignore stale CDN responses instead of rolling the known version backwards.
            val chosen=if(cached!=null&&(cached.code>latest.code||(cached.code==latest.code&&cached.attempt>latest.attempt)))cached else latest
            if(chosen===latest)prefs.edit().putString("manifest",raw).putLong("manifest_time",now).remove("legacy_retry").apply()
            else prefs.edit().putLong("manifest_time",now).apply()
            chosen.takeIf{it.code>installed}
        }catch(e:UpdateHttpException){
            if(e.status!=404||cached!=null)throw e
            // Bootstrap only: installations before the first manifest was published.
            // Do not fall back on raw access restrictions or malformed manifests.
            val retry=prefs.getLong("legacy_retry",0L)
            if(retry>now&&retry-now<=86400000L)throw java.io.IOException("GitHub update check temporarily limited. Try again later.")
            try {
                val result=findLegacyUpdate(installed)
                prefs.edit().putLong("legacy_retry",now+MANUAL_CACHE_MS).apply()
                result
            }catch(legacy:UpdateHttpException){
                if(legacy.status==403||legacy.status==429)prefs.edit().putLong("legacy_retry",legacy.retryAt).apply()
                throw legacy
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun packageVersion(info: PackageInfo): Long = if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()

@Suppress("DEPRECATION")
private fun signingFlags(): Int = if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

@Suppress("DEPRECATION")
private fun signerDigests(info: PackageInfo): Set<String> {
    val signatures = if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
    return signatures.orEmpty().map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }.toSet()
}

private fun verifyArchive(context: Context, file: File, expected: Long) {
    val pm = context.packageManager
    val installed = pm.getPackageInfo(context.packageName,signingFlags())
    val archive = pm.getPackageArchiveInfo(file.absolutePath,signingFlags()) ?: error("Invalid APK")
    check(archive.packageName == context.packageName) { "APK package does not match Slate" }
    check(packageVersion(archive)==expected && expected>packageVersion(installed)) { "APK version does not match the update" }
    val current = signerDigests(installed)
    check(current.isNotEmpty() && current==signerDigests(archive)) { "APK signing certificate differs from the installed app" }
}

private suspend fun download(context: Context, update: Update, progress: suspend (Long)->Unit): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir,"updates").apply { mkdirs() }
    val part = File.createTempFile("slate-part-", ".apk", dir)
    val result = File(dir,"Slate-${update.code}.apk")
    try {
        val conn = connect(update.url,"application/octet-stream")
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0L
        var last = 0L
        try {
            conn.inputStream.buffered().use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64*1024)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if(n == -1) break
                        count += n
                        check(count <= update.size && count <= MAX_APK_BYTES) { "Unexpected APK size" }
                        output.write(buffer,0,n)
                        hash.update(buffer,0,n)
                        val now = System.nanoTime()
                        if(now-last >= 150_000_000L) { last=now; progress(count) }
                    }
                }
            }
        } finally { conn.disconnect() }
        check(count == update.size) { "Incomplete download" }
        val digest = hash.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
        check(update.digest==null || digest.equals(update.digest,ignoreCase=true)) { "APK checksum mismatch" }
        verifyArchive(context,part,update.code)
        check(part.renameTo(result)) { "Could not save update" }
        // Keep only the verified APK for the current update.
        dir.listFiles()?.filter{it!=result && it.name.endsWith(".apk")}?.forEach{it.delete()}
        result
    } finally { part.delete() }
}

private fun openInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context,context.packageName+".updates",apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri,"application/vnd.android.package-archive")
        clipData = ClipData.newRawUri("Slate update",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

@Composable
internal fun UpdateSettings(onCheck: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { context.packageManager.getPackageInfo(context.packageName,0) }
    Text(tr("Updates"),style=MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Slate ${installed.versionName.orEmpty()}",style=MaterialTheme.typography.titleMedium)
            Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.update_build,packageVersion(installed).toString()),style=MaterialTheme.typography.bodySmall)
            Text(tr("Checks for updates when Slate opens."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=onCheck,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Check for updates"))
            }
        }
    }
}

@Composable
internal fun UpdatePrompt(manualRequest: Int) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val installed = remember { packageVersion(context.packageManager.getPackageInfo(context.packageName,0)) }
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<Update?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var visible by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var received by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun installVerified(file: File) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            message = "Download verified. Tap Install to continue."
            return
        }
        try {
            openInstaller(context,file)
            message = "Confirm the update in Android's installer."
        } catch(e:Exception) {
            message = e.message ?: "Unable to open installer"
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(context.packageManager.canRequestPackageInstalls()) {
            // The settings activity may deliver its result before ON_RESUME.
            val file=apk
            if(file!=null) scope.launch {
                try {
                    lifecycle.withResumed { openInstaller(context,file) }
                    message="Confirm the update in Android's installer."
                } catch(e:CancellationException){throw e}
                catch(e:Exception){message=e.message ?: "Unable to open installer"}
            }
        } else message="Allow app installation to continue."
    }
    fun install(file: File) {
        try {
            if(!context.packageManager.canRequestPackageInstalls()) {
                message="Allow Slate to install apps, then return to continue."
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)))
            } else installVerified(file)
        } catch(e:Exception) { message=e.message ?: "Unable to open installer" }
    }
    fun check(manual: Boolean) {
        if(checking || downloading) { if(manual) visible=true; return }
        if(manual && candidate!=null) { visible=true; return }
        checking=true
        message=null
        if(manual)visible=true
        job=scope.launch {
            try {
                candidate=findUpdate(context,installed,manual)
                apk=null
                if(candidate!=null) visible=true
                else if(manual) message="You're up to date."
            } catch(e:CancellationException){throw e}
            catch(e:Exception){ if(manual || visible) message=e.message ?: "Unable to check for updates" }
            finally{checking=false}
        }
    }
    // One quiet startup check per Activity composition; explicit checks remain available.
    LaunchedEffect(manualRequest) { check(manualRequest>0) }

    if(visible) AlertDialog(
        onDismissRequest={if(!downloading && !checking) visible=false},
        title={Text(tr(if(candidate!=null) "Update available" else "Slate updates"))},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                candidate?.let {
                    Text(tr("A newer version of Slate is available."))
                    Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.update_build_size,it.code.toString(),Formatter.formatFileSize(context,it.size)),style=MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.update_whats_new),style=MaterialTheme.typography.titleMedium)
                    if(it.notes.isNotBlank()){
                        Column(Modifier.fillMaxWidth().heightIn(max=240.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            it.notes.lineSequence().map{line->line.trim()}.filter{line->line.isNotBlank()}.forEach{line->
                                if(line.startsWith("#"))Text(line.trimStart('#').trim(),style=MaterialTheme.typography.titleSmall)
                                else Text(if(line.startsWith("- "))"• "+line.drop(2) else line,style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }else Text(stringResource(R.string.update_notes_missing),style=MaterialTheme.typography.bodySmall)
                }
                if(checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(tr("Checking for updates…"))
                }
                if(downloading) {
                    val total=candidate?.size ?: 1L
                    LinearProgressIndicator(progress={(received.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    Text(Formatter.formatFileSize(context,received)+" / "+Formatter.formatFileSize(context,total))
                }
                message?.let { Text(tr(it),style=MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton={
            if(!checking && !downloading) {
                val file=apk
                val update=candidate
                if(file!=null) TextButton(onClick={install(file)}){Text(tr("Install"))}
                else if(update!=null) TextButton(onClick={
                    received=0L; downloading=true; message=null
                    job=scope.launch {
                        try {
                            val result=download(context,update){bytes->withContext(Dispatchers.Main.immediate){received=bytes}}
                            apk=result
                            message="Download verified."
                            install(result)
                        } catch(e:CancellationException){throw e}
                        catch(e:Exception){message=e.message ?: "Download failed"}
                        finally{downloading=false}
                    }
                }){Text(tr("Download & install"))}
                else TextButton(onClick={visible=false}){Text("OK")}
            }
        },
        dismissButton={
            TextButton(onClick={
                if(downloading || checking){job?.cancel();message="Cancelled"}
                else visible=false
            }){Text(tr(if(downloading || checking)"Cancel" else if(candidate!=null)"Later" else "Close"))}
        }
    )
}
