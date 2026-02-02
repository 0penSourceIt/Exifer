package com.image.exifer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.image.exifer.ui.theme.DarkBg
import com.image.exifer.ui.theme.TextDim
import kotlinx.coroutines.launch
import java.io.File
import com.image.exifer.exifAnalyzer.AnalysisViewMode
import com.image.exifer.exifAnalyzer.CybersecurityCategory

// ==========================================================
// 1. MAIN UI SCREEN: ExifScreen
// ==========================================================

@Composable
fun ExifScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var rawExifData by rememberSaveable { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var rawMakerNoteData by rememberSaveable { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var rawDeepPayloadData by rememberSaveable { mutableStateOf<Map<String, String?>>(emptyMap()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var viewMode by rememberSaveable { mutableStateOf<AnalysisViewMode?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf("Target image analysis required...") }
    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var showSearchInfo by rememberSaveable { mutableStateOf(false) }

    val neonYellow = Color(0xFFFFFF00)
    val neonCyan = Color(0xFF00FFFF)
    val hackerGreen = Color(0xFF00FF00)
    val hackerDark = Color(0xFF001100)
    val hackerBorder = Color(0xFF00AA00)
    val hackerRed = Color(0xFFFF0000)
    val homeScreenBorder = Color(0xFF00AA00) // neon

    fun processImage(uri: Uri) {
        selectedUri = uri
        scope.launch {
            isProcessing = true
            statusMessage = "FORCE INTRUSION IN PROGRESS..."
            val mime = context.contentResolver.getType(uri) ?: ""

            if (mime.contains("heic") || mime.contains("heif")) {
                statusMessage = "⚠ HEIC images may contain limited metadata on Android"
            }
            val result = exifAnalyzer.extractEverythingMetadata(context, uri)
            rawExifData = result.first
            rawMakerNoteData = result.second
            rawDeepPayloadData = result.third
            statusMessage = if (rawExifData.isNotEmpty()) "✅ SYSTEM COMPROMISED: PAYLOAD CAPTURED" else "⚠️ WEAK PAYLOAD: NO DATA FOUND"
            isProcessing = false
        }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { processImage(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            processImage(uris.first()) // UI same, first image show
            statusMessage = "✅ Batch Loaded: ${uris.size} Images (First Scanned)"
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) { tempCameraUri?.let { processImage(it) } } }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "temp_scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "CAMERA PERMISSION DENIED", Toast.LENGTH_SHORT).show()
        }
    }

    // POWERFUL SEARCH FILTER
    val allData = remember(rawExifData, rawMakerNoteData, rawDeepPayloadData, searchQuery) {

        val merged = rawExifData + rawMakerNoteData + rawDeepPayloadData

        if (searchQuery.isBlank()) return@remember merged

        val query = searchQuery.trim().lowercase()

        // Smart normalization
        fun normalize(text: String): String {
            return text.lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace(":", "")
        }

        // Synonym dictionary (user friendly)
        val synonyms = mapOf(
            "location" to listOf("gps", "latitude", "longitude", "coords"),
            "device" to listOf("make", "model", "serial", "camera"),
            "edited" to listOf("photoshop", "snapseed", "tampering"),
            "hash" to listOf("sha", "sha256", "fingerprint"),
            "forensic" to listOf("risk", "payload", "evidence")
        )

        val expandedQueries = mutableListOf(query)

        synonyms.forEach { (key, words) ->
            if (query.contains(key)) expandedQueries.addAll(words)
        }

        merged.filter { (k, v) ->

            val keyNorm = normalize(k)
            val valueNorm = normalize(v ?: "")

            expandedQueries.any { q ->
                val qNorm = normalize(q)

                keyNorm.contains(qNorm) ||
                        valueNorm.contains(qNorm) ||

                        // ✅ Smart fuzzy fallback (3-letter token match)
                        (qNorm.length >= 2 && qNorm.chunked(2).any { token ->
                            keyNorm.contains(token) || valueNorm.contains(token)
                        })
            }
        }
    }

    val categories = remember(allData) {
        exifAnalyzer.buildCybersecurityCategories(allData)
    }

    if (showSearchInfo) {
        HackerSearchInfoDialog(onDismiss = { showSearchInfo = false }, neonYellow = neonYellow, hackerDark = hackerDark)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(hackerDark, DarkBg)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = hackerGreen)
            }
            Text(
                text = "EXIF ANALYZER 🔓",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(hackerGreen, hackerRed))
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                selectedUri = null
                rawExifData = emptyMap()
                rawMakerNoteData = emptyMap()
                rawDeepPayloadData = emptyMap()
                searchQuery = ""
                viewMode = null
                statusMessage = "Ready for target analysis..."
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = hackerGreen)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RectHackerButton(
                        modifier = Modifier.weight(1f),
                        text = "GALLERY",
                        icon = Icons.Default.Image,
                        color = hackerGreen,
                        borderColor = homeScreenBorder,
                        onClick = { galleryLauncher.launch("image/*") }
                    )

                    RectHackerButton(
                        modifier = Modifier.weight(1f),
                        text = "CAMERA",
                        icon = Icons.Default.CameraAlt,
                        color = hackerGreen,
                        borderColor = homeScreenBorder,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val file = File(
                                    context.cacheDir,
                                    "temp_scan_${System.currentTimeMillis()}.jpg"
                                )
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }

            }

            item {
                StatusSection(
                    message = statusMessage,
                    isBlinking = isProcessing,
                    textColor = if (isProcessing) hackerRed else hackerGreen
                )
            }

            if (allData.isNotEmpty() || rawExifData.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionRectButton(
                            modifier = Modifier.weight(1f),
                            text = "REMOVE EXIF DATA",
                            color = hackerRed,
                            onClick = {
                                selectedUri?.let {
                                    val cleaned = exifAnalyzer.stripExif(context, it)
                                    Toast.makeText(
                                        context,
                                        if (cleaned != null) "✅ EXIF Data Removed and SAVED to Downloads/Exif_Data_Removed"
                                        else "⚠ EXIF Removal Failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        ActionRectButton(
                            modifier = Modifier.weight(1f),
                            text = "⎙ SAVE PDF REPORT",
                            color = neonYellow,
                            onClick = {
                                val merged = rawExifData + rawMakerNoteData + rawDeepPayloadData
                                val pdfUri = exifAnalyzer.exportForensicPdf(context, merged)

                                Toast.makeText(
                                    context,
                                    if (pdfUri != null) "✅ PDF Report Saved to Downloads/Exif_Data_Reports"
                                    else "⚠ PDF Export Failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }

                item {
                    HackerSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onInfoClick = { showSearchInfo = true },
                        neonCyan = neonCyan,
                        neonYellow = neonYellow,
                        hackerDark = hackerDark
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("GPS", "Device", "Edited", "Owner", "Serial").forEach { hint ->
                            AssistChip(
                                onClick = { searchQuery = hint },
                                label = {
                                    Text(
                                        hint,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF002200),
                                    labelColor = neonCyan
                                )
                            )
                        }
                    }
                }


                item {
                    QuickIntelSection(
                        allData = allData,
                        selectedUri = selectedUri,
                        context = context,
                        neonCyan = neonCyan,
                        hackerDark = hackerDark
                    )
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(hackerDark)
                                .border(1.dp, hackerBorder, RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable { viewMode = AnalysisViewMode.CATEGORIZED },
                                color = if (viewMode == AnalysisViewMode.CATEGORIZED) hackerGreen.copy(alpha = 0.2f) else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("CATEGORIZED", color = if (viewMode == AnalysisViewMode.CATEGORIZED) hackerGreen else hackerGreen.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(hackerBorder))
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable { viewMode = AnalysisViewMode.COMPLETE },
                                color = if (viewMode == AnalysisViewMode.COMPLETE) hackerGreen.copy(alpha = 0.2f) else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("COMPLETE LIST", color = if (viewMode == AnalysisViewMode.COMPLETE) hackerGreen else hackerGreen.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        Text(
                            text = "// Select mode to expand detailed forensic datastream",
                            color = hackerGreen.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                }

                if (viewMode == AnalysisViewMode.CATEGORIZED) {
                    categories.forEach { category ->
                        if (category.tags.isNotEmpty()) {
                            item(key = category.id) {
                                ExpandableCategoryItem(category)
                            }
                        }
                    }
                } else if (viewMode == AnalysisViewMode.COMPLETE) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(hackerDark).border(1.dp, hackerBorder, RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = if (searchQuery.isEmpty()) "FULL SYSTEM PAYLOAD" else "FILTERED DATASTREAM", color = hackerGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            HorizontalDivider(color = hackerGreen.copy(alpha = 0.3f))
                            categories.forEach { cat ->
                                cat.tags.forEach { (k, v) -> DataRowItem(k, v, cat.riskColor, TextDim) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// 2. UI COMPONENTS
// ==========================================================

@Composable
fun QuickIntelSection(
    allData: Map<String, String?>,
    selectedUri: Uri?,
    context: Context,
    neonCyan: Color,
    hackerDark: Color
) {
    val intel = exifAnalyzer.getQuickIntel(context, selectedUri, allData)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(hackerDark)
            .border(1.dp, neonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("CRITICAL QUICK INTEL", color = neonCyan, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(12.dp))
        intel.forEach { (label, value) ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(label.uppercase(), color = neonCyan.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun HackerSearchInfoDialog(onDismiss: () -> Unit, neonYellow: Color, hackerDark: Color) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, neonYellow, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = hackerDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SEARCH_PROTOCOL.txt", color = neonYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = neonYellow) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val examples = listOf("• Type 'GPS' for tracking intel", "• Type 'Sony' for device specific data", "• Type '2024' for temporal hits", "• Partial keywords like 'lat' or 'ser'", "• Cross-references keys AND values")
                examples.forEach { Text(it, color = neonYellow.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("root@intruder:~$ exit", color = neonYellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun HackerSearchBar(query: String, onQueryChange: (String) -> Unit, onInfoClick: () -> Unit, neonCyan: Color, neonYellow: Color, hackerDark: Color) {
    TextField(
        value = query, onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, neonCyan, RoundedCornerShape(8.dp)),
        placeholder = { Text("root@intruder:~/search# _", color = neonCyan.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = neonCyan) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                if (query.isNotEmpty()) { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = neonCyan) } }
                IconButton(onClick = onInfoClick) { Icon(Icons.Default.Info, contentDescription = "Help", tint = neonYellow) }
            }
        },
        colors = TextFieldDefaults.colors(focusedContainerColor = hackerDark, unfocusedContainerColor = hackerDark, cursorColor = neonCyan, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = neonCyan, unfocusedTextColor = neonCyan),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        singleLine = true
    )
}

@Composable
fun ActionRectButton(
    modifier: Modifier = Modifier,
    text: String,
    //icon: ImageVector? = null,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),

        // ✅ Border matches main color
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),

        // ✅ Light Background based on button color
        color = color.copy(alpha = 0.12f),

        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

//            if (icon != null) {
//                Icon(
//                    imageVector = icon,
//                    contentDescription = null,
//                    tint = color,
//                    modifier = Modifier.size(18.dp)
//                )
//
//                Spacer(modifier = Modifier.width(6.dp))
//            }

            Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


//@Composable
//fun SquareHackerButton(modifier: Modifier = Modifier, text: String, icon: ImageVector, color: Color, borderColor: Color, onClick: () -> Unit) {
//    Surface(modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).border(1.dp, borderColor, RoundedCornerShape(12.dp)), color = borderColor.copy(alpha = 0.10f)
//    ) {
//        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
//            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
//            Spacer(modifier = Modifier.height(8.dp))
//            Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
//        }
//    }
//}

@Composable
fun RectHackerButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    color: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp) // ✅ compact height
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        color = Color(0xFF001100)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


@Composable
fun ExpandableCategoryItem(category: CybersecurityCategory) {
    var expanded by rememberSaveable { mutableStateOf(category.id == 1) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001100)).border(1.dp, category.riskColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = category.title, color = category.riskColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                Text(text = "STATUS: ${category.riskLevel}", color = category.riskColor.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = category.riskColor)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(color = category.riskColor.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                category.tags.forEach { (key, value) -> DataRowItem(key, value, category.riskColor, TextDim) }
            }
        }
    }
}

@Composable
fun StatusSection(message: String, isBlinking: Boolean, textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blinking")
    val alpha by infiniteTransition.animateFloat(initialValue = 1f, targetValue = if (isBlinking) 0.3f else 1f, animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = "> $message", color = textColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.alpha(alpha), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DataRowItem(key: String, value: String?, labelColor: Color, valueColor: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "$key:", color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = value ?: "EMPTY", color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        HorizontalDivider(color = labelColor.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}
