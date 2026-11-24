// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

// Never follow symbolic links, delete volume roots, or enter restricted app directories.
internal fun deleteSelected(root: File, entries: List<Entry>): Pair<Int,Int> {
    val base = root.toPath().toAbsolutePath().normalize()
    var deleted=0
    var failed=0
    for(entry in entries) {
        try {
            val target=entry.file.toPath().toAbsolutePath().normalize()
            require(target!=base && target.startsWith(base) && entry.skipped==0L)
            val relative=base.relativize(target)
            require(relative.toString()!="Android")
            require(!relative.startsWith("Android/data") && !relative.startsWith("Android/obb"))
            var ancestor=base
            for(part in relative) {
                ancestor=ancestor.resolve(part)
                require(!Files.isSymbolicLink(ancestor))
            }
            require(Files.isDirectory(target,LinkOption.NOFOLLOW_LINKS)==entry.directory)
            if(entry.directory) {
                Files.walkFileTree(target,object:SimpleFileVisitor<Path>() {
                    override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {
                        Files.delete(file)
                        return FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir:Path,exc:IOException?):FileVisitResult {
                        if(exc!=null)throw exc
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
            } else Files.delete(target)
            deleted++
        } catch(_:IOException){failed++}
        catch(_:SecurityException){failed++}
        catch(_:IllegalArgumentException){failed++}
    }
    return deleted to failed
}
