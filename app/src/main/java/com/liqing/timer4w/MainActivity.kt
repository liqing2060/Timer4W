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
    var start by remember { mutableStateOf(false) }
    var timeElapsed by remember { mutableStateOf(0.0) } // Use double for higher precision
    val scope = rememberCoroutineScope()
    var startTime by remember { mutableStateOf(0L) }
    var job: Job? by remember { mutableStateOf(null) }

    // Handle lifecycle to cancel coroutine when the app is not in the foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                job?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Format the time elapsed to two decimal places
    Text(
        text = "${"%.2f".format(timeElapsed)} s",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp
        )
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            start = !start
            if (start) {
                // Save the start time
                startTime = System.currentTimeMillis()
                // Start the timer
                job = scope.launch(Dispatchers.Default) {
                    while (isActive) {
                        // Calculate elapsed time
                        val now = System.currentTimeMillis()
                        timeElapsed = (now - startTime) / 1000.0 // Convert to seconds
                        delay(10) // Smaller delay for higher precision
                    }
                }
            } else {
                // Stop the timer
                job?.cancel()
            }
        },
        modifier = Modifier.size(
            width = 240.dp,
            height = 100.dp
        )
    )
    {
        Text(
            text = if (start) "Stop" else "Start",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp
            ),

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