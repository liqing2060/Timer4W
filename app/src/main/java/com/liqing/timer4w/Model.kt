package com.liqing.timer4w

import CarAnalyzer
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope

class Model(scope: CoroutineScope) : ViewModel() {
    enum class TimerState {
        START_CAMERA, WAIT_FOR_CAR_FIRST_PASS, RUNNING, STOPPED
    }

    class LapInfo {
        var lapIndex = 0
        var lapTime = 0.0
        var speed = 0.0
        var diff = 0.0
    }

    val timerState = mutableStateOf(TimerState.STOPPED)

    private var timeElapsed = mutableStateOf(0.0)
    // 创建TimerLogic实例
    private val timerLogic = TimerLogic(update = { time ->
        timeElapsed.value = time
    }, coroutineScope = scope)

    private val carAnalyzer = CarAnalyzer(diffThreshold = 0.5, onCarDetected = {
        onCarPass()
    })

    private val lastCarPassElapsed = mutableStateOf(0.0)
    val targetLapCount = mutableStateOf(0)
    val lapCount = mutableStateOf(0)
    val lastLapTime = mutableStateOf(0.0)
    val lapTimes = mutableListOf<LapInfo>()
    val minLapInterval = mutableStateOf(0.8)

    val fontSize = mutableStateOf(20.sp)
    val space = mutableStateOf(20.dp)
    val scale = mutableStateOf(1.0)

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
        } else {
            if (timeElapsed.value - lastCarPassElapsed.value > minLapInterval.value) {
                recordLap()
            } else {
                // Ignore the car pass event if it happens within 1 second
            }
        }
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
        }
    }

    fun fastestLap(): Double? {
        return if (lapTimes.size == 0) 0.0 else lapTimes.minByOrNull { it.lapTime }?.lapTime
    }

    fun averageLap(): Double {
        return if (lapTimes.size == 0) 0.0 else lapTimes.map { it.lapTime }.average()
    }

    fun stop() {
        timerState.value = TimerState.STOPPED
        timerLogic.stop()
    }

    private fun resetData() {
        timeElapsed.value = 0.0
        lastLapTime.value = 0.0
        lastCarPassElapsed.value = 0.0
        lapCount.value = 0
        lapTimes.clear()
        carAnalyzer.reset()
    }

    fun debugInfo() : String {
        return "speed:" + "%.2f".format(carAnalyzer.speed.doubleValue) + " diff:" + "%.2f".format(carAnalyzer.backgroundDiff.doubleValue)
    }
}