package co.novolytics.noisemeter

import android.os.Handler
import android.os.Looper

class Decibel(listener: OnDecibelListener) {

    interface OnDecibelListener {
        fun onDecibel(duration: String)
    }
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private var duration = 0L
    private var delay = 100L

    init {
        runnable = Runnable {
            duration += delay
            handler.postDelayed(runnable, delay)
            listener.onDecibel(duration.toString())
        }
    }

    fun start(){
        handler.postDelayed(runnable, delay)
    }

    fun pause(){
        handler.removeCallbacks(runnable)
    }

    fun stop(){
        handler.removeCallbacks(runnable)
        duration = 0L
    }
}