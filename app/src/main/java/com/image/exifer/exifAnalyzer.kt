package com.image.exifer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.makernotes.*
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.itextpdf.io.font.constants.StandardFontFamilies
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.*
import androidx.compose.ui.graphics.Color

object ExifAnalyzer {

    // ==========================================================
    // DATA MODELS
    // ==========================================================
    enum class AnalysisViewMode {
        CATEGORIZED, COMPLETE
    }

    data class CybersecurityCategory(
        val id: Int,
        val title: String,
        val riskLevel: String,
        val riskColor: Color,
        val tags: Map<String, String?>
    )

    // ==========================================================
    // MAIN EXTRACTION
    // ==========================================================

    fun extractEverythingMetadata(
        context: Context,
        uri: Uri
    ): Triple<Map<String, String?>, Map<String, String?>, Map<String, String?>> {

        val basicData = mutableMapOf<String, String?>()
        val makerNoteData = mutableMapOf<String, String?>()
        val deepData = mutableMapOf<String, String?>()

        try {

            // ✅ HEIC FIX: Convert stream → temp file for full parsing
            val tempFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            // FULL BASIC EXIF TAG EXTRACTION
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                val allTags = ExifInterface::class.java
                    .fields
                    .filter {
                        it.name.startsWith("TAG_") &&
                                it.type == String::class.java
                    }
                    .mapNotNull { field ->
                        field.get(null) as? String
                    }

                allTags.forEach { tag ->
                    exif.getAttribute(tag)?.let {
                        basicData["EXIF:$tag"] = it
                    }
                }

                // GPS Decimal Extraction
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    basicData["EXIF:GPS_LAT_DECIMAL"] = latLong[0].toString()
                    basicData["EXIF:GPS_LONG_DECIMAL"] = latLong[1].toString()
                }

                // Extract Embedded Thumbnail
                exif.thumbnailBitmap?.let {
                    basicData["EXIF:Embedded Thumbnail"] =
                        "YES (${it.width}x${it.height})"
                }
            }

            // DEEP METADATA EXTRACTION (MAKERNOTES + XMP + IPTC + ICC)
            context.contentResolver.openInputStream(uri)?.use { metadataStream ->
                val metadata = ImageMetadataReader.readMetadata(tempFile)

                for (directory in metadata.directories) {
                    val dirName = directory.name

                    for (tag in directory.tags) {
                        val key = "$dirName: ${tag.tagName}"
                        val value = tag.description

                        if (directory is CanonMakernoteDirectory ||
                            directory is NikonType1MakernoteDirectory ||
                            directory is NikonType2MakernoteDirectory ||
                            directory is SonyType1MakernoteDirectory ||
                            directory is OlympusMakernoteDirectory ||
                            directory is PanasonicMakernoteDirectory ||
                            directory is FujifilmMakernoteDirectory
                        ) {
                            val makerKey = "$dirName:${tag.tagName}"
                            makerNoteData[makerKey] = value
                        } else {
                            if (directory is XmpDirectory ||
                                directory is IptcDirectory ||
                                directory is IccDirectory
                            ) {
                                deepData["DEEP:$key"] = value
                            } else {
                                if (basicData["EXIF:${tag.tagName}"] == null){
                                    deepData["DEEP:$key"] = value
                                }
                            }
                        }
                    }
                }

                getSpecificTag(metadata, "Lens Model")?.let { makerNoteData["Lens Model"] = it }
                getSpecificTag(metadata, "Shutter Count")?.let { makerNoteData["Shutter Count"] = it }
                getSpecificTag(metadata, "Serial Number")?.let { makerNoteData["Camera Serial Number"] = it }
                getSpecificTag(metadata, "Owner Name")?.let { makerNoteData["Owner Name"] = it }
            }

            // 🔥 FORENSIC INTELLIGENCE INJECTION
            val mergedAll = basicData + makerNoteData + deepData

            deepData["FORENSIC: Risk Score"] =
                "${calculateRiskScore(mergedAll)}/100"

            detectTampering(mergedAll)?.let {
                deepData["FORENSIC: Tampering Alert"] = it
            }

            computeSHA256(context, uri)?.let {
                deepData["FORENSIC: SHA-256 Hash"] = it
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Triple(
            basicData.filterValues { !it.isNullOrBlank() },
            makerNoteData.filterValues { !it.isNullOrBlank() },
            deepData.filterValues { !it.isNullOrBlank() }
        )
    }

    private fun getSpecificTag(metadata: Metadata, tagName: String): String? {
        for (directory in metadata.directories) {
            for (tag in directory.tags) {
                if (tag.tagName.equals(tagName, ignoreCase = true)) {
                    return tag.description
                }
            }
        }
        return null
    }

