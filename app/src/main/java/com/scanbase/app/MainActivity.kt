package com.scanbase.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanbase.app.camera.CameraCaptureScreen
import com.scanbase.app.crop.CropCorner
import com.scanbase.app.crop.CropState
import com.scanbase.app.crop.CropViewModel
import com.scanbase.app.crop.insetFallbackCorners
import com.scanbase.app.crop.pointFor
import com.scanbase.app.data.DocumentCorners
import com.scanbase.app.data.DocumentRecord
import com.scanbase.app.data.ScanDocument
import com.scanbase.app.data.ScanPage
import com.scanbase.app.document.DeleteResult
import com.scanbase.app.document.DocumentRecordStore
import com.scanbase.app.image.CropCoordinateMapper
import com.scanbase.app.image.CropPoint
import com.scanbase.app.image.DocumentDetector
import com.scanbase.app.image.EnhanceMode
import com.scanbase.app.image.ImageEnhancer
import com.scanbase.app.image.ImageFileNormalizer
import com.scanbase.app.image.ImageQualityAnalyzer
import com.scanbase.app.image.QualityResult
import com.scanbase.app.image.OpenCvRuntime
import com.scanbase.app.image.PerspectiveTransformer
import com.scanbase.app.pdf.PdfExporter
import java.io.File
import kotlin.math.hypot

private const val AppTag = "ScanBaseImageFlow"

class MainActivity : ComponentActivity() {
    private val cropViewModel by viewModels<CropViewModel>()
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

        val normalizedUri = ImageFileNormalizer.normalizeToCache(this, uri)

        if (normalizedUri == null) {
            galleryMessage = "\uC774\uBBF8\uC9C0\uB97C \uAC00\uC838\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4."
        } else {
            importedImagePath = normalizedUri.toString()
            galleryMessage = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCvRuntime.initialize()
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
                        onGalleryMessageShown = { galleryMessage = null },
                        cropViewModel = cropViewModel
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

}

@Composable
fun ScanBaseApp(
    hasCameraPermission: Boolean,
    importedImagePath: String?,
    galleryMessage: String?,
    onRequestCameraPermission: () -> Unit,
    onPickGalleryImage: () -> Unit,
    onImportedImageHandled: () -> Unit,
    onGalleryMessageShown: () -> Unit,
    cropViewModel: CropViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var scanDocument by remember { mutableStateOf(ScanDocument.empty()) }
    var selectedPageId by remember { mutableStateOf<Long?>(null) }
    var homeMessage by remember { mutableStateOf<String?>(null) }
    var documentMessage by remember { mutableStateOf<String?>(null) }
    var savedPdfPath by remember { mutableStateOf<String?>(null) }
    var cropCorners by remember { mutableStateOf<DocumentCorners?>(null) }
    var cropImagePath by remember { mutableStateOf<String?>(null) }
    var cropMessage by remember { mutableStateOf<String?>(null) }
    var resultImagePath by remember { mutableStateOf<String?>(null) }
    val documentRecordStore = remember { DocumentRecordStore(context) }
    var recentDocuments by remember { mutableStateOf<List<DocumentRecord>>(emptyList()) }
    var recentMessage by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRecord by remember { mutableStateOf<DocumentRecord?>(null) }
    var editingPageId by remember { mutableStateOf<Long?>(null) }
    var selectedPageShowOriginal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        recentDocuments = documentRecordStore.load()
    }

    LaunchedEffect(importedImagePath) {
        importedImagePath?.let { imagePath ->
            previewImagePath = imagePath
            cropCorners = null
            cropImagePath = null
            cropViewModel.resetIfImageChanged(imagePath)
            cropMessage = null
            resultImagePath = null
            selectedPageShowOriginal = false
            onImportedImageHandled()

            if (editingPageId == null) {
                navController.navigate(Screen.Preview.route)
            } else {
                decodeBitmapFromUriString(context, imagePath)?.let { bitmap ->
                    try {
                        val detectedCorners = DocumentDetector.detect(bitmap)
                        cropViewModel.initialize(
                            imageUri = imagePath,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                            detectedCorners = detectedCorners
                        )
                        cropCorners = detectedCorners
                        cropImagePath = imagePath
                    } finally {
                        bitmap.recycle()
                    }
                }
                navController.navigate(Screen.Crop.route)
            }
        }
    }

