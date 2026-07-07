package com.scanbase.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            ComposeView(this).apply {
                setContent {
                    ScanBaseApp()
                }
            }
        )
    }
}

@Composable
fun ScanBaseApp() {
    val navController = rememberNavController()

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
            CameraScreen(onBackClick = navController::navigateUp)
        }
        composable(Screen.GalleryImport.route) {
            GalleryImportScreen(onBackClick = navController::navigateUp)
        }
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(onBackClick = navController::navigateUp)
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
fun CameraScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(
        title = "새 스캔",
        message = "카메라 화면은 다음 단계에서 구현합니다.",
        onBackClick = onBackClick
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
fun DocumentPreviewScreen(onBackClick: () -> Unit) {
    PlaceholderScreen(
        title = "최근 문서",
        message = "문서 미리보기는 다음 단계에서 구현합니다.",
        onBackClick = onBackClick
    )
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
}

@Composable
private fun BackButton(onClick: () -> Unit) {
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
