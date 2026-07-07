package com.scanbase.app.camera

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.scanbase.app.BackButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

@Composable
fun CameraCaptureScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onBackClick: () -> Unit,
    onImageCaptured: (String) -> Unit
) {
    if (!hasCameraPermission) {
        CameraPermissionScreen(
            onBackClick = onBackClick,
            onRequestCameraPermission = onRequestCameraPermission
        )
        return
    }

    CameraPreviewContent(
        onBackClick = onBackClick,
        onImageCaptured = onImageCaptured
    )
}

@Composable
private fun CameraPermissionScreen(
    onBackClick: () -> Unit,
    onRequestCameraPermission: () -> Unit
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
                text = "카메라 권한 필요",
                style = TextStyle(
                    color = Color(0xFF111827),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = "문서를 촬영하려면 카메라 권한을 허용해 주세요.",
                style = TextStyle(
                    color = Color(0xFF4B5563),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            TextButton(
                text = "권한 요청",
                backgroundColor = Color(0xFF2563EB),
                textColor = Color.White,
                onClick = onRequestCameraPermission
            )
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onBackClick: () -> Unit,
    onImageCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewView) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            imageCapture = capture
        } catch (_: Exception) {
            errorMessage = "카메라를 시작할 수 없습니다."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraTopButton(text = "< 뒤로", onClick = onBackClick)
                CameraTopButton(
                    text = if (isFlashOn) "플래시 ON" else "플래시 OFF",
                    onClick = {
                        val nextFlashState = !isFlashOn
                        camera?.cameraControl?.enableTorch(nextFlashState)
                        isFlashOn = nextFlashState
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                errorMessage?.let { message ->
                    BasicText(
                        text = message,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCCB91C1C))
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraBottomButton(text = "갤러리", onClick = {})

                    CaptureButton(
                        onClick = {
                            val capture = imageCapture
                            if (capture == null) {
                                errorMessage = "카메라 준비 중입니다."
                            } else {
                                takePhoto(
                                    context = context,
                                    imageCapture = capture,
                                    executor = mainExecutor,
                                    onSuccess = {
                                        errorMessage = null
                                        onImageCaptured(it)
                                    },
                                    onError = {
                                        errorMessage = "촬영에 실패했습니다."
                                    }
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(68.dp))
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onSuccess: (String) -> Unit,
    onError: () -> Unit
) {
    val imageFile = createCacheImageFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSuccess(imageFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError()
            }
        }
    )
}

private fun createCacheImageFile(context: Context): File {
    val directory = File(context.cacheDir, "captures").apply {
        mkdirs()
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        .format(System.currentTimeMillis())
    return File(directory, "scan_$timestamp.jpg")
}

@Composable
private fun CaptureButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFFE5E7EB))
        )
    }
}

@Composable
private fun CameraTopButton(
    text: String,
    onClick: () -> Unit
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun CameraBottomButton(
    text: String,
    onClick: () -> Unit
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun TextButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    )
}
