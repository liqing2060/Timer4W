package com.liqing.timer4w

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.liqing.timer4w.ui.theme.Timer4WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
fun AppContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CameraPreview(modifier = Modifier.weight(1f))
//            StartButton()
//            TimerDisplay()
            Timer();
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    // Placeholder for camera preview
    // You should integrate the actual camera preview here
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp), contentAlignment = Alignment.Center
    ) {
        Text(text = "Camera Preview Placeholder")
    }
}

@Composable
fun StartButton() {
    // Example start button
    Button(onClick = { /* Implement start/stop logic */ }) {
        Text(text = "Start")
    }
}

@Composable
fun TimerDisplay() {
    var timeElapsed by remember { mutableStateOf("0:00") }

    // You can update `timeElapsed` based on your timing logic
    Text(text = "Time Elapsed: $timeElapsed")
}

@Composable
fun Timer() {
    var timeElapsed by remember { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    // 创建TimerLogic实例
    val timerLogic = remember { TimerLogic(update = { time ->
        timeElapsed = time
    }, coroutineScope = scope) }

    var start by remember { mutableStateOf(false) }

    // 显示计时器时间
    Text(
        text = "${"%.2f".format(timeElapsed)} s",
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    // 开始/停止按钮
    Button(
        onClick = {
            start = !start
            if (start) {
                timerLogic.start()
            } else {
                timerLogic.stop()
            }
        },
        modifier = Modifier.size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = if (start) "Stop" else "Start",
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