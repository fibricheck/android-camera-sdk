package com.qompium.fibricheck.camerasdk.utils

import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import kotlin.math.ln
import kotlin.math.pow

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
  }
}