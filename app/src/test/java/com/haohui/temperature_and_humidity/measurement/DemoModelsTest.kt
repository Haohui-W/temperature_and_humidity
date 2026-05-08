package com.haohui.temperature_and_humidity.measurement

import com.haohui.temperature_and_humidity.model.MeasurementDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoModelsTest {
    @Test
    fun acousticDemoModel_marksOutputUncalibrated() {
        val output = AcousticDemoModel().predict(
            AcousticModelInput(
                soundSpeedMetersPerSecond = 343.0,
                pressureHpa = 1_013.2,
                inputQuality = 0.7
            )
        )

        assertNotNull(output)
        assertFalse(output!!.metadata.calibrated)
        assertEquals(0.7, output.confidence, 0.001)
    }

    @Test
    fun acousticDemoModel_rejectsImplausibleSoundSpeed() {
        val output = AcousticDemoModel().predict(
            AcousticModelInput(
                soundSpeedMetersPerSecond = 100.0,
                pressureHpa = 1_013.2,
                inputQuality = 0.7
            )
        )

        assertNull(output)
    }

    @Test
    fun thermalDemoModel_usesAvailableInputs() {
        val output = ThermalDemoModel().predict(
            ThermalModelInput(
                batteryTemperatureCelsius = 25.0,
                cpuTemperatureCelsius = null,
                cpuLoadPercent = 20.0,
                previousCpuLoadPercent = 18.0
            )
        )

        assertNotNull(output)
        assertFalse(output!!.metadata.calibrated)
        assertEquals(25.0, output.temperatureCelsius, 0.001)
    }

    @Test
    fun thermalDemoModel_compensatesDeviceHeatBeforeAmbientEstimate() {
        val output = ThermalDemoModel().predict(
            ThermalModelInput(
                batteryTemperatureCelsius = 35.9,
                cpuTemperatureCelsius = 44.4,
                cpuLoadPercent = null,
                previousCpuLoadPercent = null
            )
        )

        assertNotNull(output)
        assertTrue(output!!.temperatureCelsius in 25.0..33.0)
    }

    @Test
    fun compactDiagnosticsSummary_deduplicatesRepeatedModelText() {
        val summary = MeasurementDiagnostics(
            modelSummary = "热特征未标定演示模型（v0）",
            inputSummary = "热特征未标定演示模型（v0）"
        ).compactSummary()

        assertEquals("热特征未标定演示模型（v0）", summary)
    }
}
