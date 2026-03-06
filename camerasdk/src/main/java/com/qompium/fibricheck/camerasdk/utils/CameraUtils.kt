package com.qompium.fibricheck.camerasdk.utils

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import android.os.Build
import android.util.Size
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import java.util.Arrays
import kotlin.math.ln
import kotlin.math.pow


val hdrQualityRanking = listOf(
    DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM,   // Highest - Dolby Vision 10-bit (OEM)
    DynamicRangeProfiles.DOLBY_VISION_10B_HDR_REF,   // Dolby Vision 10-bit (reference)
    DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM,    // Dolby Vision 8-bit (OEM)
    DynamicRangeProfiles.DOLBY_VISION_8B_HDR_REF,    // Dolby Vision 8-bit (reference)
    DynamicRangeProfiles.HDR10_PLUS,                  // HDR10+ (dynamic metadata)
    DynamicRangeProfiles.HDR10,                       // HDR10 (static metadata)
    DynamicRangeProfiles.HLG10,                       // HLG10 (broadest device support)
    DynamicRangeProfiles.STANDARD                     // SDR - not HDR
)

class CameraUtils {
    companion object {
        // https://github.com/mohankumar-s/android_camera2_manual/blob/master/Camera2ManualFragment.java
        fun whiteBalanceToGains(whiteBalance: Int): Vec3f {
            val temperature = (whiteBalance / 100).toFloat()
            var red: Float
            var green: Float
            var blue: Float

            //Calculate red
            if (temperature <= 66) red = 255f
            else {
                red = temperature - 60
                red = (329.698727446 * (red.toDouble().pow(-0.1332047592))).toFloat()
                if (red < 0) red = 0f
                if (red > 255) red = 255f
            }


            //Calculate green
            if (temperature <= 66) {
                green = temperature
                green = (99.4708025861 * ln(green.toDouble()) - 161.1195681661).toFloat()
                if (green < 0) green = 0f
                if (green > 255) green = 255f
            } else {
                green = temperature - 60
                green = (288.1221695283 * (green.toDouble().pow(-0.0755148492))).toFloat()
                if (green < 0) green = 0f
                if (green > 255) green = 255f
            }

            //calculate blue
            if (temperature >= 66) blue = 255f
            else if (temperature <= 19) blue = 0f
            else {
                blue = temperature - 10
                blue = (138.5177312231 * ln(blue.toDouble()) - 305.0447927307).toFloat()
                if (blue < 0) blue = 0f
                if (blue > 255) blue = 255f
            }

            return Vec3f((red / 255) * 2, (green / 255) * 2, (blue / 255) * 2)
        }

        fun getHighestQualityHdrProfile(availableProfiles: Set<Long>): Long {
            for (profile in hdrQualityRanking) {
                if (availableProfiles.contains(profile)) {
                    return profile
                }
            }

            return -1
        }

        fun dynamicRangeProfileToString(profile: Long?): String?{
            if (profile == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

            return when (profile) {
                DynamicRangeProfiles.STANDARD                    -> "STANDARD (SDR)"
                DynamicRangeProfiles.HLG10                       -> "HLG10"
                DynamicRangeProfiles.HDR10                       -> "HDR10"
                DynamicRangeProfiles.HDR10_PLUS                  -> "HDR10_PLUS"
                DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM    -> "DOLBY_VISION_10B_HDR_OEM"
                DynamicRangeProfiles.DOLBY_VISION_10B_HDR_REF    -> "DOLBY_VISION_10B_HDR_REF"
                DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM     -> "DOLBY_VISION_8B_HDR_OEM"
                DynamicRangeProfiles.DOLBY_VISION_8B_HDR_REF     -> "DOLBY_VISION_8B_HDR_REF"
                DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM_PO -> "DOLBY_VISION_10B_HDR_OEM_PO"
                DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM_PO  -> "DOLBY_VISION_8B_HDR_OEM_PO"
                else                                             -> "UNKNOWN ($profile)"
            }
        }

        fun tonemapModeToString(tonemapMode: Int?): String? {
            if (tonemapMode == null) return null

            return when (tonemapMode) {
                CaptureResult.TONEMAP_MODE_CONTRAST_CURVE -> "CONTRAST_CURVE"
                CaptureResult.TONEMAP_MODE_FAST -> "FAST"
                CaptureResult.TONEMAP_MODE_HIGH_QUALITY -> "HIGH_QUALITY"
                CaptureResult.TONEMAP_MODE_GAMMA_VALUE -> "GAMMA_VALUE"
                CaptureResult.TONEMAP_MODE_PRESET_CURVE -> "PRESET_CURVE"
                else -> "UNKNOWN($tonemapMode)"
            }
        }

        fun getSmallestSize(choices: Array<Size>): Size {
            return choices.minBy { it.width * it.height }
        }
    }
}