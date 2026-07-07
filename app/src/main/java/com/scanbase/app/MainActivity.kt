package com.scanbase.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanbase.app.camera.CameraCaptureScreen

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCameraPermission = isCameraPermissionGranted()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    ScanBaseApp(
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = ::requestCameraPermission
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
}

@Composable
fun ScanBaseApp(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit
) {
    val navController = rememberNavController()
    var capturedImagePath by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNewScanClick = { navController.navigate(Screen.Camera.route) },
                onGalleryClick = { navController.navigate(Screen.GalleryImport.route) },
                onRecentDocumentsClick = { navController.navigate(Screen.DocumentPreview.route) }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                onBackClick = navController::navigateUp,
                onImageCaptured = { imagePath ->
                    capturedImagePath = imagePath
                    navController.navigate(Screen.DocumentPreview.route)
                }
            )
        }
        composable(Screen.GalleryImport.route) {
            GalleryImportScreen(onBackClick = navController::navigateUp)
        }
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(
                imagePath = capturedImagePath,
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

        Spacer(modifier = Modifier.height(40.dp))

        HomeButton(text = "새 스캔", onClick = onNewScanClick)
        Spacer(modifier = Modifier.height(14.dp))
        HomeButton(text = "갤러리에서 가져오기", onClick = onGalleryClick)
        Spacer(modifier = Modifier.height(14.dp))
        HomeButton(text = "최근 문서", onClick = onRecentDocumentsClick)
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
        title = "갤러리에서 가져오기",
        message = "갤러리 가져오기는 다음 단계에서 구현합니다.",
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
                title = "최근 문서",
                message = "아직 촬영한 문서가 없습니다."
            )
        } else {
            CapturedImagePreview(imagePath = imagePath)
        }
    }
}

@Composable
private fun CapturedImagePreview(imagePath: String) {
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
            text = "촬영 이미지",
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
                text = "이미지를 불러올 수 없습니다.",
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
                contentDescription = "촬영한 문서 이미지",
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
            text = "뒤로",
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
