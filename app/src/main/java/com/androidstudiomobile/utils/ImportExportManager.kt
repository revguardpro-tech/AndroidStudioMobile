package com.androidstudiomobile.utils
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream
object ImportExportManager {
    fun exportProjectToZip(projectPath: String, destUri: Uri, context: Context): Boolean {
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                val zos = ZipOutputStream(out)
                val base = File(projectPath)
                base.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entry = file.relativeTo(base).path
                    zos.putNextEntry(java.util.zip.ZipEntry(entry))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                zos.close()
            }
            true
        } catch (_: Exception) { false }
    }
    fun importProjectFromZip(zipUri: Uri, destDir: File, context: Context): Boolean {
        return try {
            val tmp = File(context.cacheDir, "import_tmp.zip")
            context.contentResolver.openInputStream(zipUri)?.use { it.copyTo(FileOutputStream(tmp)) }
            ZipFile(tmp).use { zf ->
                zf.entries().asSequence().forEach { entry ->
                    val dest = File(destDir, entry.name)
                    if (entry.isDirectory) dest.mkdirs()
                    else { dest.parentFile?.mkdirs(); zf.getInputStream(entry).use { it.copyTo(FileOutputStream(dest)) } }
                }
            }
            tmp.delete(); true
        } catch (_: Exception) { false }
    }
}