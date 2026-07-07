package com.scanbase.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanbase.app.camera.CameraCaptureScreen
import com.scanbase.app.data.ScanDocument
import com.scanbase.app.data.ScanPage
import com.scanbase.app.pdf.PdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)
    private var importedImagePath by mutableStateOf<String?>(null)
    private var galleryMessage by mutableStateOf<String?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            galleryMessage = "\uC774\uBBF8\uC9C0 \uC120\uD0DD\uC774 \uCDE8\uC18C\uB418\uC5C8\uC2B5\uB2C8\uB2E4."
            return@registerForActivityResult
        }

        val copiedPath = copyPickedImageToCache(uri)
        if (copiedPath == null) {
            galleryMessage = "\uC774\uBBF8\uC9C0\uB97C \uAC00\uC838\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4."
        } else {
            importedImagePath = copiedPath
            galleryMessage = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCameraPermission = isCameraPermissionGranted()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    ScanBaseApp(
                        hasCameraPermission = hasCameraPermission,
                        importedImagePath = importedImagePath,
                        galleryMessage = galleryMessage,
                        onRequestCameraPermission = ::requestCameraPermission,
                        onPickGalleryImage = ::pickGalleryImage,
                        onImportedImageHandled = { importedImagePath = null },
                        onGalleryMessageShown = { galleryMessage = null }
                    )
                }
            }
        )
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun pickGalleryImage() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun copyPickedImageToCache(uri: Uri): String? {
        return runCatching {
            val extension = resolveImageExtension(this, uri)
            val imageFile = createGalleryCacheFile(this, extension)

            contentResolver.openInputStream(uri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            imageFile.absolutePath
        }.getOrNull()
    }
}

