package com.liqing.timer4w

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope

class Model(private val scope: CoroutineScope) : ViewModel() {
    enum class TimerState {
        START_CAMERA, WAIT_FOR_CAR_FIRST_PASS, RUNNING, STOPPED
    }

    val timerState = mutableStateOf(TimerState.STOPPED)
    private var timeElapsed = mutableStateOf(0.0)
    // 创建TimerLogic实例
    private val timerLogic = TimerLogic(update = { time ->
        timeElapsed.value = time
    }, coroutineScope = scope)

    fun getTimeElapsed(): Double = timeElapsed.value

    fun isStopped() = timerState.value == TimerState.STOPPED

    fun start() {
        timeElapsed.value = 0.0
        timerState.value = TimerState.START_CAMERA
    }

    fun onCameraPreviewStart() {
        timeElapsed.value = 0.0
        timerState.value = TimerState.WAIT_FOR_CAR_FIRST_PASS
    }

    fun onCarPass() {
        if (timerState.value == TimerState.WAIT_FOR_CAR_FIRST_PASS) {
            timeElapsed.value = 0.0
            timerState.value = TimerState.RUNNING
            timerLogic.start()
        }
    }

    fun stop() {
        timeElapsed.value = 0.0
        timerState.value = TimerState.STOPPED
        timerLogic.stop()
    }
}