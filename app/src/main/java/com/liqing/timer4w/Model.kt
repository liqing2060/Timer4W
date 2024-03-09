package com.liqing.timer4w

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class Model : ViewModel() {
    val startTimer = mutableStateOf(false)
    val startCamera = mutableStateOf(false)

    fun toggleTimer() {
        startTimer.value = !startTimer.value
        if (!startTimer.value) {
            startCamera.value = false
        }
    }
}