@Composable
fun ScanBaseApp(
    hasCameraPermission: Boolean,
    importedImagePath: String?,
    galleryMessage: String?,
    onRequestCameraPermission: () -> Unit,
    onPickGalleryImage: () -> Unit,
    onImportedImageHandled: () -> Unit,
    onGalleryMessageShown: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var scanDocument by remember { mutableStateOf(ScanDocument.empty()) }
    var selectedPageId by remember { mutableStateOf<Long?>(null) }
    var homeMessage by remember { mutableStateOf<String?>(null) }
    var documentMessage by remember { mutableStateOf<String?>(null) }
    var savedPdfPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(importedImagePath) {
        importedImagePath?.let { imagePath ->
            previewImagePath = imagePath
            onImportedImageHandled()
            navController.navigate(Screen.Preview.route)
        }
    }

    LaunchedEffect(galleryMessage) {
        galleryMessage?.let { message ->
            homeMessage = message
            onGalleryMessageShown()
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) {
                    inclusive = false
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                message = homeMessage,
                onNewScanClick = { navController.navigate(Screen.Camera.route) },
                onGalleryClick = onPickGalleryImage,
                onRecentDocumentsClick = { navController.navigate(Screen.DocumentPreview.route) }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                onBackClick = navController::navigateUp,
                onImageCaptured = { imagePath ->
                    previewImagePath = imagePath
                    navController.navigate(Screen.Preview.route)
                }
            )
        }
        composable(Screen.GalleryImport.route) {
            GalleryImportScreen(onBackClick = navController::navigateUp)
        }
        composable(Screen.Preview.route) {
            PreviewScreen(
                imagePath = previewImagePath,
                onBackClick = navController::navigateUp,
                onRetakeClick = { navController.navigate(Screen.Camera.route) },
                onAddPageClick = {
                    previewImagePath?.let { imagePath ->
                        val page = ScanPage(
                            id = System.currentTimeMillis(),
                            imagePath = imagePath,
                            createdAtMillis = System.currentTimeMillis()
                        )
                        scanDocument = scanDocument.copy(
                            pages = scanDocument.pages + page
                        )
                        selectedPageId = page.id
                        savedPdfPath = null
                        documentMessage = null
                    }
                    navController.navigate(Screen.DocumentPreview.route)
                },
                onCropClick = { navController.navigate(Screen.Crop.route) }
            )
        }
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(
                document = scanDocument,
                selectedPageId = selectedPageId,
                message = documentMessage,
                savedPdfPath = savedPdfPath,
                onBackClick = navController::navigateUp,
                onAddPageClick = { navController.navigate(Screen.Camera.route) },
                onSelectPage = { pageId -> selectedPageId = pageId },
                onDeletePage = { pageId ->
                    scanDocument = scanDocument.copy(
                        pages = scanDocument.pages.filterNot { it.id == pageId }
                    )
                    savedPdfPath = null
                    if (selectedPageId == pageId) {
                        selectedPageId = scanDocument.pages.firstOrNull { it.id != pageId }?.id
                    }
                },
                onMovePageUp = { pageId ->
                    scanDocument = scanDocument.copy(
                        pages = movePage(scanDocument.pages, pageId, -1)
                    )
                    savedPdfPath = null
                },
                onMovePageDown = { pageId ->
                    scanDocument = scanDocument.copy(
                        pages = movePage(scanDocument.pages, pageId, 1)
                    )
                    savedPdfPath = null
                },
                onSavePdfClick = {
                    if (scanDocument.pages.isEmpty()) {
                        documentMessage = "\uD398\uC774\uC9C0\uAC00 \uC5C6\uC5B4 \u0050\u0044\u0046\uB97C \uC0DD\uC131\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."
                    } else {
                        runCatching {
                            PdfExporter.save(context, scanDocument)
                        }.onSuccess { file ->
                            savedPdfPath = file.absolutePath
                            documentMessage = "\u0050\u0044\u0046 \uC800\uC7A5 \uC644\uB8CC"
                        }.onFailure {
                            documentMessage = "\u0050\u0044\u0046 \uC800\uC7A5\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."
                        }
                    }
                },
                onSharePdfClick = {
                    if (scanDocument.pages.isEmpty()) {
                        documentMessage = "\uD398\uC774\uC9C0\uAC00 \uC5C6\uC5B4 \uACF5\uC720\uD560 \u0050\u0044\u0046\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
                    } else {
                        runCatching {
                            val existingFile = savedPdfPath?.let { File(it) }?.takeIf { it.exists() }
                            val pdfFile = existingFile ?: PdfExporter.save(context, scanDocument).also {
                                savedPdfPath = it.absolutePath
                            }
                            PdfExporter.share(context, pdfFile)
                        }.onFailure {
                            documentMessage = "\u0050\u0044\u0046 \uACF5\uC720\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4."
                        }
                    }
                }
            )
        }
        composable(Screen.Crop.route) {
            CropScreen(onBackClick = navController::navigateUp)
        }
    }
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Camera : Screen("camera")
    data object GalleryImport : Screen("gallery_import")
    data object Preview : Screen("preview")
    data object DocumentPreview : Screen("document_preview")
    data object Crop : Screen("crop")
}

@Composable
fun HomeScreen(
    message: String?,
    onNewScanClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRecentDocumentsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = "ScanBase",
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(18.dp))
            NoticeText(text = message)
        }

        Spacer(modifier = Modifier.height(40.dp))

        HomeButton(text = "\uC0C8 \uC2A4\uCE94", onClick = onNewScanClick)
        Spacer(modifier = Modifier.height(14.dp))
        HomeButton(text = "\uAC24\uB7EC\uB9AC\uC5D0\uC11C \uAC00\uC838\uC624\uAE30", onClick = onGalleryClick)
        Spacer(modifier = Modifier.height(14.dp))
        HomeButton(text = "\uCD5C\uADFC \uBB38\uC11C", onClick = onRecentDocumentsClick)
    }
}

@Composable
fun CameraScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onBackClick: () -> Unit,
    onImageCaptured: (String) -> Unit
) {
    CameraCaptureScreen(
        hasCameraPermission = hasCameraPermission,
        onRequestCameraPermission = onRequestCameraPermission,
        onBackClick = onBackClick,
        onImageCaptured = onImageCaptured
    )
}

