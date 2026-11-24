// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal data class ProtectedCandidate(val relative:String,val empty:Boolean)
private val pkgPattern=Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
internal fun validProtectedPath(relative:String):Boolean {
    val parts=relative.split('/')
    return parts.size==3 && parts[0]=="Android" && parts[1] in listOf("data","obb") && pkgPattern.matches(parts[2])
}
private fun shell(script:String,root:Boolean):String {
    val process=ProcessBuilder(if(root)listOf("su","-c",script) else listOf("/system/bin/sh","-c",script)).redirectErrorStream(true).start()
    val read=FutureTask<String>{
        process.inputStream.bufferedReader().use{reader->
            val buf=CharArray(4096);val out=StringBuilder()
            while(true){val n=reader.read(buf);if(n<0)break;require(out.length+n<=1_000_000);out.append(buf,0,n)}
            out.toString()
        }
    }
    Thread(read,"slate-protected-read").apply{isDaemon=true;start()}
    try {
        check(process.waitFor(30,TimeUnit.SECONDS))
        val output=read.get(2,TimeUnit.SECONDS)
        check(process.exitValue()==0)
        return output
    } finally {if(process.isAlive)process.destroyForcibly();read.cancel(true)}
}
// Retain folders of packages still known to PackageManager, including retained data.
private fun preamble(user:Int):String {
    require(user in 0..999)
    return """
        base=/storage/emulated/$user/Android
        [ -d "${'$'}base" ] && [ ! -L "${'$'}base" ] || exit 2
        packages=${'$'}(/system/bin/pm list packages -u --user $user) || exit 3
        [ -n "${'$'}packages" ] || exit 3
        printf '%s\n' "${'$'}packages" | /system/bin/grep -q '^package:' || exit 3
    """.trimIndent()
}
internal fun listProtectedCandidates(user:Int,root:Boolean):List<ProtectedCandidate> {
    val script=preamble(user)+"\n"+"""
        accessible=0
        for kind in data obb; do
            parent="${'$'}base/${'$'}kind"
            [ -d "${'$'}parent" ] && [ ! -L "${'$'}parent" ] && [ -r "${'$'}parent" ] && [ -x "${'$'}parent" ] || continue
            accessible=1
            for dir in "${'$'}parent"/*; do
                [ -d "${'$'}dir" ] && [ ! -L "${'$'}dir" ] || continue
                pkg=${'$'}{dir##*/}
                printf '%s\n' "${'$'}packages" | /system/bin/grep -Fxq "package:${'$'}pkg" && continue
                [ -r "${'$'}dir" ] && [ -x "${'$'}dir" ] || continue
                contents=${'$'}(/system/bin/ls -A "${'$'}dir" 2>/dev/null) || continue
                if [ -z "${'$'}contents" ]; then flag=1; else flag=0; fi
                printf '%s\t%s\n' "${'$'}flag" "Android/${'$'}kind/${'$'}pkg"
            done
        done
        [ "${'$'}accessible" = 1 ] || exit 4
    """.trimIndent()
    return parseProtectedRows(shell(script,root).lines())
}
internal fun parseProtectedRows(rows:List<String>):List<ProtectedCandidate> = rows.mapNotNull{line->
    val parts=line.split('\t',limit=2)
    if(parts.size==2 && parts[0] in listOf("0","1") && validProtectedPath(parts[1]))ProtectedCandidate(parts[1],parts[0]=="1") else null
}.distinctBy{it.relative}.take(1000)
internal fun removeProtectedEmpty(user:Int,relative:String,root:Boolean):Boolean {
    require(validProtectedPath(relative))
    val parts=relative.split('/')
    val pkg=parts[2];val kind=parts[1]
    val script=preamble(user)+"\n"+"""
        parent="${'$'}base/$kind"
        dir="${'$'}parent/$pkg"
        [ -d "${'$'}parent" ] && [ ! -L "${'$'}parent" ] || exit 4
        [ -d "${'$'}dir" ] && [ ! -L "${'$'}dir" ] || exit 4
        printf '%s\n' "${'$'}packages" | /system/bin/grep -Fxq 'package:$pkg' && exit 5
        /system/bin/rmdir "${'$'}dir"
    """.trimIndent()
    return runCatching{shell(script,root);true}.getOrDefault(false)
}
