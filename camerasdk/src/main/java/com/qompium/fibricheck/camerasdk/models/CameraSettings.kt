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
    var internal_exposureMode: CameraSettingMode = CameraSettingMode.Locked,
    var internal_manualIso: Int = 0,
    var internal_manualExposureTime: Long = 0,

    var internal_whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.Auto,
    var internal_manualWhiteBalanceRgb: Vec3f = Vec3f(),
    var internal_manualWhiteBalanceKelvin: Int = 6504,

    var internal_focusMode: CameraSettingMode = CameraSettingMode.Auto,
    var internal_manualFocus: Float = 0f,

    var internal_hdrMode: HdrMode = HdrMode.Off,

    var internal_logWhiteBalance: Boolean = true,
    var internal_logExposure: Boolean = false,
    var internal_logFocus: Boolean = true,
    var internal_logHdr: Boolean = false
)

class CameraSettings(
    var cameraSettingsState: CameraSettingsState = CameraSettingsState.Calibrating,
    internal_exposureMode: CameraSettingMode = CameraSettingMode.Locked,
    var autoIsoValue: Int = 0,
    var autoExposureTime: Long = 0,
    internal_manualIso: Int = 0,
    internal_manualExposureTime: Long = 0,

    internal_whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.Auto,
    var autoWhiteBalanceRgb: Vec3f = Vec3f(),
    internal_manualWhiteBalanceRgb: Vec3f = Vec3f(),
    internal_manualWhiteBalanceKelvin: Int = 6504,

    internal_focusMode: CameraSettingMode = CameraSettingMode.Auto,
    var autoFocusValue: Float = 0f,
    internal_manualFocus: Float = 0f,

    internal_hdrMode: HdrMode = HdrMode.Off,
    var hdrEnabled: Boolean = false,

    internal_logWhiteBalance: Boolean = true,
    internal_logExposure: Boolean = false,
    internal_logFocus: Boolean = true,
    internal_logHdr: Boolean = false,
) : CameraSettingsInput(
    internal_exposureMode, internal_manualIso, internal_manualExposureTime,
    internal_whiteBalanceMode, internal_manualWhiteBalanceRgb, internal_manualWhiteBalanceKelvin,
    internal_focusMode, internal_manualFocus,
    internal_hdrMode,
    internal_logWhiteBalance, internal_logExposure, internal_logFocus, internal_logHdr
) {
    val iso get() = if (internal_exposureMode == CameraSettingMode.Manual) internal_manualIso else autoIsoValue
    val exposureTime get() = if (internal_exposureMode == CameraSettingMode.Manual) internal_manualExposureTime else autoExposureTime
    val focus get() = if (internal_focusMode == CameraSettingMode.Manual) internal_manualFocus else autoFocusValue
    val whiteBalance
        get() = when (internal_whiteBalanceMode) {
            WhiteBalanceMode.ManualRgb -> internal_manualWhiteBalanceRgb
            WhiteBalanceMode.ManualKelvin -> CameraUtils.whiteBalanceToGains(
                internal_manualWhiteBalanceKelvin
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
        this.internal_exposureMode = settings.internal_exposureMode
        this.internal_manualIso = settings.internal_manualIso
        this.internal_manualExposureTime = settings.internal_manualExposureTime

        this.internal_whiteBalanceMode = settings.internal_whiteBalanceMode
        this.internal_manualWhiteBalanceRgb = settings.internal_manualWhiteBalanceRgb
        this.internal_manualWhiteBalanceKelvin = settings.internal_manualWhiteBalanceKelvin

        this.internal_focusMode = settings.internal_focusMode
        this.internal_manualFocus = settings.internal_manualFocus

        this.internal_hdrMode = settings.internal_hdrMode

        this.internal_logWhiteBalance = settings.internal_logWhiteBalance
        this.internal_logExposure = settings.internal_logExposure
        this.internal_logFocus = settings.internal_logFocus
        this.internal_logHdr = settings.internal_logHdr
    }

    fun addTo(map: MutableMap<String, Any>) {
        if (iso != 0 && internal_exposureMode != CameraSettingMode.Auto) map["camera_iso"] = iso
        if (exposureTime != 0L && internal_exposureMode != CameraSettingMode.Auto) map["camera_exposure_time"] =
            exposureTime
        if (internal_whiteBalanceMode != WhiteBalanceMode.Auto) map["camera_white_balance"] = whiteBalance
        if (internal_focusMode != CameraSettingMode.Auto) map["camera_focus_distance"] = focus
        map["camera_hdr"] = when {
            internal_hdrMode == HdrMode.Auto && hdrEnabled -> "hdr-auto-on"
            internal_hdrMode == HdrMode.Auto && !hdrEnabled -> "hdr-auto-off"
            internal_hdrMode == HdrMode.Off && hdrEnabled -> "hdr-conflict"
            internal_hdrMode == HdrMode.Off && !hdrEnabled -> "hdr-manual-off"
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

        if (internal_focusMode != CameraSettingMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoFocusValue = focusDistance ?: autoFocusValue
        }
        if (internal_whiteBalanceMode != WhiteBalanceMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoWhiteBalanceRgb = whiteBalance ?: autoWhiteBalanceRgb
        }
        if (internal_exposureMode != CameraSettingMode.Locked || cameraSettingsState == CameraSettingsState.Calibrating) {
            autoExposureTime = exposureTime ?: autoExposureTime
            autoIsoValue = iso ?: autoIsoValue
        }

        // We only want to log values when we are recording
        if (cameraSettingsState != CameraSettingsState.Recording) {
            return
        }

        if (internal_logFocus && internal_focusMode == CameraSettingMode.Auto && focusDistance != null && lastFocusValue.differs(focusDistance)) {
            focusLog.add(listOf(focusDistance, frameCounter))
            lastFocusValue = focusDistance
        }
        if (internal_logWhiteBalance && internal_whiteBalanceMode == WhiteBalanceMode.Auto && whiteBalance != null && lastWhiteBalanceValue.differs(whiteBalance)) {
            whiteBalanceLog.add(listOf<Any>(whiteBalance.r, whiteBalance.g, whiteBalance.b, frameCounter))
            lastWhiteBalanceValue = whiteBalance
        }
        if (internal_logExposure && internal_exposureMode == CameraSettingMode.Auto && exposureTime != null && iso != null) {
            if (iso != lastIsoValue) {
                isoLog.add(listOf(iso, frameCounter))
                lastIsoValue = iso
            }

            if (exposureTime != lastExposureTimeValue) {
                exposureTimeLog.add(listOf(exposureTime, frameCounter))
                lastExposureTimeValue = exposureTime
            }
        }
        if (internal_logHdr && internal_hdrMode == HdrMode.Auto && hdrEnabled != lastHdrValue) {
            hdrLog.add(listOf(hdrEnabled.toInt(), frameCounter))
            lastHdrValue = hdrEnabled
        }

        frameCounter++
    }

    fun toOutput(): MeasurementCameraSettings {
        val whiteBalanceMode = when (internal_whiteBalanceMode) {
            WhiteBalanceMode.ManualRgb -> "manual"
            WhiteBalanceMode.ManualKelvin -> "manual"
            WhiteBalanceMode.Locked -> null
            else -> "auto"
        }

        return MeasurementCameraSettings(
            internal_exposureMode.name.lowercase(),
            isoLog.ifEmpty { null },
            exposureTimeLog.ifEmpty { null },
            whiteBalanceMode,
            whiteBalanceLog.ifEmpty { null },
            internal_focusMode.name.lowercase(),
            focusLog.ifEmpty { null },
            internal_hdrMode.name.lowercase(),
            toneMapMode,
            CameraUtils.dynamicRangeProfileToString(hdrProfile) ?: "STANDARD (SDR)",
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
        get() = internal_exposureMode == CameraSettingMode.Auto || (internal_exposureMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoFocus
        get() = internal_focusMode == CameraSettingMode.Auto || (internal_focusMode == CameraSettingMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)

    val isAutoWhiteBalance
        get() = internal_whiteBalanceMode == WhiteBalanceMode.Auto || (internal_whiteBalanceMode == WhiteBalanceMode.Locked && cameraSettingsState == CameraSettingsState.Calibrating)
}
