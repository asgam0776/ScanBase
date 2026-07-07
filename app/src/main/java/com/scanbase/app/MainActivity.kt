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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    val navController = rememberNavController()
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var homeMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(importedImagePath) {
        importedImagePath?.let { imagePath ->
            previewImagePath = imagePath
            onImportedImageHandled()
            navController.navigate(Screen.DocumentPreview.route)
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
                    navController.navigate(Screen.DocumentPreview.route)
                }
            )
        }
        composable(Screen.GalleryImport.route) {
            GalleryImportScreen(onBackClick = navController::navigateUp)
        }
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(
                imagePath = previewImagePath,
                onBackClick = navController::navigateUp
            )
        }
    }
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Camera : Screen("camera")
    data object GalleryImport : Screen("gallery_import")
    data object DocumentPreview : Screen("document_preview")
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
    imagePath: String?,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        BackButton(onClick = onBackClick)

        if (imagePath == null) {
            PlaceholderContent(
                title = "\uCD5C\uADFC \uBB38\uC11C",
                message = "\uC544\uC9C1 \uD45C\uC2DC\uD560 \uC774\uBBF8\uC9C0\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
            )
        } else {
            ImagePreview(imagePath = imagePath)
        }
    }
}

@Composable
private fun ImagePreview(imagePath: String) {
    val bitmap = remember(imagePath) {
        BitmapFactory.decodeFile(imagePath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = "\uBBF8\uB9AC\uBCF4\uAE30",
            style = TextStyle(
                color = Color(0xFF111827),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (bitmap == null) {
            BasicText(
                text = "\uC774\uBBF8\uC9C0\uB97C \uBD88\uB7EC\uC62C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.",
                style = TextStyle(
                    color = Color(0xFFB91C1C),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "\uC120\uD0DD\uD55C \uC774\uBBF8\uC9C0",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            )
        }
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
