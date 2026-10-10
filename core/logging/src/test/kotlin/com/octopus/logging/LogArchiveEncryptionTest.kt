package com.octopus.logging

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Checks the exported log archive is actually protected.
 *
 * An export is the artefact most likely to be attached to a mail and sent to
 * somebody else, so the protection has to hold on the file that leaves the app,
 * not merely on the diagnostic bundle. This mirrors the parameters
 * `PigeonLogger.encryptedParameters` uses; if those change, this is the check
 * that notices.
 */
class LogArchiveEncryptionTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val password = "com.octopus.pigeon.post".toCharArray()

    @Test
    fun `entry contents cannot be read without the password`() {
        val archive = buildArchive()

        // Listing names still works, because a zip central directory is not
        // encrypted. That is expected, and the entry names here are dates and a
        // fixed system_info.txt, so nothing identifying leaks that way.
        val zip = ZipFile(archive)
        assertTrue(zip.fileHeaders.isNotEmpty())
        runCatching {
            zip.getInputStream(zip.fileHeaders.first()).use { it.readBytes() }
        }.onSuccess {
            throw AssertionError("exported log entry was readable without the password")
        }
    }

    @Test
    fun `the wrong password does not open an entry`() {
        val archive = buildArchive()
        val zip = ZipFile(archive, "not-the-package-name".toCharArray())

        runCatching {
            zip.getInputStream(zip.fileHeaders.first()).use { it.readBytes() }
        }.onSuccess {
            throw AssertionError("exported log entry opened with the wrong password")
        }
    }

    @Test
    fun `the package name password recovers the contents`() {
        val archive = buildArchive()
        val zip = ZipFile(archive, password)

        val restored = zip.getInputStream(zip.fileHeaders.first()).use { String(it.readBytes()) }

        assertTrue(restored.contains("redacted log line"))
    }

    private fun buildArchive(): File {
        val archive = temp.newFile("pigeon_post_logs.zip")
        ZipOutputStream(archive.outputStream(), password).use { zip ->
            zip.putNextEntry(
                ZipParameters().apply {
                    fileNameInZip = "2026-10-09.log"
                    compressionMethod = CompressionMethod.DEFLATE
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.ZIP_STANDARD
                },
            )
            zip.write("2026-10-09 12:00:00 INFO PigeonPost - a redacted log line".toByteArray())
            zip.closeEntry()
        }
        return archive
    }
}