@Composable
fun GalleryImportScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(
        title = "\uAC24\uB7EC\uB9AC\uC5D0\uC11C \uAC00\uC838\uC624\uAE30",
        message = "\uD648 \uD654\uBA74\uC5D0\uC11C \uC774\uBBF8\uC9C0\uB97C \uC120\uD0DD\uD574 \uC8FC\uC138\uC694.",
        onBackClick = onBackClick
    )
}

@Composable
fun DocumentPreviewScreen(
    document: ScanDocument,
    selectedPageId: Long?,
    message: String?,
    savedPdfPath: String?,
    onBackClick: () -> Unit,
    onAddPageClick: () -> Unit,
    onSelectPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePageUp: (Long) -> Unit,
    onMovePageDown: (Long) -> Unit,
    onSavePdfClick: () -> Unit,
    onSharePdfClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBackClick)
            BasicText(
                text = "\uCD5C\uADFC \uBB38\uC11C",
                style = TextStyle(
                    color = Color(0xFF111827),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 58.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (message != null) {
            NoticeText(text = message)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (savedPdfPath != null) {
            NoticeText(text = "\uC800\uC7A5 \uACBD\uB85C: $savedPdfPath")
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (document.pages.isEmpty()) {
            PlaceholderContent(
                title = "\uBE48 \uBB38\uC11C",
                message = "\uC544\uC9C1 \uC2A4\uCE94\uD55C \uD398\uC774\uC9C0\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
            )
            ActionButton(text = "\uD398\uC774\uC9C0 \uCD94\uAC00", onClick = onAddPageClick)
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(text = "\u0050\u0044\u0046 \uC800\uC7A5", onClick = onSavePdfClick)
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(text = "\uACF5\uC720", onClick = onSharePdfClick)
        } else {
            DocumentPageManager(
                document = document,
                selectedPageId = selectedPageId,
                onAddPageClick = onAddPageClick,
                onSavePdfClick = onSavePdfClick,
                onSharePdfClick = onSharePdfClick,
                onSelectPage = onSelectPage,
                onDeletePage = onDeletePage,
                onMovePageUp = onMovePageUp,
                onMovePageDown = onMovePageDown
            )
        }
    }
}

@Composable
fun PreviewScreen(
    imagePath: String?,
    onBackClick: () -> Unit,
    onRetakeClick: () -> Unit,
    onAddPageClick: () -> Unit,
    onCropClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBackClick)
            BasicText(
                text = "\uBBF8\uB9AC\uBCF4\uAE30",
                style = TextStyle(
                    color = Color(0xFF111827),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 58.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (imagePath == null) {
            PlaceholderContent(
                title = "\uC774\uBBF8\uC9C0 \uC5C6\uC74C",
                message = "\uBBF8\uB9AC\uBCFC \uC774\uBBF8\uC9C0\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
            )
        } else {
            ImagePreviewArea(
                imagePath = imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            ActionButton(text = "\uB2E4\uC2DC \uCD2C\uC601", onClick = onRetakeClick)
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(text = "\uD398\uC774\uC9C0\uC5D0 \uCD94\uAC00", onClick = onAddPageClick)
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(text = "\uC790\uB974\uAE30/\uBCF4\uC815", onClick = onCropClick)
        }
    }
}

@Composable
fun CropScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(
        title = "\uC790\uB974\uAE30/\uBCF4\uC815",
        message = "\uC790\uB974\uAE30\uC640 \uBCF4\uC815\uC740 \uB2E4\uC74C \uB2E8\uACC4\uC5D0\uC11C \uAD6C\uD604\uD569\uB2C8\uB2E4.",
        onBackClick = onBackClick
    )
}

@Composable
private fun DocumentPageManager(
    document: ScanDocument,
    selectedPageId: Long?,
    onAddPageClick: () -> Unit,
    onSavePdfClick: () -> Unit,
    onSharePdfClick: () -> Unit,
    onSelectPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePageUp: (Long) -> Unit,
    onMovePageDown: (Long) -> Unit
) {
    val selectedPage = document.pages.firstOrNull { it.id == selectedPageId }
        ?: document.pages.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ImagePreviewArea(
            imagePath = selectedPage.imagePath,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        ActionButton(text = "\uD398\uC774\uC9C0 \uCD94\uAC00", onClick = onAddPageClick)
        Spacer(modifier = Modifier.height(10.dp))
        ActionButton(text = "\u0050\u0044\u0046 \uC800\uC7A5", onClick = onSavePdfClick)
        Spacer(modifier = Modifier.height(10.dp))
        ActionButton(text = "\uACF5\uC720", onClick = onSharePdfClick)

        Spacer(modifier = Modifier.height(18.dp))

        BasicText(
            text = "\uD398\uC774\uC9C0 \uBAA9\uB85D",
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        document.pages.forEachIndexed { index, page ->
            PageListItem(
                page = page,
                pageNumber = index + 1,
                isSelected = page.id == selectedPage.id,
                canMoveUp = index > 0,
                canMoveDown = index < document.pages.lastIndex,
                onSelectPage = onSelectPage,
                onDeletePage = onDeletePage,
                onMovePageUp = onMovePageUp,
                onMovePageDown = onMovePageDown
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PageListItem(
    page: ScanPage,
    pageNumber: Int,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePageUp: (Long) -> Unit,
    onMovePageDown: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFFEFF6FF) else Color.White)
            .clickable { onSelectPage(page.id) }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImagePreviewArea(
                imagePath = page.imagePath,
                modifier = Modifier.size(82.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = "\uD398\uC774\uC9C0 $pageNumber",
                    style = TextStyle(
                        color = Color(0xFF111827),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = if (isSelected) "\uC120\uD0DD\uB428" else "\uD0ED\uD558\uBA74 \uD06C\uAC8C \uBCF4\uAE30",
                    style = TextStyle(
                        color = Color(0xFF4B5563),
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(
                text = "\uC704",
                enabled = canMoveUp,
                onClick = { onMovePageUp(page.id) },
                modifier = Modifier.weight(1f)
            )
            SmallActionButton(
                text = "\uC544\uB798",
                enabled = canMoveDown,
                onClick = { onMovePageDown(page.id) },
                modifier = Modifier.weight(1f)
            )
            SmallActionButton(
                text = "\uC0AD\uC81C",
                enabled = true,
                onClick = { onDeletePage(page.id) },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFB91C1C)
            )
        }
    }
}

@Composable
private fun ImagePreviewArea(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)
    }

    if (bitmap == null) {
        BasicText(
            text = "\uC774\uBBF8\uC9C0\uB97C \uBD88\uB7EC\uC62C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.",
            style = TextStyle(
                color = Color(0xFFB91C1C),
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            ),
            modifier = modifier.fillMaxWidth()
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "\uC120\uD0DD\uD55C \uC774\uBBF8\uC9C0",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        BackButton(onClick = onBackClick)
        PlaceholderContent(title = title, message = message)
    }
}

@Composable
private fun PlaceholderContent(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        BasicText(
            text = message,
            style = TextStyle(
                color = Color(0xFF4B5563),
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoticeText(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Color(0xFF92400E),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF7ED))
            .padding(12.dp)
    )
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2563EB))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    )
}

@Composable
private fun SmallActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF374151)
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) backgroundColor else Color(0xFF9CA3AF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "<",
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicText(
            text = "\uB4A4\uB85C",
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun HomeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2563EB))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun resolveImageExtension(context: Context, uri: Uri): String {
    val mimeType = context.contentResolver.getType(uri)
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    return extension?.takeIf { it.isNotBlank() } ?: "jpg"
}

private fun createGalleryCacheFile(context: Context, extension: String): File {
    val directory = File(context.cacheDir, "imports").apply {
        mkdirs()
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        .format(System.currentTimeMillis())
    return File(directory, "gallery_$timestamp.$extension")
}

private fun movePage(
    pages: List<ScanPage>,
    pageId: Long,
    direction: Int
): List<ScanPage> {
    val currentIndex = pages.indexOfFirst { it.id == pageId }
    if (currentIndex == -1) return pages

    val targetIndex = currentIndex + direction
    if (targetIndex !in pages.indices) return pages

    return pages.toMutableList().apply {
        val page = removeAt(currentIndex)
        add(targetIndex, page)
    }
}
