package com.liqing.timer4w

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
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
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import androidx.camera.core.Preview as CameraXPreview
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在这里加载OpenCV库
        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully!")
        }
        val sharedPreferences = getSharedPreferences("Timer4W", Context.MODE_PRIVATE)
        setContent {
            Timer4WTheme {
                AppContent(sharedPreferences)
            }
        }
    }
}

@Composable
fun AppContent(
    sharedPreferences: SharedPreferences?,
    model: Model = Model(rememberCoroutineScope()),
    cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CameraPreview(
                model,
                cameraExecutor,
                modifier = Modifier
                    .height(320.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            LapInfo(model)

            Spacer(modifier = Modifier.height(20.dp))

            Buttons(model, sharedPreferences)

            Spacer(modifier = Modifier.height(100.dp))

            Text(
                text = model.debugInfo(),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            )
        }
    }
}

@Composable
fun CameraPreview(model: Model, cameraExecutor: ExecutorService, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissionState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    Box(modifier = modifier) {
        if (!model.isStopped()) {

            // 创建一个启动器用于请求权限
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    permissionState.value = isGranted
                }
            )

            // 检查权限并可能请求权限
            LaunchedEffect(Unit) {
                if (!permissionState.value) {
                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }

            if (permissionState.value) {
                if (model.timerState.value == Model.TimerState.START_CAMERA) {
                    model.onCameraPreviewStart()
                }

                // 权限被授予，显示相机预览
                AndroidView(factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                            val preview = CameraXPreview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        // 使用OpenCV进行图像分析，检测四驱车
                                        model.analysis(imageProxy)
                                        imageProxy.close() // 完成分析后必须关闭imageProxy
                                    }
                                }

                            try {
                                // 默认选择后置相机
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll() // 在绑定之前解除绑定
                                cameraProvider.bindToLifecycle(
                                    context as LifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }, modifier = Modifier.fillMaxSize()) // 确保预览填满Box
            }
        }

        if (model.isStopped() || !permissionState.value) {
            var tipString = "Camera Preview"
            var fontSize = 20.sp
            // 如果有圈数数据，展示详细的信息
            if (model.lapCount.value > 0) {
                fontSize = 16.sp
                tipString = "Lap Details"
                for (lap in model.lapTimes) {
                    tipString += "\nLap ${lap.lapIndex}: ${"%.2f".format(lap.lapTime)} s" + " Speed: ${"%.2f".format(lap.speed)}" + " Diff: ${"%.2f".format(lap.diff)}"
                }
                Column(modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)) {
                    Text(
                        text = tipString,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize)
                    )
                }
            } else {
                // 权限未被授予或者相机没有开始，显示文本提示
                Text(
                    text = tipString,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun LapInfo(model: Model) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lap Count: ${model.lapCount.value}" + if (model.targetLapCount.value > 0) "/${model.targetLapCount.value}" else "",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
        Text(
            text = "Total Time: ${"%.2f".format(model.getTimeElapsed())} s",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
        Text(
            text = "Current Lap: ${"%.2f".format(model.getCurLapTimeElapsed())} s",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
        Text(
            text = "Average Lap: ${"%.2f".format(model.averageLap())} s",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
        Text(
            text = "Fastest Lap: ${"%.2f".format(model.fastestLap())} s",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
    }
}

@Composable
fun Buttons(model: Model, sharedPreferences: SharedPreferences?) {

    // 显示状态
    val tipText: String = when (model.timerState.value) {
        Model.TimerState.START_CAMERA -> {
            "Wait for the camera to start."
        }

        Model.TimerState.WAIT_FOR_CAR_FIRST_PASS -> {
            "Wait for the car."
        }

        Model.TimerState.RUNNING -> {
            "Recording..."
        }

        else -> {
            "Press start to record."
        }
    }
    Text(
        text = tipText,
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
    )

    Spacer(modifier = Modifier.height(20.dp))

    // 开始/停止按钮
    Button(
        onClick = {
            if (model.isStopped()) {
                model.start()
            } else {
                model.stop()
            }
        },
        modifier = Modifier.size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = if (model.isStopped()) "Start" else "Stop",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 目标圈数
    val lapOptions = List(11) { it * 10 }
    // 读取保存的索引，如果不存在则默认为0（对应0圈）
    val savedIndex = sharedPreferences?.getInt("targetLapCountIndex", 0) ?: 0
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(savedIndex) }

    // 设置model中的targetLapCount为当前选择的圈数
    LaunchedEffect(selectedIndex) {
        model.targetLapCount.value = lapOptions[selectedIndex]
        // 保存当前选择的索引到本地
        sharedPreferences?.edit()?.putInt("targetLapCountIndex", selectedIndex)?.apply()
    }

    // UI 组件，包括下拉菜单等
    Column {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }) {
            lapOptions.forEachIndexed { index, lapCount ->
                DropdownMenuItem(
                    onClick = {
                        selectedIndex = index
                        expanded = false
                    }, text = {
                        Text("$lapCount Laps")
                    }
                )
            }
        }
    }

    Button(onClick = { expanded = true })
    {
        Text("Select Lap: ${lapOptions[selectedIndex]}")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Timer4WTheme {
        AppContent(null)
    }
}