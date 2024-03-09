package com.liqing.timer4w

import CarAnalyzer
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope

class Model(scope: CoroutineScope) : ViewModel() {
    enum class TimerState {
        START_CAMERA, WAIT_FOR_CAR_FIRST_PASS, RUNNING, STOPPED
    }

    val timerState = mutableStateOf(TimerState.STOPPED)

    private var timeElapsed = mutableStateOf(0.0)
    // 创建TimerLogic实例
    private val timerLogic = TimerLogic(update = { time ->
        timeElapsed.value = time
    }, coroutineScope = scope)

    private val carAnalyzer = CarAnalyzer(diffThreshold = 10.0, onCarDetected = {
        onCarPass()
    })
    private val analysisSkippedFrameCount = mutableStateOf(0)

    private val lastCarPassElapsed = mutableStateOf(0.0)
    val lapCount = mutableStateOf(0)
    val targetLapCount = mutableStateOf(0)
    private val lastLapTime = mutableStateOf(0.0)
    val lapTimes = mutableListOf<Double>()

    fun getTimeElapsed(): Double = timeElapsed.value

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
        analysisSkippedFrameCount.value += 1
        if (analysisSkippedFrameCount.value >= 10) {
            carAnalyzer.analyze(imageProxy)
        }
    }

    private fun onCarPass() {
        if (timerState.value == TimerState.WAIT_FOR_CAR_FIRST_PASS) {
            timeElapsed.value = 0.0
            timerState.value = TimerState.RUNNING
            timerLogic.start()
            Log.d("Model", "Car pass, start timer")
        } else {
            if (timeElapsed.value - lastCarPassElapsed.value > 1) {
                recordLap()
            } else {
                // Ignore the car pass event if it happens within 1 second
            }
        }
    }

    private fun recordLap() {
        lastCarPassElapsed.value = timeElapsed.value
        val lapTime = timeElapsed.value - lastLapTime.value
        lastLapTime.value = timeElapsed.value
        lapTimes.add(lapTime)
        lapCount.value = lapTimes.size

        Log.d("Model", "Lap $lapCount: ${"%.2f".format(lapTime)}s")

        if (targetLapCount.value > 0 && lapCount.value >= targetLapCount.value) {
            Log.d("Model", "Target lap count reached, stop record.")
            stop()
        }
    }

    fun fastestLap(): Double? {
        return lapTimes.minOrNull()
    }

    fun averageLap(): Double {
        return lapTimes.average()
    }

    fun stop() {
        timeElapsed.value = 0.0
        timerState.value = TimerState.STOPPED
        timerLogic.stop()
    }

    private fun resetData() {
        timeElapsed.value = 0.0
        lastCarPassElapsed.value = 0.0
        lapCount.value = 0
        lapTimes.clear()
        analysisSkippedFrameCount.value = 0
        carAnalyzer.reset()
    }

    fun debugInfo() : String {
        return "diff:" + "%.2f".format(carAnalyzer.curDiff.doubleValue) // "%.2f".format(carAnalyzer.curDiff)
    }
}