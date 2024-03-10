package com.liqing.timer4w

import CarAnalyzer
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope

class Model(scope: CoroutineScope, private var context: Context) : ViewModel() {
    enum class TimerState {
        START_CAMERA, WAIT_FOR_CAR_FIRST_PASS, RUNNING, STOP, STOPPED
    }

    class LapInfo {
        var lapIndex = 0
        var lapTime = 0.0
        var speed = 0.0
        var diff = 0.0
    }

    class ImageAnalysisInfo {
        var lapIndex = 0
        var lapTime = 0.0
        var speed = 0.0
        var diff = 0.0
        var carPassTimeFrame = false
        var totalTimeElapsed = 0.0
    }

    val timerState = mutableStateOf(TimerState.STOPPED)

    private var timeElapsed = mutableStateOf(0.0)

    // 创建TimerLogic实例
    private val timerLogic = TimerLogic(update = { time ->
        timeElapsed.value = time
    }, coroutineScope = scope)

    private val carAnalyzer = CarAnalyzer(
        diffThreshold = 0.5,
        onCarDetected = {
            onCarPass()
        },
        onImageAnalysis = {
            onImageAnalysis()
        }
    )

    private val lastCarPassElapsed = mutableStateOf(0.0)
    val targetLapCount = mutableStateOf(0)
    val lapCount = mutableStateOf(0)
    val lastLapTime = mutableStateOf(0.0)
    val lapTimes = mutableListOf<LapInfo>()
    val minLapInterval = mutableStateOf(0.8)
    val imageAnalysisInfos = mutableListOf<ImageAnalysisInfo>()

    val fontSize = mutableStateOf(20.sp)
    val space = mutableStateOf(20.dp)
    val scale = mutableStateOf(1.0)

    private lateinit var soundPool: SoundPool
    private var soundIdPass: Int = 0
    private var soundIdStart: Int = 0
    private var soundIdEnd: Int = 0

    fun init() {

        // 配置SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        // 加载音效
        soundIdPass = soundPool.load(context, R.raw.pass2, 1)
        soundIdStart = soundPool.load(context, R.raw.start, 1)
        soundIdEnd = soundPool.load(context, R.raw.end, 1)
    }

    fun getTimeElapsed(): Double = timeElapsed.value

    fun getCurLapTimeElapsed(): Double = timeElapsed.value - lastLapTime.value

    fun isStopped() = timerState.value == TimerState.STOPPED

    fun start() {
        resetData()
        timerState.value = TimerState.START_CAMERA
    }

    fun onCameraPreviewStart() {
        resetData()
        timerState.value = TimerState.WAIT_FOR_CAR_FIRST_PASS
    }

    fun analysis(imageProxy: ImageProxy) {
//        Log.d("Model", "Analysis")
        carAnalyzer.analyze(imageProxy)
    }

    private fun onCarPass() {
        if (timerState.value == TimerState.WAIT_FOR_CAR_FIRST_PASS) {
            timeElapsed.value = 0.0
            timerState.value = TimerState.RUNNING
            timerLogic.start()
            Log.d("Model", "Car pass, start timer")
            playSound(soundIdStart)
        } else {
            if (timeElapsed.value - lastCarPassElapsed.value > minLapInterval.value) {
                recordLap()
            } else {
                // Ignore the car pass event if it happens within 1 second
            }
        }
    }

    private fun onImageAnalysis() {
        if (timerState.value != TimerState.RUNNING) {
            return
        }
        val info = ImageAnalysisInfo().apply {
            lapIndex = lapCount.value
            lapTime = getCurLapTimeElapsed()
            speed = carAnalyzer.speed.doubleValue
            diff = carAnalyzer.backgroundDiff.doubleValue
            carPassTimeFrame = carAnalyzer.carPassedThisFrame.value
            totalTimeElapsed = timeElapsed.value
        }
        imageAnalysisInfos.add(info)
//        Log.d("Model", "Image analysis: $info")
    }

    private fun recordLap() {
        lastCarPassElapsed.value = timeElapsed.value
        val curLapTime = timeElapsed.value - lastLapTime.value
        lastLapTime.value = timeElapsed.value
        lapTimes.add(LapInfo().apply {
            lapIndex = lapCount.value
            lapTime = curLapTime
            speed = carAnalyzer.speed.doubleValue
            diff = carAnalyzer.backgroundDiff.doubleValue
        })
        lapCount.value = lapTimes.size

        Log.d("Model", "Lap $lapCount: ${"%.2f".format(curLapTime)}s")

        if (targetLapCount.value > 0 && lapCount.value >= targetLapCount.value) {
            Log.d("Model", "Target lap count reached, stop record.")
            stop()
        } else {
            playSound(soundIdPass)
        }
    }

    fun fastestLap(): Double? {
        return if (lapTimes.size == 0) 0.0 else lapTimes.minByOrNull { it.lapTime }?.lapTime
    }

    fun averageLap(): Double {
        return if (lapTimes.size == 0) 0.0 else lapTimes.map { it.lapTime }.average()
    }

    fun stop() {
        timerState.value = TimerState.STOP
        timerLogic.stop()
        playSound(soundIdEnd)
    }

    fun onStopFinish() {
        timerState.value = TimerState.STOPPED
    }

    private fun resetData() {
        timeElapsed.value = 0.0
        lastLapTime.value = 0.0
        lastCarPassElapsed.value = 0.0
        lapCount.value = 0
        lapTimes.clear()
        carAnalyzer.reset()
        imageAnalysisInfos.clear()
    }

    fun debugInfo(): String {
        return "speed:" + "%.2f".format(carAnalyzer.speed.doubleValue) + " diff:" + "%.2f".format(
            carAnalyzer.backgroundDiff.doubleValue
        )
    }

    fun playSound(soundId: Int) {
        soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f)
    }
}