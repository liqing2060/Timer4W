package com.liqing.timer4w

import kotlinx.coroutines.*

class TimerLogic(private val update: (Double) -> Unit, private val coroutineScope: CoroutineScope) {
    private var job: Job? = null
    private var startTime = 0L

    fun start() {
        if (job == null || job?.isActive != true) {
            startTime = System.currentTimeMillis()
            job = coroutineScope.launch(Dispatchers.Default) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val elapsed = (now - startTime) / 1000.0
                    update(elapsed)
                    delay(10)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
