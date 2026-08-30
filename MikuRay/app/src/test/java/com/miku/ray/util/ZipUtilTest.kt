package com.miku.ray.util

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipUtilTest {
    private val temporaryRoot = Files.createTempDirectory("mikuray-zip-test").toFile()

    @After
    fun tearDown() {
        temporaryRoot.deleteRecursively()
    }

    @Test
    fun unzipRejectsPathTraversal() {
        val archive = createArchive("../outside" to "unsafe".toByteArray())
        val destination = File(temporaryRoot, "destination")

        assertFalse(ZipUtil.unzipToFolder(archive, destination.absolutePath))
        assertFalse(File(temporaryRoot, "outside").exists())
    }

    @Test
    fun extractArchiveRejectsDuplicateCanonicalDestination() {
        val archive = createArchive(
            "config" to byteArrayOf(1),
            "nested/../config" to byteArrayOf(2),
        )

        assertThrowsZipException(archive)
    }

    @Test
    fun extractArchiveRejectsEntryBelowFile() {
        val archive = createArchive(
            "parent" to byteArrayOf(1),
            "parent/child" to byteArrayOf(2),
        )

        assertThrowsZipException(archive)
    }

    @Test
    fun extractArchiveRejectsOversizedEntry() {
        val archive = createArchive("config" to ByteArray(5))
        val destination = File(temporaryRoot, "destination")

        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(
                archive,
                destination,
                ZipUtil.ExtractionLimits(
                    maxArchiveBytes = archive.length(),
                    maxEntries = 4,
                    maxEntryBytes = 4,
                    maxTotalBytes = 16,
                    maxCompressionRatio = 1000.0,
                ),
            )
        }
        assertTrue(!destination.exists())
    }

    private fun assertThrowsZipException(archive: File) {
        val destination = File(temporaryRoot, "destination")
        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(archive, destination)
        }
        assertFalse(destination.exists())
    }

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val archive = File(temporaryRoot, "archive-${System.nanoTime()}.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (name, contents) ->
                output.putNextEntry(ZipEntry(name))
                output.write(contents)
                output.closeEntry()
            }
        }
        return archive
    }

    private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (type.isInstance(error)) return
            throw AssertionError("Expected ${type.simpleName}, got ${error::class.java.simpleName}", error)
        }
        throw AssertionError("Expected ${type.simpleName}")
    }
}
