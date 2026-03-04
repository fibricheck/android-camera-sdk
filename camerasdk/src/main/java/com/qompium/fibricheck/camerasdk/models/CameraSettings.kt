package com.qompium.fibricheck.camerasdk.models

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi
import com.qompium.fibricheck.camerasdk.extensions.toRgb
import com.qompium.fibricheck.camerasdk.measurement.MeasurementCameraSettings
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import com.qompium.fibricheck.camerasdk.measurement.WhiteBalanceLog
import com.qompium.fibricheck.camerasdk.utils.CameraUtils

open class CameraSettingsInput(
    var exposureMode: CameraSettingMode = CameraSettingMode.Locked,
    var manualIsoValue: Int = 0,
    var manualExposureTime: Long = 0,

    var whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.Auto,
    var manualWhiteBalanceRgb: Vec3f = Vec3f(),
    var manualWhiteBalanceKelvin: Int = 6504,

    var focusMode: CameraSettingMode = CameraSettingMode.Auto,
    var manualFocusValue: Float = 0f,

    var logWhiteBalance: Boolean = false,
    var logExposure: Boolean = false,
    var logFocus: Boolean = false
)

public class CameraSettings(
    var cameraSettingsState: CameraSettingsState = CameraSettingsState.Calibrating,
    exposureMode: CameraSettingMode = CameraSettingMode.Locked,
    var autoIsoValue: Int = 0,
    var autoExposureTime: Long = 0,
    manualIsoValue: Int = 0,
    manualExposureTime: Long = 0,

    whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.Auto,
    var autoWhiteBalanceRgb: Vec3f = Vec3f(),
    manualWhiteBalanceRgb: Vec3f = Vec3f(),
    manualWhiteBalanceKelvin: Int = 6504,

    focusMode: CameraSettingMode = CameraSettingMode.Auto,
    var autoFocusValue: Float = 0f,
    manualFocusValue: Float = 0f,

    logWhiteBalance: Boolean = false,
    logExposure: Boolean = false,
    logFocus: Boolean = false
) : CameraSettingsInput(
    exposureMode, manualIsoValue, manualExposureTime,
    whiteBalanceMode, manualWhiteBalanceRgb, manualWhiteBalanceKelvin,
    focusMode, manualFocusValue,
    logWhiteBalance, logExposure, logFocus
) {
    val iso get() = if (exposureMode == CameraSettingMode.Manual) manualIsoValue else autoIsoValue
    val exposureTime get() = if (exposureMode == CameraSettingMode.Manual) manualExposureTime else autoExposureTime
    val focus get() = if (focusMode == CameraSettingMode.Manual) manualFocusValue else autoFocusValue
    val whiteBalance
        get() = when (whiteBalanceMode) {
            WhiteBalanceMode.ManualRgb -> manualWhiteBalanceRgb
            WhiteBalanceMode.ManualKelvin -> CameraUtils.whiteBalanceToGains(
                manualWhiteBalanceKelvin
            )

            else -> autoWhiteBalanceRgb
        }
    val whiteBalanceRggb
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() = RggbChannelVector(
            whiteBalance.r,
            whiteBalance.g / 2.0f,
            whiteBalance.g / 2.0f,
            whiteBalance.b
        )
    val whiteBalanceLog = mutableListOf<Vec3f>()
    val isoLog = mutableListOf<Int>()
    val exposureTimeLog = mutableListOf<Long>()
    val focusLog = mutableListOf<Float>()

    fun set(settings: CameraSettingsInput) {
        this.exposureMode = settings.exposureMode
        this.manualIsoValue = settings.manualIsoValue
        this.manualExposureTime = settings.manualExposureTime

        this.whiteBalanceMode = settings.whiteBalanceMode
        this.manualWhiteBalanceRgb = settings.manualWhiteBalanceRgb
        this.manualWhiteBalanceKelvin = settings.manualWhiteBalanceKelvin

        this.focusMode = settings.focusMode
        this.manualFocusValue = settings.manualFocusValue

        this.logWhiteBalance = settings.logWhiteBalance
        this.logExposure = settings.logExposure
        this.logFocus = settings.logFocus
    }

    fun addTo(map: MutableMap<String, Any>) {
        if (iso != 0 && exposureMode != CameraSettingMode.Auto) map["camera_iso"] = iso
        if (exposureTime != 0L && exposureMode != CameraSettingMode.Auto) map["camera_exposure_time"] =
            exposureTime
        if (whiteBalanceMode != WhiteBalanceMode.Auto) map["camera_white_balance"] = whiteBalance
        if (focusMode != CameraSettingMode.Auto) map["camera_focus_distance"] = focus
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onSettingsChanged(settings: CaptureResult) {
        val focusDistance = settings.get(CaptureResult.LENS_FOCUS_DISTANCE)
        val whiteBalance = settings.get(CaptureResult.COLOR_CORRECTION_GAINS)?.toRgb()
        val iso = settings.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposureTime = settings.get(CaptureResult.SENSOR_EXPOSURE_TIME)

        if (focusMode != CameraSettingMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoFocusValue = focusDistance ?: autoFocusValue
        }
        if (whiteBalanceMode != WhiteBalanceMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoWhiteBalanceRgb = whiteBalance ?: autoWhiteBalanceRgb
        }
        if (exposureMode != CameraSettingMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoExposureTime = exposureTime ?: autoExposureTime
            autoIsoValue = iso ?: autoIsoValue
        }

        // We only want to log values when we are recording
        if (cameraSettingsState != CameraSettingsState.Recording) {
            return
        }

        if (logFocus && focusMode == CameraSettingMode.Auto && focusDistance != null) {
            focusLog.add(focusDistance)
        }
        if (logWhiteBalance && whiteBalanceMode == WhiteBalanceMode.Auto && whiteBalance != null) {
            whiteBalanceLog.add(whiteBalance)
        }
        if (logExposure && exposureMode == CameraSettingMode.Auto && exposureTime != null && iso != null) {
            exposureTimeLog.add(exposureTime)
            isoLog.add(iso)
        }
    }

    fun toOutput(): MeasurementCameraSettings {
        val whiteBalanceMode = when (whiteBalanceMode) {
            WhiteBalanceMode.ManualRgb -> "manual"
            WhiteBalanceMode.ManualKelvin -> "manual"
            WhiteBalanceMode.Locked -> null
            else -> "auto"
        }

        val whiteBalanceR = whiteBalanceLog.map { it.r }
        val whiteBalanceG = whiteBalanceLog.map { it.g }
        val whiteBalanceB = whiteBalanceLog.map { it.b }
        val isWhiteBalanceEmpty =
            whiteBalanceR.isEmpty() && whiteBalanceG.isEmpty() && whiteBalanceB.isEmpty()
        val whiteBalanceOutput = if (isWhiteBalanceEmpty) null else WhiteBalanceLog(
            whiteBalanceR,
            whiteBalanceG,
            whiteBalanceB
        )

        return MeasurementCameraSettings(
            if (exposureMode != CameraSettingMode.Locked) exposureMode.name.lowercase() else null,
            if (isoLog.size > 0) isoLog else null,
            if (exposureTimeLog.size > 0) exposureTimeLog else null,
            whiteBalanceMode,
            whiteBalanceOutput,
            if (focusMode != CameraSettingMode.Locked) focusMode.name.lowercase() else null,
            if (focusLog.size > 0) focusLog else null
        )
    }

    fun clear() {
        whiteBalanceLog.clear()
        focusLog.clear()
        exposureTimeLog.clear()
        isoLog.clear()
    }

    val isAutoExposure
        get() = exposureMode == CameraSettingMode.Auto || (exposureMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoFocus
        get() = focusMode == CameraSettingMode.Auto || (focusMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoWhiteBalance
        get() = whiteBalanceMode == WhiteBalanceMode.Auto || (whiteBalanceMode == WhiteBalanceMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)
}
