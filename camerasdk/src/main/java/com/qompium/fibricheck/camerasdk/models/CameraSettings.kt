package com.qompium.fibricheck.camerasdk.models

import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.ln
import kotlin.math.pow

public data class CameraSettings(
  var isExposureLocked: Boolean = false,
  var autoIsoValue: Int = 0,
  var autoExposureTime: Long = 0,

  var isManualExposureEnabled: Boolean = false,
  var manualIsoValue: Int = 0,
  var manualExposureTime: Long = 0,

  var isManualWhiteBalanceEnabled: Boolean = false,
  var manualWhiteBalance: List<Float> = listOf(1f, 1f, 1f, 1f),

  var isManualFocusEnabled: Boolean = false,
  var manualFocus: Float = 0f,
) {
  companion object {
    // https://github.com/mohankumar-s/android_camera2_manual/blob/master/Camera2ManualFragment.java
    fun whiteBalanceToGains(whiteBalance: Int): List<Float> {
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

      return listOf((red / 255) * 2, (green / 255), (green / 255), (blue / 255) * 2)
    }
  }

  public fun isAutoExposure(): Boolean {
    return !isExposureLocked && !isManualExposureEnabled
  }

  public fun getIso(): Int {
    return if (isManualExposureEnabled) {
      manualIsoValue
    } else {
      autoIsoValue
    }
  }

  public fun getExposureTime(): Long {
    return if (isManualExposureEnabled) {
      manualExposureTime
    } else {
      autoExposureTime
    }
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  public fun getWhileBalanceVector(): RggbChannelVector {
    return RggbChannelVector(
      manualWhiteBalance[0],
      manualWhiteBalance[1],
      manualWhiteBalance[2],
      manualWhiteBalance[3]
    )
  }
}
