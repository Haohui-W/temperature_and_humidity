package com.haohui.temperature_and_humidity.measurement

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

interface PressureReader {
    fun readPressureHpa(): Double?

    fun readValidPressureHpa(): Double? {
        return readPressureHpa()?.takeIf(::isValidPressureHpa)
    }

    companion object {
        const val MIN_VALID_HPA = 300.0
        const val MAX_VALID_HPA = 1_100.0

        fun isValidPressureHpa(value: Double): Boolean = value in MIN_VALID_HPA..MAX_VALID_HPA
    }
}

class AndroidPressureReader(
    private val context: Context,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : PressureReader {
    override fun readPressureHpa(): Double? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return null
        val handlerThread = HandlerThread("pressure-reader")
        handlerThread.start()

        val latch = CountDownLatch(1)
        val pressure = AtomicReference<Double?>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull()?.toDouble()
                if (value != null) {
                    pressure.set(value)
                    latch.countDown()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager.registerListener(
            listener,
            pressureSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            Handler(handlerThread.looper)
        )
        if (!registered) {
            handlerThread.quitSafely()
            return null
        }

        return try {
            if (latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                pressure.get()
            } else {
                null
            }
        } finally {
            sensorManager.unregisterListener(listener)
            handlerThread.quitSafely()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 1_500L
    }
}

object UnavailablePressureReader : PressureReader {
    override fun readPressureHpa(): Double? = null
}
