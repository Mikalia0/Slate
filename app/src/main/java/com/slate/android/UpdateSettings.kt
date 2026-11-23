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
import androidx.compose.foundation.layout.*
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

private const val RELEASES = "https://api.github.com/repos/Mikalia0/Slate/releases?per_page=30"
private const val MAX_APK_BYTES = 200L * 1024 * 1024
private data class Update(val code: Long, val attempt: Long, val tag: String, val url: String, val size: Long, val digest: String?)

internal fun updateVersion(tag: String): Pair<Long,Long>? {
    val m = Regex("""dev-([1-9][0-9]*)-([1-9][0-9]*)""").matchEntire(tag) ?: return null
    val run = m.groupValues[1].toLongOrNull() ?: return null
    val attempt = m.groupValues[2].toLongOrNull() ?: return null
    if (run > 2_100_000_000L - 1000) return null
    return (1000L + run) to attempt
}

private fun trusted(url: URL): Boolean =
    url.protocol == "https" && (url.port == -1 || url.port == 443) && url.userInfo == null &&
    url.host.lowercase(java.util.Locale.ROOT) in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")

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
                check(status == 200) { if(status==403 || status==429) "GitHub rate limit or access restriction" else "GitHub HTTP $status" }
                return conn
            }
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
    }
    error("Too many redirects")
}

private suspend fun findUpdate(installed: Long): Update? = withContext(Dispatchers.IO) {
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
        val (code,attempt) = updateVersion(tag) ?: continue
        if(code <= installed) continue
        val assets = release.optJSONArray("assets") ?: continue
        for(j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j)
            if(asset.optString("name") != "Slate.apk" || asset.optString("state") != "uploaded") continue
            val size = asset.optLong("size")
            val url = asset.optString("browser_download_url")
            if(url != "https://github.com/Mikalia0/Slate/releases/download/$tag/Slate.apk" || size !in 1..MAX_APK_BYTES) continue
            val digest = asset.optString("digest").takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(":")
            releases += Update(code,attempt,tag,url,size,digest)
        }
    }
    releases.maxWithOrNull(compareBy<Update>{it.code}.thenBy{it.attempt})
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
    val part = File.createTempFile("slate-", ".part", dir)
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
internal fun UpdateSettings() {
    val context = LocalContext.current
    val installed = remember { context.packageManager.getPackageInfo(context.packageName,0) }
    val version = packageVersion(installed)
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<Update?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var received by remember { mutableLongStateOf(0L) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    Text("Updates",style=MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Slate ${installed.versionName.orEmpty()}",style=MaterialTheme.typography.titleMedium)
            Text("Build $version · GitHub releases",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            update?.let { candidate ->
                Text("Update available · build ${candidate.code}",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                Text(Formatter.formatFileSize(context,candidate.size),style=MaterialTheme.typography.bodySmall)
            }
            if(busy) {
                if(downloading && update!=null) {
                    LinearProgressIndicator(progress={ (received.toDouble()/update!!.size).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                    Text(Formatter.formatFileSize(context,received)+" / "+Formatter.formatFileSize(context,update!!.size),style=MaterialTheme.typography.bodySmall)
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if(!busy) {
                if(apk!=null && update!=null) {
                    Button(onClick={
                        try {
                            if(!context.packageManager.canRequestPackageInstalls()) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)))
                                message="Allow Slate to install apps, then return and tap Install."
                            } else {
                                openInstaller(context,apk!!)
                                message="Confirm the update in Android's installer."
                            }
                        } catch(e:Exception) { message=e.message ?: "Unable to open installer" }
                    },modifier=Modifier.fillMaxWidth()) { Text("Install update") }
                } else if(update!=null) {
                    Button(onClick={
                        val candidate=update!!
                        busy=true; downloading=true; received=0L; message=null
                        job=scope.launch {
                            try {
                                apk=download(context,candidate){ bytes -> withContext(Dispatchers.Main.immediate){received=bytes} }
                                message="Download verified. Ready to install."
                            } catch(e:CancellationException){throw e}
                            catch(e:Exception){message=e.message ?: "Download failed"}
                            finally{busy=false;downloading=false}
                        }
                    },modifier=Modifier.fillMaxWidth()){Text("Download update")}
                }
                OutlinedButton(onClick={
                    busy=true;downloading=false;message=null;update=null;apk=null
                    job=scope.launch {
                        try {
                            update=findUpdate(version)
                            if(update==null) message="You're up to date."
                        } catch(e:CancellationException){throw e}
                        catch(e:Exception){message=e.message ?: "Unable to check for updates"}
                        finally{busy=false}
                    }
                },modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Check for updates")
                }
            } else OutlinedButton(onClick={job?.cancel();message="Cancelled"}){Text("Cancel")}
        }
    }
}
