package com.parktimedetector

import com.parktimedetector.data.LogEntry
import com.parktimedetector.data.LogExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

class LogExporterTest {

    @Test
    fun testFormatLogsAsText_containsExpectedHeaderAndEntries() {
        val sampleLogs = listOf(
            LogEntry(
                id = 1,
                timestampMillis = 1714000000000L,
                level = "INFO",
                tag = "SCREEN_DETECTED",
                packageName = "com.cpa.accountManagement",
                message = "Detected parking timer screen with 45m left",
                rawData = "Node: Parking details\nNode: Time left: 45 min"
            ),
            LogEntry(
                id = 2,
                timestampMillis = 1714000005000L,
                level = "ERROR",
                tag = "NOTIF_PARSE_ERROR",
                packageName = "com.preciseparklink.parkedin",
                message = "Failed to parse notification payload",
                rawData = null
            )
        )

        val status = LogExporter.DiagnosticStatus(
            hasNotifPermission = true,
            isNotifConnected = true,
            hasAccessibilityPermission = true,
            isAccessibilityConnected = false,
            logAllNotifications = true
        )

        val text = LogExporter.formatLogsAsText(sampleLogs, status, 1714000010000L)

        assertTrue(text.contains("PARKING TIME DETECTOR - SYSTEM LOGS"))
        assertTrue(text.contains("Notification Listener Perm  : GRANTED"))
        assertTrue(text.contains("Notification Service Active : CONNECTED"))
        assertTrue(text.contains("Accessibility Perm          : GRANTED"))
        assertTrue(text.contains("Accessibility Service Active: DISCONNECTED"))
        assertTrue(text.contains("Log All Notifications       : ENABLED"))
        assertTrue(text.contains("Total Log Entries           : 2"))
        assertTrue(text.contains("Detected parking timer screen with 45m left"))
        assertTrue(text.contains("Failed to parse notification payload"))
        assertTrue(text.contains("Node: Parking details"))
    }

    @Test
    fun testFormatLogsAsJson_validJsonStructure() {
        val sampleLogs = listOf(
            LogEntry(
                id = 10,
                timestampMillis = 1714000000000L,
                level = "SUCCESS",
                tag = "SESSION_APPROVED",
                packageName = "com.preciseparklink.parkedin",
                message = "Approved parking session",
                rawData = "Raw Test Data"
            )
        )

        val status = LogExporter.DiagnosticStatus(
            hasNotifPermission = true,
            isNotifConnected = true,
            hasAccessibilityPermission = false,
            isAccessibilityConnected = false,
            logAllNotifications = false
        )

        val jsonString = LogExporter.formatLogsAsJson(sampleLogs, status, 1714000000000L)

        assertTrue(jsonString.contains("\"metadata\": {"))
        assertTrue(jsonString.contains("\"exportTimeMillis\": 1714000000000"))
        assertTrue(jsonString.contains("\"notificationPermissionGranted\": true"))
        assertTrue(jsonString.contains("\"accessibilityPermissionGranted\": false"))
        assertTrue(jsonString.contains("\"totalLogs\": 1"))
        assertTrue(jsonString.contains("\"logs\": ["))
        assertTrue(jsonString.contains("\"id\": 10"))
        assertTrue(jsonString.contains("\"level\": \"SUCCESS\""))
        assertTrue(jsonString.contains("\"tag\": \"SESSION_APPROVED\""))
        assertTrue(jsonString.contains("\"packageName\": \"com.preciseparklink.parkedin\""))
        assertTrue(jsonString.contains("\"message\": \"Approved parking session\""))
        assertTrue(jsonString.contains("\"rawData\": \"Raw Test Data\""))
    }

    @Test
    fun testZipArchiveCreationAndExtraction() {
        val sampleLogs = listOf(
            LogEntry(
                id = 1,
                timestampMillis = 1714000000000L,
                level = "INFO",
                tag = "TEST_TAG",
                packageName = "com.test.pkg",
                message = "Test message for zip",
                rawData = "Raw details in zip"
            )
        )

        val status = LogExporter.DiagnosticStatus(
            hasNotifPermission = true,
            isNotifConnected = true,
            hasAccessibilityPermission = true,
            isAccessibilityConnected = true,
            logAllNotifications = true
        )

        val textContent = LogExporter.formatLogsAsText(sampleLogs, status)
        val jsonContent = LogExporter.formatLogsAsJson(sampleLogs, status)

        val tempDir = Files.createTempDirectory("log_test").toFile()
        try {
            val zipFile = File(tempDir, "test_logs.zip")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
                zipOut.putNextEntry(java.util.zip.ZipEntry("detection_logs.txt"))
                zipOut.write(textContent.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                zipOut.putNextEntry(java.util.zip.ZipEntry("detection_logs.json"))
                zipOut.write(jsonContent.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            assertTrue(zipFile.exists())
            assertTrue(zipFile.length() > 0)

            // Verify reading back from zip
            val entriesFound = mutableMapOf<String, String>()
            ZipInputStream(zipFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val bytes = zipIn.readBytes()
                    entriesFound[entry.name] = String(bytes, Charsets.UTF_8)
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            assertTrue(entriesFound.containsKey("detection_logs.txt"))
            assertTrue(entriesFound.containsKey("detection_logs.json"))

            val unzippedText = entriesFound["detection_logs.txt"]
            val unzippedJson = entriesFound["detection_logs.json"]

            assertNotNull(unzippedText)
            assertNotNull(unzippedJson)

            assertTrue(unzippedText!!.contains("Test message for zip"))
            assertTrue(unzippedJson!!.contains("Test message for zip"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