    private fun calculateRiskScore(allData: Map<String, String?>): Int {
        var score = 0

        fun has(keyword: String): Boolean =
            allData.keys.any { it.contains(keyword, ignoreCase = true) }

        if (has("GPS")) score += 35
        if (has("Serial")) score += 20
        if (has("Owner") || has("Artist")) score += 15
        if (has("Photoshop") || has("Adobe") || has("Snapseed")) score += 15
        if (has("XMP") || has("History")) score += 10

        return score.coerceAtMost(100)
    }

    private fun detectTampering(allData: Map<String, String?>): String? {
        val software = allData["EXIF:${ExifInterface.TAG_SOFTWARE}"] ?: return null

        return if (
            software.contains("Photoshop", true) ||
            software.contains("Snapseed", true) ||
            software.contains("Lightroom", true)
        ) {
            "⚠ Possible Editing Detected: $software"
        } else null
    }

    private fun computeSHA256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytes = stream.read(buffer)

                while (bytes > 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = stream.read(buffer)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            null
        }
    }

    // CATEGORIZATION LOGIC
    fun buildCybersecurityCategories(allData: Map<String, String?>): List<CybersecurityCategory> {
        val forensic = mutableMapOf<String, String?>()
        val gps = mutableMapOf<String, String?>()
        val device = mutableMapOf<String, String?>()
        val dateTime = mutableMapOf<String, String?>()
        val software = mutableMapOf<String, String?>()
        val identity = mutableMapOf<String, String?>()
        val thumbnail = mutableMapOf<String, String?>()
        val identifiers = mutableMapOf<String, String?>()
        val network = mutableMapOf<String, String?>()
        val camera = mutableMapOf<String, String?>()
        val autoBuckets = mutableMapOf<String, MutableMap<String, String?>>()

        allData.forEach { (k, v) ->
            val key = k.uppercase()
            when {
                key.contains("FORENSIC") || key.contains("SHA-256") || key.contains("RISK") -> forensic[k] = v
                key.contains("GPS") || key.contains("LATITUDE") || key.contains("LONGITUDE") || key.contains("POSITION") || key.contains("ALTITUDE") -> gps[k] = v
                key == "MAKE" || key == "MODEL" || key.contains("SERIALNUMBER") || key.contains("SERIAL NUMBER") || key.contains("CAMERAID") || key.contains("DEVICE") || key.contains("LENS") -> device[k] = v
                key.contains("DATETIME") || key.contains("CREATEDATE") || key.contains("MODIFYDATE") || key.contains("DATE ORIGINAL") || key.contains("SUBSEC") || key.contains("DATESTAMP") || key.contains("TIMESTAMP") -> dateTime[k] = v
                key.contains("SOFTWARE") || key.contains("CREATORTOOL") || key.contains("ADOBE") || key.contains("PHOTOSHOP") || key.contains("SNAPSEED") || key.contains("PROCESSING") || key.contains("HISTORY") -> software[k] = v
                key == "ARTIST" || key.contains("OWNERNAME") || key.contains("COPYRIGHT") || key.contains("AUTHOR") || key.contains("BY-LINE") -> identity[k] = v
                key.contains("THUMBNAIL") || key.contains("PREVIEWIMAGE") || key.contains("PREVIEW IMAGE") -> thumbnail[k] = v
                key.contains("UNIQUEID") || key.contains("DOCUMENTID") || key.contains("INSTANCEID") -> identifiers[k] = v
                key.contains("HOSTCOMPUTER") || key.contains("NETWORK") || key.contains("TRANSMISSION") -> network[k] = v
                key.contains("EXPOSURE") || key.contains("FNUMBER") || key.contains("F-NUMBER") || key.contains("ISO") || key.contains("FLASH") || key.contains("FOCAL") || key.contains("APERTURE") || key.contains("SHUTTER") -> camera[k] = v
                else -> {
                    val group = k.substringBefore(":").trim()
                    autoBuckets.getOrPut(group.ifEmpty { "UNKNOWN" }) { mutableMapOf() }[k] = v
                }
            }
        }

        val dynamicCategories = autoBuckets.entries.mapIndexed { index, entry ->
            CybersecurityCategory(
                id = 100 + index,
                title = "AUTO: ${entry.key.uppercase()}",
                riskLevel = "UNCLASSIFIED METADATA",
                riskColor = Color(0xFF888888),
                tags = entry.value
            )
        }

        val fixedCategories = listOf(
            CybersecurityCategory(1, "1. GPS / LOCATION DATA", "EXTREME RISK (STALKING)", Color(0xFFFF0000), gps),
            CybersecurityCategory(2, "2. DEVICE IDENTIFICATION", "HIGH RISK (FINGERPRINTING)", Color(0xFFFF4400), device),
            CybersecurityCategory(3, "3. DATE & TIME METADATA", "HIGH RISK (RECONSTRUCTION)", Color(0xFFFF8800), dateTime),
            CybersecurityCategory(4, "4. SOFTWARE / HISTORY", "MEDIUM RISK (TAMPERING)", Color(0xFFFFFF00), software),
            CybersecurityCategory(5, "5. OWNER / AUTHOR INFO", "MEDIUM RISK (IDENTITY LEAK)", Color(0xFF00FFCC), identity),
            CybersecurityCategory(6, "6. THUMBNAIL EMBEDDED DATA", "LOW RISK (HIDDEN COPY)", Color(0xFF00CCFF), thumbnail),
            CybersecurityCategory(7, "7. UNIQUE FILE IDENTIFIERS", "LOW RISK (TRACKING)", Color(0xFF0088FF), identifiers),
            CybersecurityCategory(8, "8. NETWORK / SOURCE", "LOW RISK (INFRASTRUCTURE)", Color(0xFF8844FF), network),
            CybersecurityCategory(9, "9. CAMERA SETTINGS", "STRICTLY FORENSIC", Color(0xFF00FF00), camera),
            CybersecurityCategory(10, "10. FORENSIC INTELLIGENCE", "CRITICAL SECURITY SIGNALS", Color(0xFFFF00FF), forensic)
        )

        return fixedCategories + dynamicCategories
    }

    // PDF EXPORT
    fun exportForensicPdf(
        context: Context,
        allData: Map<String, String?>,
        fileName: String = "Forensic_Report.pdf"
    ): Uri? {
        return try {
            val timestamp = System.currentTimeMillis()
            val pdfFileName = "Forensic_Report_$timestamp.pdf"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Exif_Data_Reports")
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val writer = com.itextpdf.kernel.pdf.PdfWriter(outputStream)
                    val pdf = com.itextpdf.kernel.pdf.PdfDocument(writer)
                    val doc = Document(pdf)

                    val monospaceFont: PdfFont = PdfFontFactory.createFont(StandardFontFamilies.COURIER)

                    doc.add(Paragraph(Text("🔥 EXIF FORENSIC REPORT").setFont(monospaceFont)))
                    doc.add(Paragraph(Text("Generated: ${Date()}").setFont(monospaceFont)))
                    doc.add(Paragraph(Text("====================================").setFont(monospaceFont)))

                    var lineCount = 0
                    allData.forEach { (k, v) ->
                        doc.add(
                            Paragraph(
                                Text("$k : ${v ?: "EMPTY"}").setFont(monospaceFont)
                            )
                        )
                        lineCount++

                        if (lineCount % 45 == 0) {
                            doc.add(AreaBreak())
                        }
                    }

                    doc.close()
                }
                it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // EXIF STRIPPING
    fun stripExif(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val timestamp = System.currentTimeMillis()
            val mime = context.contentResolver.getType(uri)
            val extension = if (mime?.contains("png") == true) "png" else "jpg"
            val cleanFileName = "Exif_Removed_$timestamp.$extension"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFileName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    if (mime?.contains("png") == true) "image/png" else "image/jpeg"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Exif_Data_Removed")
                }
            }

            val resolver = context.contentResolver
            val cleanUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            cleanUri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val format = Bitmap.CompressFormat.JPEG
                    val quality = 95
                    bitmap.compress(format, quality, outputStream)
                }
            }

            cleanUri

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // QUICK INTEL SUMMARY
    fun getQuickIntel(
        context: Context,
        uri: Uri?,
        allData: Map<String, String?>
    ): List<Pair<String, String>> {

        if (uri == null) return emptyList()

        var fileName= "Unknown"
        var fileSize = "Unknown"
        val fileFormat = context.contentResolver.getType(uri) ?: "Unknown"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            if (cursor.moveToFirst()) {
               fileName= cursor.getString(nameIndex) ?: "Unknown"
                val size = cursor.getLong(sizeIndex)

                fileSize = when {
                    size >= 1024 * 1024 ->
                        String.format(Locale.US, "%.2f MB", size / (1024f * 1024f))

                    size >= 1024 ->
                        String.format(Locale.US, "%.2f KB", size / 1024f)

                    else -> "$size Bytes"
                }
            }
        }

        // Search for a deep unique identifier in the metadata
        val docId = allData.entries.find {
            val k = it.key.uppercase()
            k.contains("DOCUMENTID") || k.contains("UNIQUEID") || k.contains("INSTANCEID")
        }?.value ?: "SYSTEM_GEN_NULL"

        return listOf(
            "Display File Name (For User Understanding)" to fileName,
            "Document ID (For System Understanding)" to docId,
            "Size" to fileSize,
            "MIME Format" to fileFormat,
            "Coordinates" to (
                    allData["EXIF:GPS_LAT_DECIMAL"]?.let {
                        "$it, ${allData["EXIF:GPS_LONG_DECIMAL"]}"
                    } ?: "NO SIGNAL"
                    ),
            "Manufacturer" to (allData["EXIF:Make"] ?: allData["DEEP:Exif IFD0: Make"] ?: "Unknown"),
            "System Model" to (allData["EXIF:Model"] ?: allData["DEEP:Exif IFD0: Model"] ?: "Unknown")
        )
    }
}
