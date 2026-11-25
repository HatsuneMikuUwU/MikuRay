package com.miku.ray.ui.userasset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UserAssetViewModelTest {
    private val viewModel = UserAssetViewModel()

    @Test
    fun replaceAssetFileMovesTempFileIntoPlace() {
        val dir = Files.createTempDirectory("mikuray-asset-").toFile()
        try {
            val temp = File(dir, "asset_temp").apply { writeText("new") }
            val target = File(dir, "asset")

            assertTrue(viewModel.replaceAssetFile(temp, target))
            assertEquals("new", target.readText())
            assertFalse(temp.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun replaceAssetFileOverwritesExistingTarget() {
        val dir = Files.createTempDirectory("mikuray-asset-").toFile()
        try {
            val temp = File(dir, "asset_temp").apply { writeText("updated") }
            val target = File(dir, "asset").apply { writeText("old") }

            assertTrue(viewModel.replaceAssetFile(temp, target))
            assertEquals("updated", target.readText())
            assertFalse(temp.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun replaceAssetFileReportsFailureAndRemovesTempFile() {
        val dir = Files.createTempDirectory("mikuray-asset-").toFile()
        try {
            val temp = File(dir, "asset_temp").apply { writeText("new") }
            val target = File(File(dir, "missing"), "asset")

            assertFalse(viewModel.replaceAssetFile(temp, target))
            assertFalse(temp.exists())
            assertFalse(target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
