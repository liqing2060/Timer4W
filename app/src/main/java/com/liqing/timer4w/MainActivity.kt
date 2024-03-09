package com.liqing.timer4w

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.liqing.timer4w.ui.theme.Timer4WTheme
import androidx.camera.core.Preview as CameraXPreview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Timer4WTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent(model: Model = Model()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CameraPreview(model, modifier = Modifier.weight(1f).height(200.dp).fillMaxWidth())
            Timer(model);
        }
    }
}

@Composable
fun CameraPreview(model: Model, modifier: Modifier = Modifier) {
    val startCamera by model.startTimer
    val context = LocalContext.current
    val permissionState = remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

    // 创建一个启动器用于请求权限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                permissionState.value = true
            } else {
                permissionState.value = false
                // 处理未授权的情况
            }
        }
    )

    // 检查权限并可能请求权限
    LaunchedEffect(Unit) {
        if (!permissionState.value) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (startCamera && permissionState.value) {
            // 权限被授予，显示相机预览
            AndroidView(factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                        val preview = CameraXPreview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        try {
                            // 默认选择后置相机
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll() // 在绑定之前解除绑定
                            cameraProvider.bindToLifecycle(context as LifecycleOwner, cameraSelector, preview)
                        } catch(e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            }, modifier = Modifier.fillMaxSize()) // 确保预览填满Box
        } else {
            // 权限未被授予或者相机没有开始，显示文本提示
            Text(
                text = if (!permissionState.value) "No camera permission yet" else "Press start to record.",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun Timer(model: Model) {
    val startCamera by model.startTimer
    var timeElapsed by remember { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    // 创建TimerLogic实例
    val timerLogic = remember { TimerLogic(update = { time ->
        timeElapsed = time
    }, coroutineScope = scope) }

    Spacer(modifier = Modifier.height(24.dp))

    // 显示计时器时间
    Text(
        text = "${"%.2f".format(timeElapsed)} s",
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    // 开始/停止按钮
    Button(
        onClick = {
            model.toggleTimer()
            if (startCamera) {
                timerLogic.start()
            } else {
                timerLogic.stop()
            }
        },
        modifier = Modifier.size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = if (startCamera) "Stop" else "Start",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
    }

    Spacer(modifier = Modifier.height(150.dp))
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Timer4WTheme {
        AppContent()
    }
}