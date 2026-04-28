package com.qompium.fibricheck.camerasdk.models

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import androidx.annotation.RequiresApi

data class CameraSettingsInfo(
    val hardwareLevel: Int,
    val hasManualPostProcessing: Boolean,
    val isoRange: Pair<Int, Int>,
    val exposureTimeRange: Pair<Long, Long>,
    val focusRange: Pair<Float, Float>,
) {
    constructor(infoSupportedHardwareLevelLegacy: Int) : this(
        infoSupportedHardwareLevelLegacy,
        false,
        Pair(-1, -1),
        Pair(-1L, -1L),
        Pair(-1.0f, -1.0f)
    )

    companion object {
        fun from(characteristics: CameraCharacteristics): CameraSettingsInfo {
            val capabilities =
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                ?: Range(-1, -1)
            val exposureTimeRange =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(
                    -1L,
                    -1L
                )
            val focusRange = Range(
                0.0f,
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: -1.0f
            )
            val hardwareLevel =
                characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
            val hasManualPostProcessing =
                capabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                    ?: false

            return CameraSettingsInfo(
                hardwareLevel,
                hasManualPostProcessing,
                Pair(isoRange.lower, isoRange.upper),
                Pair(exposureTimeRange.lower, exposureTimeRange.upper),
                Pair(focusRange.lower, focusRange.upper)
            )
        }
    }
}