    LaunchedEffect(galleryMessage) {
        galleryMessage?.let { message ->
            homeMessage = message
            editingPageId = null
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
                onNewScanClick = {
                    editingPageId = null
                    navController.navigate(Screen.Camera.route)
                },
                onGalleryClick = {
                    editingPageId = null
                    onPickGalleryImage()
                },
                onRecentDocumentsClick = {
                    recentDocuments = documentRecordStore.load()
                    navController.navigate(Screen.RecentDocuments.route)
                }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                onBackClick = {
                    if (editingPageId != null) {
                        editingPageId = null
                        previewImagePath = null
                        resultImagePath = null
                    }
                    navController.navigateUp()
                },
                onImageCaptured = { imagePath ->
                    previewImagePath = imagePath
                    cropCorners = null
                    cropImagePath = null
                    cropViewModel.resetIfImageChanged(imagePath)
                    cropMessage = null
                    resultImagePath = null
                    selectedPageShowOriginal = false

                    if (editingPageId == null) {
                        navController.navigate(Screen.Preview.route)
                    } else {
                        decodeBitmapFromUriString(context, imagePath)?.let { bitmap ->
                            try {
                                val detectedCorners = DocumentDetector.detect(bitmap)
                                cropViewModel.initialize(
                                    imageUri = imagePath,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    detectedCorners = detectedCorners
                                )
                                cropCorners = detectedCorners
                                cropImagePath = imagePath
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        navController.navigate(Screen.Crop.route)
                    }
                }
            )
        }
        composable(Screen.Preview.route) {
            PreviewScreen(
                imagePath = previewImagePath,
                onBackClick = navController::navigateUp,
                onRetakeClick = {
                    editingPageId = null
                    navController.navigate(Screen.Camera.route)
                },
                onAddPageClick = {
                    previewImagePath?.let { imagePath ->
                        val page = ScanPage(
                            id = System.currentTimeMillis(),
                            originalImageUri = imagePath,
                            perspectiveImageUri = null,
                            processedImageUri = imagePath,
                            enhanceMode = EnhanceMode.Original,
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
                onCropClick = {
                    val imagePath = previewImagePath
                    if (imagePath != null) {
                        cropViewModel.resetIfImageChanged(imagePath)
                        if (!cropViewModel.state.isInitialized) {
                            decodeBitmapFromUriString(context, imagePath)?.let { bitmap ->
                                try {
                                    val detectedCorners = DocumentDetector.detect(bitmap)
                                    cropViewModel.initialize(
                                        imageUri = imagePath,
                                        bitmapWidth = bitmap.width,
                                        bitmapHeight = bitmap.height,
                                        detectedCorners = detectedCorners
                                    )
                                    cropCorners = detectedCorners
                                    cropImagePath = imagePath
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                    navController.navigate(Screen.Crop.route)
                }
            )
        }
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(
                document = scanDocument,
                selectedPageId = selectedPageId,
                message = documentMessage,
                savedPdfPath = savedPdfPath,
                onBackClick = navController::navigateUp,
                onAddPageClick = {
                    editingPageId = null
                    navController.navigate(Screen.Camera.route)
                },
                selectedPageShowOriginal = selectedPageShowOriginal,
                onSelectPage = { pageId ->
                    selectedPageId = pageId
                    selectedPageShowOriginal = scanDocument.pages.firstOrNull { it.id == pageId }?.useOriginalForExport ?: false
                },
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
                onShowOriginalPage = {
                    selectedPageShowOriginal = true
                },
                onShowProcessedPage = {
                    selectedPageShowOriginal = false
                },
                onRecropPage = { page ->
                    editingPageId = page.id
                    previewImagePath = page.originalImageUri
                    resultImagePath = null
                    cropMessage = null
                    cropCorners = null
                    cropImagePath = null
                    cropViewModel.resetIfImageChanged(page.originalImageUri)
                    decodeBitmapFromUriString(context, page.originalImageUri)?.let { bitmap ->
                        try {
                            val detectedCorners = DocumentDetector.detect(bitmap)
                            cropViewModel.initialize(
                                imageUri = page.originalImageUri,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                                detectedCorners = detectedCorners
                            )
                            cropCorners = detectedCorners
                            cropImagePath = page.originalImageUri
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    navController.navigate(Screen.Crop.route)
                },
                onReplaceWithCamera = { page ->
                    editingPageId = page.id
                    previewImagePath = null
                    resultImagePath = null
                    cropMessage = null
                    cropCorners = null
                    cropImagePath = null
                    selectedPageShowOriginal = false
                    navController.navigate(Screen.Camera.route)
                },
                onReplaceWithGallery = { page ->
                    editingPageId = page.id
                    previewImagePath = null
                    resultImagePath = null
                    cropMessage = null
                    cropCorners = null
                    cropImagePath = null
                    selectedPageShowOriginal = false
                    onPickGalleryImage()
                },
                onUseOriginalForExport = { pageId ->
                    scanDocument = scanDocument.copy(
                        pages = scanDocument.pages.map { page ->
                            if (page.id == pageId) page.copy(useOriginalForExport = true) else page
                        }
                    )
                    selectedPageShowOriginal = true
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
                            val record = DocumentRecord.fromPdfFile(
                                file = file,
                                pageCount = scanDocument.pages.size
                            )
                            recentDocuments = documentRecordStore.add(record)
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
        composable(Screen.RecentDocuments.route) {
            RecentDocumentsScreen(
                records = recentDocuments,
                message = recentMessage,
                pendingDeleteRecord = pendingDeleteRecord,
                onBackClick = navController::navigateUp,
                onOpenClick = { record ->
                    val opened = PdfExporter.open(context, File(record.pdfPath))
                    if (!opened) {
                        recentMessage = "PDF를 열 수 있는 앱이 없습니다."
                    }
                },
                onShareClick = { record ->
                    runCatching { PdfExporter.share(context, File(record.pdfPath)) }
                        .onFailure { recentMessage = "PDF 공유에 실패했습니다." }
                },
                onDeleteClick = { record -> pendingDeleteRecord = record },
                onDismissDelete = { pendingDeleteRecord = null },
                onConfirmDelete = { record ->
                    when (documentRecordStore.delete(record.id)) {
                        DeleteResult.Deleted -> {
                            recentDocuments = documentRecordStore.load()
                            recentMessage = "문서를 삭제했습니다."
                        }
                        DeleteResult.FileDeleteFailed -> {
                            recentMessage = "PDF 파일을 삭제하지 못했습니다."
                        }
                        DeleteResult.NotFound -> {
                            recentDocuments = documentRecordStore.load()
                            recentMessage = "문서를 찾을 수 없습니다."
                        }
                    }
                    pendingDeleteRecord = null
                }
            )
        }
        composable(Screen.Crop.route) {
            CropScreen(
                imagePath = previewImagePath,
                cropState = cropViewModel.state,
                message = cropMessage,
                onBackClick = {
                    if (editingPageId != null) {
                        editingPageId = null
                    }
                    navController.navigateUp()
                },
                onSelectCorner = cropViewModel::setSelectedCorner,
                onUpdateCorner = cropViewModel::updateCorner,
                onFineTuneCorner = cropViewModel::moveSelectedCorner,
                onApplyClick = {
                    val imagePath = previewImagePath
                    val latestCorners = cropViewModel.latestUserCorners()
                    if (imagePath == null || latestCorners == null) {
                        cropMessage = "선택 영역을 보정할 수 없습니다. 코너를 다시 조정해주세요."
                    } else {
                        val processedUri = decodeBitmapFromUriString(context, imagePath)?.let { bitmap ->
                            try {
                                PerspectiveTransformer.transformToCache(
                                    context = context,
                                    sourceBitmap = bitmap,
                                    corners = latestCorners
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }

                        if (processedUri == null) {
                            cropMessage = "선택 영역을 보정할 수 없습니다. 코너를 다시 조정해주세요."
                        } else {
                            val processedPath = processedUri.toString()
                            cropMessage = null
                            cropCorners = latestCorners
                            cropImagePath = imagePath
                            resultImagePath = processedPath
                            navController.navigate(Screen.Enhance.route)
                        }
                    }
                }
            )
        }

        composable(Screen.Enhance.route) {
            EnhanceScreen(
                imagePath = resultImagePath,
                isReplacingPage = editingPageId != null,
                onBackClick = navController::navigateUp,
                onRecropClick = navController::navigateUp,
                onAddPageClick = { enhancedImagePath, enhanceMode ->
                    val originalImagePath = previewImagePath
                    val perspectiveImagePath = resultImagePath
                    if (originalImagePath != null && perspectiveImagePath != null) {
                        val currentEditingPageId = editingPageId
                        if (currentEditingPageId == null) {
                            val page = ScanPage(
                                id = System.currentTimeMillis(),
                                originalImageUri = originalImagePath,
                                perspectiveImageUri = perspectiveImagePath,
                                processedImageUri = enhancedImagePath,
                                enhanceMode = enhanceMode,
                                createdAtMillis = System.currentTimeMillis()
                            )
                            scanDocument = scanDocument.copy(pages = scanDocument.pages + page)
                            selectedPageId = page.id
                        } else {
                            scanDocument = scanDocument.copy(
                                pages = scanDocument.pages.map { page ->
                                    if (page.id == currentEditingPageId) {
                                        page.replaceImages(
                                            originalImageUri = originalImagePath,
                                            perspectiveImageUri = perspectiveImagePath,
                                            processedImageUri = enhancedImagePath,
                                            enhanceMode = enhanceMode,
                                            updatedAtMillis = System.currentTimeMillis()
                                        )
                                    } else {
                                        page
                                    }
                                }
                            )
                            selectedPageId = currentEditingPageId
                        }
                        savedPdfPath = null
                        documentMessage = null
                        selectedPageShowOriginal = false
                        editingPageId = null
                        navController.navigate(Screen.DocumentPreview.route)
                    }
                }
            )
        }
}
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Camera : Screen("camera")
    data object Preview : Screen("preview")
    data object DocumentPreview : Screen("document_preview")
    data object RecentDocuments : Screen("recent_documents")
    data object Crop : Screen("crop")
    data object Enhance : Screen("enhance")
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
fun DocumentPreviewScreen(
    document: ScanDocument,
    selectedPageId: Long?,
    selectedPageShowOriginal: Boolean,
    message: String?,
    savedPdfPath: String?,
    onBackClick: () -> Unit,
    onAddPageClick: () -> Unit,
    onSelectPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePageUp: (Long) -> Unit,
    onMovePageDown: (Long) -> Unit,
    onShowOriginalPage: () -> Unit,
    onShowProcessedPage: () -> Unit,
    onRecropPage: (ScanPage) -> Unit,
    onReplaceWithCamera: (ScanPage) -> Unit,
    onReplaceWithGallery: (ScanPage) -> Unit,
    onUseOriginalForExport: (Long) -> Unit,
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
                selectedPageShowOriginal = selectedPageShowOriginal,
                onAddPageClick = onAddPageClick,
                onSavePdfClick = onSavePdfClick,
                onSharePdfClick = onSharePdfClick,
                onSelectPage = onSelectPage,
                onDeletePage = onDeletePage,
                onMovePageUp = onMovePageUp,
                onMovePageDown = onMovePageDown,
                onShowOriginalPage = onShowOriginalPage,
                onShowProcessedPage = onShowProcessedPage,
                onRecropPage = onRecropPage,
                onReplaceWithCamera = onReplaceWithCamera,
                onReplaceWithGallery = onReplaceWithGallery,
                onUseOriginalForExport = onUseOriginalForExport
            )
        }
    }
}

@Composable
fun RecentDocumentsScreen(
    records: List<DocumentRecord>,
    message: String?,
    pendingDeleteRecord: DocumentRecord?,
    onBackClick: () -> Unit,
    onOpenClick: (DocumentRecord) -> Unit,
    onShareClick: (DocumentRecord) -> Unit,
    onDeleteClick: (DocumentRecord) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (DocumentRecord) -> Unit
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
                text = "최근 문서",
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

        pendingDeleteRecord?.let { record ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF7ED))
                    .padding(12.dp)
            ) {
                BasicText(
                    text = "이 문서를 삭제할까요?",
                    style = TextStyle(
                        color = Color(0xFF111827),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicText(
                    text = record.title,
                    style = TextStyle(color = Color(0xFF4B5563), fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallActionButton(
                        text = "취소",
                        enabled = true,
                        onClick = onDismissDelete,
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0xFF6B7280)
                    )
                    SmallActionButton(
                        text = "삭제",
                        enabled = true,
                        onClick = { onConfirmDelete(record) },
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0xFFB91C1C)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (records.isEmpty()) {
            PlaceholderContent(
                title = "저장된 문서 없음",
                message = "PDF로 저장한 문서가 여기에 표시됩니다."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                records.forEach { record ->
                    RecentDocumentItem(
                        record = record,
                        onOpenClick = onOpenClick,
                        onShareClick = onShareClick,
                        onDeleteClick = onDeleteClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentDocumentItem(
    record: DocumentRecord,
    onOpenClick: (DocumentRecord) -> Unit,
    onShareClick: (DocumentRecord) -> Unit,
    onDeleteClick: (DocumentRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        BasicText(
            text = record.title,
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicText(
            text = "${record.createdAtText} · ${record.pageCount}페이지 · ${record.fileSizeText}",
            style = TextStyle(color = Color(0xFF4B5563), fontSize = 13.sp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(
                text = "열기",
                enabled = true,
                onClick = { onOpenClick(record) },
                modifier = Modifier.weight(1f)
            )
            SmallActionButton(
                text = "공유",
                enabled = true,
                onClick = { onShareClick(record) },
                modifier = Modifier.weight(1f)
            )
            SmallActionButton(
                text = "삭제",
                enabled = true,
                onClick = { onDeleteClick(record) },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFB91C1C)
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
    val context = LocalContext.current
    var qualityResult by remember(imagePath) { mutableStateOf<QualityResult?>(null) }

    LaunchedEffect(imagePath) {
        qualityResult = null
        imagePath?.let { path ->
            decodeBitmapFromUriString(context, path)?.let { bitmap ->
                try {
                    qualityResult = ImageQualityAnalyzer.analyze(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

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

            QualityWarnings(result = qualityResult)

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
fun EnhanceScreen(
    imagePath: String?,
    isReplacingPage: Boolean,
    onBackClick: () -> Unit,
    onRecropClick: () -> Unit,
    onAddPageClick: (String, EnhanceMode) -> Unit
) {
    val context = LocalContext.current
    val sourceBitmap = remember(imagePath) {
        imagePath?.let { decodeBitmapFromUriString(context, it) }
    }
    var selectedModeName by rememberSaveable(imagePath) { mutableStateOf(EnhanceMode.Original.name) }
    val selectedMode = runCatching { EnhanceMode.valueOf(selectedModeName) }
        .getOrDefault(EnhanceMode.Original)
    var enhancedPaths by remember(imagePath) {
        mutableStateOf<Map<EnhanceMode, String>>(
            imagePath?.let { mapOf(EnhanceMode.Original to it) } ?: emptyMap()
        )
    }
    var displayImagePath by remember(imagePath) { mutableStateOf(imagePath) }
    var errorMessage by remember(imagePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(imagePath, selectedMode) {
        if (imagePath == null || sourceBitmap == null) {
            displayImagePath = null
            return@LaunchedEffect
        }

        val cachedPath = enhancedPaths[selectedMode]
        if (cachedPath != null) {
            displayImagePath = cachedPath
            errorMessage = null
            return@LaunchedEffect
        }

        val enhancedUri = ImageEnhancer.enhanceToCache(
            context = context,
            sourceBitmap = sourceBitmap,
            mode = selectedMode
        )

        if (enhancedUri == null) {
            displayImagePath = null
            errorMessage = "이미지 보정에 실패했습니다. 다른 모드를 선택해주세요."
        } else {
            val enhancedPath = enhancedUri.toString()
            enhancedPaths = enhancedPaths + (selectedMode to enhancedPath)
            displayImagePath = enhancedPath
            errorMessage = null
        }
    }

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
                text = "보정",
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

        Spacer(modifier = Modifier.height(14.dp))

        if (imagePath == null || sourceBitmap == null) {
            PlaceholderContent(
                title = "이미지 없음",
                message = "보정할 이미지가 없습니다."
            )
        } else {
            if (errorMessage != null) {
                NoticeText(text = errorMessage.orEmpty())
                Spacer(modifier = Modifier.height(10.dp))
            }

            val currentDisplayPath = displayImagePath
            if (currentDisplayPath == null) {
                PlaceholderContent(
                    title = "보정 실패",
                    message = "다른 보정 모드를 선택해주세요."
                )
            } else {
                ImagePreviewArea(
                    imagePath = currentDisplayPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhanceModeButton(
                    text = "원본",
                    selected = selectedMode == EnhanceMode.Original,
                    onClick = { selectedModeName = EnhanceMode.Original.name },
                    modifier = Modifier.weight(1f)
                )
                EnhanceModeButton(
                    text = "문서",
                    selected = selectedMode == EnhanceMode.Document,
                    onClick = { selectedModeName = EnhanceMode.Document.name },
                    modifier = Modifier.weight(1f)
                )
                EnhanceModeButton(
                    text = "흑백",
                    selected = selectedMode == EnhanceMode.BlackWhite,
                    onClick = { selectedModeName = EnhanceMode.BlackWhite.name },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(text = "다시 자르기", onClick = onRecropClick)
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(
                text = if (isReplacingPage) "페이지 교체" else "페이지에 추가",
                onClick = {
                    val selectedPath = displayImagePath
                    if (selectedPath == null) {
                        errorMessage = "이미지 보정에 실패했습니다. 다른 모드를 선택해주세요."
                    } else {
                        onAddPageClick(selectedPath, selectedMode)
                    }
                }
            )
        }
    }
}

@Composable
private fun EnhanceModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallActionButton(
        text = text,
        enabled = true,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = if (selected) Color(0xFF2563EB) else Color(0xFF374151)
    )
}
@Composable
fun CropScreen(
    imagePath: String?,
    cropState: CropState,
    message: String?,
    onBackClick: () -> Unit,
    onSelectCorner: (CropCorner?) -> Unit,
    onUpdateCorner: (CropCorner, PointF) -> Unit,
    onFineTuneCorner: (Float, Float) -> Unit,
    onApplyClick: () -> Unit
) {
    val context = LocalContext.current
    var showDebugCorners by rememberSaveable { mutableStateOf(false) }
    var qualityResult by remember(imagePath) { mutableStateOf<QualityResult?>(null) }

    LaunchedEffect(imagePath) {
        qualityResult = null
        imagePath?.let { path ->
            decodeBitmapFromUriString(context, path)?.let { bitmap ->
                try {
                    qualityResult = ImageQualityAnalyzer.analyze(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBackClick)
            BasicText(
                text = "\uC790\uB974\uAE30/\uBCF4\uC815",
                style = TextStyle(
                    color = Color(0xFF111827),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 58.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (imagePath == null) {
            PlaceholderContent(
                title = "\uC774\uBBF8\uC9C0 \uC5C6\uC74C",
                message = "\uD14C\uB450\uB9AC\uB97C \uAC10\uC9C0\uD560 \uC774\uBBF8\uC9C0\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
            )
        } else {
            CropImageWithCorners(
                imagePath = imagePath,
                cropState = cropState,
                onSelectCorner = onSelectCorner,
                onUpdateCorner = onUpdateCorner,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            if (message != null) {
                NoticeText(text = message)
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
            QualityWarnings(result = qualityResult, compact = true)
            if (qualityResult?.warnings?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(6.dp))
            }


            if (cropState.selectedCorner != null) {
                FineTuneControls(
                    selectedCorner = cropState.selectedCorner,
                    onMove = onFineTuneCorner
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            ActionButton(
                text = "\uC801\uC6A9",
                onClick = onApplyClick
            )

            cropState.userCorners?.let { corners ->
                Spacer(modifier = Modifier.height(6.dp))
                SmallActionButton(
                    text = if (showDebugCorners) "좌표 숨기기" else "좌표 보기",
                    enabled = true,
                    onClick = { showDebugCorners = !showDebugCorners },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF6B7280)
                )
                if (showDebugCorners) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DebugCornerText(corners = corners)
                }
            }
        }
    }
}

@Composable
private fun CropImageWithCorners(
    imagePath: String,
    cropState: CropState,
    onSelectCorner: (CropCorner?) -> Unit,
    onUpdateCorner: (CropCorner, PointF) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap = remember(imagePath) {
        decodeBitmapFromUriString(context, imagePath)
    }
    var boxWidth by remember { mutableStateOf(0) }
    var boxHeight by remember { mutableStateOf(0) }
    var activeCorner by remember { mutableStateOf<CropCorner?>(null) }
    val latestCorners by rememberUpdatedState(cropState.userCorners)
    val latestMapper by rememberUpdatedState(
        if (bitmap != null && boxWidth > 0 && boxHeight > 0) {
            CropCoordinateMapper(
                bitmapWidth = bitmap.width.toFloat(),
                bitmapHeight = bitmap.height.toFloat(),
                containerWidth = boxWidth.toFloat(),
                containerHeight = boxHeight.toFloat()
            )
        } else {
            null
        }
    )
    val touchRadiusPx = with(density) { 56.dp.toPx() }

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
        return
    }

    val currentCorners = cropState.userCorners ?: insetFallbackCorners(bitmap.width, bitmap.height)
    val mapper = latestMapper

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .onSizeChanged { size ->
                boxWidth = size.width
                boxHeight = size.height
            }
            .pointerInput(bitmap.width, bitmap.height, touchRadiusPx) {
                detectDragGestures(
                    onDragStart = { touchPoint ->
                        val activeMapper = latestMapper ?: return@detectDragGestures
                        val activeCorners = latestCorners ?: return@detectDragGestures
                        activeCorner = findNearestCorner(
                            corners = activeCorners,
                            mapper = activeMapper,
                            touchPoint = touchPoint,
                            radiusPx = touchRadiusPx
                        )
                        onSelectCorner(activeCorner)
                        activeCorner?.let { corner ->
                            val point = activeCorners.pointFor(corner)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val corner = activeCorner
                        val activeMapper = latestMapper
                        val activeCorners = latestCorners
                        if (corner != null && activeMapper != null && activeCorners != null) {
                            val bitmapDelta = activeMapper.screenDeltaToBitmapDelta(
                                CropPoint(dragAmount.x, dragAmount.y)
                            )
                            val currentPoint = activeCorners.pointFor(corner)
                            val nextPoint = activeMapper.clampBitmapPoint(
                                CropPoint(
                                    x = currentPoint.x + bitmapDelta.x,
                                    y = currentPoint.y + bitmapDelta.y
                                )
                            )
                            onUpdateCorner(corner, PointF(nextPoint.x, nextPoint.y))
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        activeCorner?.let { corner ->
                            val point = latestCorners?.pointFor(corner)
                        }
                        activeCorner = null
                    },
                    onDragCancel = {
                        activeCorner = null
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "\uC120\uD0DD\uD55C \uC774\uBBF8\uC9C0",
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize()
        )

        if (mapper != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                fun mapPoint(point: PointF): Offset {
                    val screenPoint = mapper.bitmapToScreen(CropPoint(point.x, point.y))
                    return Offset(screenPoint.x, screenPoint.y)
                }

                val topLeft = mapPoint(currentCorners.topLeft)
                val topRight = mapPoint(currentCorners.topRight)
                val bottomRight = mapPoint(currentCorners.bottomRight)
                val bottomLeft = mapPoint(currentCorners.bottomLeft)
                val lineColor = Color(0xFFFFD400)
                val pointColor = Color(0xFFFF3B30)
                val selectedPointColor = Color(0xFF00E5FF)
                val lineWidth = 8f
                val pointRadius = 18f
                val selectedPointRadius = 25f

                drawLine(lineColor, topLeft, topRight, strokeWidth = lineWidth)
                drawLine(lineColor, topRight, bottomRight, strokeWidth = lineWidth)
                drawLine(lineColor, bottomRight, bottomLeft, strokeWidth = lineWidth)
                drawLine(lineColor, bottomLeft, topLeft, strokeWidth = lineWidth)

                listOf(
                    CropCorner.TopLeft to topLeft,
                    CropCorner.TopRight to topRight,
                    CropCorner.BottomRight to bottomRight,
                    CropCorner.BottomLeft to bottomLeft
                ).forEach { (corner, point) ->
                    val isSelected = corner == cropState.selectedCorner || corner == activeCorner
                    val radius = if (isSelected) selectedPointRadius else pointRadius
                    if (isSelected) {
                        val crosshairSize = 44f
                        drawLine(
                            Color.White,
                            Offset(point.x - crosshairSize, point.y),
                            Offset(point.x + crosshairSize, point.y),
                            strokeWidth = 3f
                        )
                        drawLine(
                            Color.White,
                            Offset(point.x, point.y - crosshairSize),
                            Offset(point.x, point.y + crosshairSize),
                            strokeWidth = 3f
                        )
                    }
                    drawCircle(Color.White, radius + 6f, point)
                    drawCircle(if (isSelected) selectedPointColor else pointColor, radius, point)
                    drawCircle(
                        color = Color.Black,
                        radius = radius + 6f,
                        center = point,
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FineTuneControls(
    selectedCorner: CropCorner,
    onMove: (Float, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = "\uC120\uD0DD\uB41C \uCF54\uB108: ${selectedCorner.logName}",
            style = TextStyle(
                color = Color(0xFF374151),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(text = "\u25B2", enabled = true, onClick = { onMove(0f, -3f) }, modifier = Modifier.weight(1f))
            SmallActionButton(text = "\u25BC", enabled = true, onClick = { onMove(0f, 3f) }, modifier = Modifier.weight(1f))
            SmallActionButton(text = "\u25C0", enabled = true, onClick = { onMove(-3f, 0f) }, modifier = Modifier.weight(1f))
            SmallActionButton(text = "\u25B6", enabled = true, onClick = { onMove(3f, 0f) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DebugCornerText(corners: DocumentCorners) {
    BasicText(
        text = "topLeft(${corners.topLeft.x.toInt()}, ${corners.topLeft.y.toInt()})  " +
            "topRight(${corners.topRight.x.toInt()}, ${corners.topRight.y.toInt()})\n" +
            "bottomRight(${corners.bottomRight.x.toInt()}, ${corners.bottomRight.y.toInt()})  " +
            "bottomLeft(${corners.bottomLeft.x.toInt()}, ${corners.bottomLeft.y.toInt()})",
        style = TextStyle(
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun findNearestCorner(
    corners: DocumentCorners,
    mapper: CropCoordinateMapper,
    touchPoint: Offset,
    radiusPx: Float
): CropCorner? {
    return CropCorner.entries
        .map { corner ->
            val point = corners.pointFor(corner)
            val screenPoint = mapper.bitmapToScreen(CropPoint(point.x, point.y))
            val distance = hypot(
                (screenPoint.x - touchPoint.x).toDouble(),
                (screenPoint.y - touchPoint.y).toDouble()
            ).toFloat()
            corner to distance
        }
        .filter { (_, distance) -> distance <= radiusPx }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}
@Composable
private fun DocumentPageManager(
    document: ScanDocument,
    selectedPageId: Long?,
    selectedPageShowOriginal: Boolean,
    onAddPageClick: () -> Unit,
    onSavePdfClick: () -> Unit,
    onSharePdfClick: () -> Unit,
    onSelectPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onMovePageUp: (Long) -> Unit,
    onMovePageDown: (Long) -> Unit,
    onShowOriginalPage: () -> Unit,
    onShowProcessedPage: () -> Unit,
    onRecropPage: (ScanPage) -> Unit,
    onReplaceWithCamera: (ScanPage) -> Unit,
    onReplaceWithGallery: (ScanPage) -> Unit,
    onUseOriginalForExport: (Long) -> Unit
) {
    val selectedPage = document.pages.firstOrNull { it.id == selectedPageId }
        ?: document.pages.first()
    val selectedImagePath = if (selectedPageShowOriginal) {
        selectedPage.originalImageUri
    } else {
        selectedPage.processedPreviewImageUri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ImagePreviewArea(
            imagePath = selectedImagePath,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(
                text = "원본 보기",
                enabled = true,
                onClick = onShowOriginalPage,
                modifier = Modifier.weight(1f),
                backgroundColor = if (selectedPageShowOriginal) Color(0xFF2563EB) else Color(0xFF374151)
            )
            SmallActionButton(
                text = "보정본 보기",
                enabled = true,
                onClick = onShowProcessedPage,
                modifier = Modifier.weight(1f),
                backgroundColor = if (!selectedPageShowOriginal) Color(0xFF2563EB) else Color(0xFF374151)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(
                text = "다시 자르기",
                enabled = true,
                onClick = { onRecropPage(selectedPage) },
                modifier = Modifier.weight(1f)
            )
            SmallActionButton(
                text = "원본으로 되돌리기",
                enabled = true,
                onClick = { onUseOriginalForExport(selectedPage.id) },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF6B7280)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallActionButton(
                text = "재촬영",
                enabled = true,
                onClick = { onReplaceWithCamera(selectedPage) },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF2563EB)
            )
            SmallActionButton(
                text = "갤러리에서 교체",
                enabled = true,
                onClick = { onReplaceWithGallery(selectedPage) },
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF2563EB)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        BasicText(
            text = "PDF 출력: " + if (selectedPage.useOriginalForExport) "원본" else "보정본",
            style = TextStyle(
                color = Color(0xFF4B5563),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
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
                imagePath = page.defaultPreviewImageUri,
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
    val context = LocalContext.current
    val bitmap = remember(imagePath) {
        decodeBitmapFromUriString(context, imagePath)
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
private fun QualityWarnings(
    result: QualityResult?,
    compact: Boolean = false
) {
    val warnings = result?.warnings.orEmpty()
    if (warnings.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        warnings.forEach { warning ->
            NoticeText(text = warning)
        }
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

private fun decodeBitmapFromUriString(
    context: Context,
    uriString: String
): android.graphics.Bitmap? {
    val uri = Uri.parse(uriString)
    return if (uri.scheme == "file") {
        BitmapFactory.decodeFile(uri.path)
    } else {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }
}


























