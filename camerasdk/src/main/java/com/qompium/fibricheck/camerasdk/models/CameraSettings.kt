package com.qompium.fibricheck.camerasdk.models

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.RggbChannelVector
import com.qompium.fibricheck.camerasdk.extensions.differs
import com.qompium.fibricheck.camerasdk.extensions.toInt
import com.qompium.fibricheck.camerasdk.extensions.toRgb
import com.qompium.fibricheck.camerasdk.measurement.MeasurementCameraSettings
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
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

    var hdrMode: HdrMode = HdrMode.Auto,

    var logWhiteBalance: Boolean = false,
    var logExposure: Boolean = false,
    var logFocus: Boolean = false,
    var logHdr: Boolean = false
)

class CameraSettings(
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

    hdrMode: HdrMode = HdrMode.Auto,
    var hdrEnabled: Boolean = false,

    logWhiteBalance: Boolean = false,
    logExposure: Boolean = false,
    logFocus: Boolean = false,
    logHdr: Boolean = false,
) : CameraSettingsInput(
    exposureMode, manualIsoValue, manualExposureTime,
    whiteBalanceMode, manualWhiteBalanceRgb, manualWhiteBalanceKelvin,
    focusMode, manualFocusValue,
    hdrMode,
    logWhiteBalance, logExposure, logFocus, logHdr
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
        get() = RggbChannelVector(
            whiteBalance.r,
            whiteBalance.g / 2.0f,
            whiteBalance.g / 2.0f,
            whiteBalance.b
        )
    val whiteBalanceLog = mutableListOf<List<Any>>()
    val isoLog = mutableListOf<List<Int>>()
    val exposureTimeLog = mutableListOf<List<Any>>()
    val focusLog = mutableListOf<List<Any>>()
    val hdrLog = mutableListOf<List<Any>>()
    var toneMapMode: String? = null
    var hdrProfile: Long? = null

    var frameCounter = 0
    var lastIsoValue = -1
    var lastExposureTimeValue =-1L
    var lastFocusValue = -1.0f
    var lastWhiteBalanceValue = Vec3f(-1f, -1f, -1f)
    var lastHdrValue: Boolean? = null

    fun set(settings: CameraSettingsInput) {
        this.exposureMode = settings.exposureMode
        this.manualIsoValue = settings.manualIsoValue
        this.manualExposureTime = settings.manualExposureTime

        this.whiteBalanceMode = settings.whiteBalanceMode
        this.manualWhiteBalanceRgb = settings.manualWhiteBalanceRgb
        this.manualWhiteBalanceKelvin = settings.manualWhiteBalanceKelvin

        this.focusMode = settings.focusMode
        this.manualFocusValue = settings.manualFocusValue

        this.hdrMode = settings.hdrMode

        this.logWhiteBalance = settings.logWhiteBalance
        this.logExposure = settings.logExposure
        this.logFocus = settings.logFocus
        this.logHdr = settings.logHdr
    }

    fun addTo(map: MutableMap<String, Any>) {
        if (iso != 0 && exposureMode != CameraSettingMode.Auto) map["camera_iso"] = iso
        if (exposureTime != 0L && exposureMode != CameraSettingMode.Auto) map["camera_exposure_time"] =
            exposureTime
        if (whiteBalanceMode != WhiteBalanceMode.Auto) map["camera_white_balance"] = whiteBalance
        if (focusMode != CameraSettingMode.Auto) map["camera_focus_distance"] = focus
        map["camera_hdr"] = when {
            hdrMode == HdrMode.Auto && hdrEnabled -> "hdr-auto-on"
            hdrMode == HdrMode.Auto && !hdrEnabled -> "hdr-auto-off"
            hdrMode == HdrMode.Off && hdrEnabled -> "hdr-conflict"
            hdrMode == HdrMode.Off && !hdrEnabled -> "hdr-manual-off"
            else -> "hdr-unknown"
        }
    }

    fun onSettingsChanged(settings: CaptureResult) {
        val focusDistance = settings.get(CaptureResult.LENS_FOCUS_DISTANCE)
        val whiteBalance = settings.get(CaptureResult.COLOR_CORRECTION_GAINS)?.toRgb()
        val iso = settings.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposureTime = settings.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val sceneMode = settings.get(CaptureResult.CONTROL_SCENE_MODE)
        hdrEnabled = sceneMode != null && sceneMode == CaptureResult.CONTROL_SCENE_MODE_HDR
        toneMapMode = CameraUtils.tonemapModeToString(settings.get(CaptureResult.TONEMAP_MODE))

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

        if (logFocus && focusMode == CameraSettingMode.Auto && focusDistance != null && lastFocusValue.differs(focusDistance)) {
            focusLog.add(listOf(focusDistance, frameCounter))
            lastFocusValue = focusDistance
        }
        if (logWhiteBalance && whiteBalanceMode == WhiteBalanceMode.Auto && whiteBalance != null && lastWhiteBalanceValue.differs(whiteBalance)) {
            whiteBalanceLog.add(listOf<Any>(whiteBalance.r, whiteBalance.g, whiteBalance.b, frameCounter))
            lastWhiteBalanceValue = whiteBalance
        }
        if (logExposure && exposureMode == CameraSettingMode.Auto && exposureTime != null && iso != null) {
            if (iso != lastIsoValue) {
                isoLog.add(listOf(iso, frameCounter))
                lastIsoValue = iso
            }

            if (exposureTime != lastExposureTimeValue) {
                exposureTimeLog.add(listOf(exposureTime, frameCounter))
                lastExposureTimeValue = exposureTime
            }
        }
        if (logHdr && hdrMode == HdrMode.Auto && hdrEnabled != lastHdrValue) {
            hdrLog.add(listOf(hdrEnabled.toInt(), frameCounter))
            lastHdrValue = hdrEnabled
        }

        frameCounter++
    }

    fun toOutput(): MeasurementCameraSettings {
        val whiteBalanceMode = when (whiteBalanceMode) {
            WhiteBalanceMode.ManualRgb -> "manual"
            WhiteBalanceMode.ManualKelvin -> "manual"
            WhiteBalanceMode.Locked -> null
            else -> "auto"
        }

        return MeasurementCameraSettings(
            if (exposureMode != CameraSettingMode.Locked) exposureMode.name.lowercase() else null,
            isoLog.ifEmpty { null },
            exposureTimeLog.ifEmpty { null },
            whiteBalanceMode,
            whiteBalanceLog,
            if (focusMode != CameraSettingMode.Locked) focusMode.name.lowercase() else null,
            focusLog.ifEmpty { null },
            hdrMode.name.lowercase(),
            toneMapMode,
            if (hdrProfile != null) CameraUtils.dynamicRangeProfileToString(hdrProfile) else null,
            hdrLog.ifEmpty { null }
        )
    }

    fun clear() {
        whiteBalanceLog.clear()
        focusLog.clear()
        exposureTimeLog.clear()
        isoLog.clear()
        hdrLog.clear()
        toneMapMode = null
        hdrProfile = null
        hdrEnabled = false

        frameCounter = 0
        lastIsoValue = -1
        lastExposureTimeValue = -1L
        lastFocusValue = -1.0f
        lastWhiteBalanceValue = Vec3f(-1f, -1f, -1f)
        lastHdrValue = null
    }

    val isAutoExposure
        get() = exposureMode == CameraSettingMode.Auto || (exposureMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoFocus
        get() = focusMode == CameraSettingMode.Auto || (focusMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoWhiteBalance
        get() = whiteBalanceMode == WhiteBalanceMode.Auto || (whiteBalanceMode == WhiteBalanceMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)
}
