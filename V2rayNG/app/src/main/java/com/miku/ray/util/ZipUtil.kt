package com.miku.ray.util

import com.miku.ray.AppConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtil {
    private const val BUFFER_SIZE = 4096

    @Throws(IOException::class)
    fun zipFromFolder(folderPath: String, outputZipFilePath: String): Boolean {
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            if (folderPath.isEmpty() || outputZipFilePath.isEmpty()) {
                return false
            }

            val directory = File(folderPath)
            if (!directory.isDirectory) return false

            val filesToCompress = ArrayList<Pair<String, File>>()
            fun collectFiles(dir: File, prefix: String) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        filesToCompress.add(Pair(if (prefix.isEmpty()) f.name else "$prefix/${f.name}", f))
                    } else if (f.isDirectory) {
                        collectFiles(f, if (prefix.isEmpty()) f.name else "$prefix/${f.name}")
                    }
                }
            }
            collectFiles(directory, "")

            if (filesToCompress.isEmpty()) {
                return false
            }

            val zos = ZipOutputStream(FileOutputStream(outputZipFilePath))

            filesToCompress.forEach { (entryName, file) ->
                val ze = ZipEntry(entryName)
                zos.putNextEntry(ze)
                val inputStream = FileInputStream(file)
                while (true) {
                    val len = inputStream.read(buffer)
                    if (len <= 0) break
                    zos.write(buffer, 0, len)
                }
                inputStream.close()
            }

            zos.closeEntry()
            zos.close()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to zip folder", e)
            return false
        }
        return true
    }

    @Throws(IOException::class)
    fun unzipToFolder(zipFile: File, destDirectory: String): Boolean {
        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }
        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        val filePath = destDirectory + File.separator + entry.name
                        if (!entry.isDirectory) {
                            extractFile(input, filePath)
                        } else {
                            val dir = File(filePath)
                            dir.mkdir()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unzip file", e)
            return false
        }
        return true
    }

    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: String) {
        File(destFilePath).parentFile?.mkdirs()
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        bos.close()
    }
}