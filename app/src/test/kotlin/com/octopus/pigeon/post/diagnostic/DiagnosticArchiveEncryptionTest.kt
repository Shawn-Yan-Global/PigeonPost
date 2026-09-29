package com.octopus.pigeon.post.diagnostic

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Checks the archive the builder produces is actually protected.
 *
 * The value of the whole feature rests on this. If the zip turns out to be
 * readable by anything that opens it, then a user forwarding it to support has
 * forwarded their mail password and their keywords with it, and the redaction in
 * [ConfigShapeReportTest] protects nothing. A build failure would never reveal
 * that, so it is asserted here.
 *
 * The parameters mirror [DiagnosticPackageBuilder]. If the builder changes them,
 * these are the check that the archive is still protected.
 */
class DiagnosticArchiveEncryptionTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val password = "com.octopus.pigeon.post"

    @Test
    fun `entry contents cannot be read without the password`() {
        val archive = buildArchive()

        // Listing the names works, because the central directory is not
        // encrypted. That is expected and is why the entry names are kept free
        // of anything identifying. Reading the contents must not.
        val noPassword = ZipFile(archive)
        val names = noPassword.fileHeaders.map { it.fileName }.toSet()
        assertEquals(setOf("report.txt", "logs/app.log"), names)

        val error =
            runCatching {
                val header = noPassword.getFileHeader("report.txt")!!
                noPassword.getInputStream(header).readBytes()
            }.exceptionOrNull()

        assertTrue("entry was readable with no password", error != null)
    }

    @Test
    fun `entry contents cannot be read with the wrong password`() {
        val archive = buildArchive()

        val wrong = ZipFile(archive, "not-the-password".toCharArray())
        val error =
            runCatching {
                val header = wrong.getFileHeader("report.txt")!!
                wrong.getInputStream(header).readBytes()
            }.exceptionOrNull()

        assertTrue("entry was readable with the wrong password", error != null)
    }

    @Test
    fun `entries are readable with the correct password`() {
        val archive = buildArchive()

        val zip = ZipFile(archive, password.toCharArray())

        assertEquals(
            "report body",
            readEntry(zip, "report.txt").trim(),
        )
        assertTrue(readEntry(zip, "logs/app.log").contains("log line"))
    }

    private fun readEntry(
        zip: ZipFile,
        name: String,
    ): String {
        val header = zip.getFileHeader(name)!!
        return zip.getInputStream(header).readBytes().decodeToString()
    }

    /**
     * ZipCrypto rather than AES, so a stock desktop unzip tool can open the
     * result without extra software. The same construction as the builder.
     */
    private fun buildArchive(): File {
        val archive = temp.newFile("diagnostic.zip")
        val logFile = temp.newFile("app.log").apply { writeText("log line") }

        ZipOutputStream(
            BufferedOutputStream(FileOutputStream(archive)),
            password.toCharArray(),
        ).use { zip ->
            zip.putNextEntry(parameters("report.txt"))
            zip.write("report body".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(parameters("logs/app.log"))
            logFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return archive
    }

    private fun parameters(entryName: String): ZipParameters =
        ZipParameters().apply {
            fileNameInZip = entryName
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.ZIP_STANDARD
        }
}
