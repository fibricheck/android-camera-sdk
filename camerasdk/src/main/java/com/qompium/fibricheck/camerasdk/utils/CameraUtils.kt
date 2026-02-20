package com.qompium.fibricheck.camerasdk.utils

import android.media.Image
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.qompium.fibricheck.camerasdk.measurement.Quadrant
import com.qompium.fibricheck.camerasdk.measurement.QuadrantColor
import com.qompium.fibricheck.camerasdk.measurement.Vec3f
import java.util.Arrays
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

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

    fun calculateAverageYUV(yuvImage: Image?, quadrantRows: Int, quadrantCols: Int): QuadrantColor? {
      if (yuvImage == null) {
        Log.w("CameraUtils", "YUV image was null..")
        return null
      }

      var ySum = 0
      var uSum = 0
      var vSum = 0
      var yAvg: Double
      var uAvg: Double
      var vAvg: Double
      var stdDevY: Double
      val histY = IntArray(256)

      val quadrant = Quadrant()
      val quadrantDataArray = Array(quadrantRows) { Array(quadrantCols) { IntArray(3) } }

      try {
        val width = yuvImage.width
        val height = yuvImage.height
        val frameSize = (width * height).toDouble()

        val quadrantWidth: Int = width / quadrantCols
        val quadrantHeight: Int = height / quadrantRows

        val yPlane = yuvImage.planes[0]
        val uPlane = yuvImage.planes[1]
        val vPlane = yuvImage.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        yBuf.rewind()
        uBuf.rewind()
        vBuf.rewind()

        //The U/V planes are guaranteed to have the same row stride and pixel stride.
        //In particular, uPlane.getRowStride() == vPlane.getRowStride() and uPlane.getPixelStride() == vPlane.getPixelStride();
        val yRowStride = yPlane.rowStride
        val uvRowStride = vPlane.rowStride

        val yPixStride = yPlane.pixelStride
        val uvPixStride = vPlane.pixelStride

        val yFullRow = ByteArray(yPixStride * (width - 1) + 1)
        val uFullRow = ByteArray(uvPixStride * (width / 2 - 1) + 1)
        val vFullRow = ByteArray(uvPixStride * (width / 2 - 1) + 1)

        var yValue: Int
        var uValue: Int
        var vValue: Int
        for (i in 0 until height) {
          val halfH = i / 2
          yBuf.position(yRowStride * i)
          yBuf.get(yFullRow)
          uBuf.position(uvRowStride * halfH)
          uBuf.get(uFullRow)
          vBuf.position(uvRowStride * halfH)
          vBuf.get(vFullRow)
          for (j in 0..<width) {
            val halfW = j / 2
            yValue = yFullRow[yPixStride * j].toInt() and 0xFF
            uValue = uFullRow[uvPixStride * halfW].toInt() and 0xFF
            vValue = vFullRow[uvPixStride * halfW].toInt() and 0xFF

            yValue = if (yValue > 0) yValue else 0
            uValue = if (uValue > 0) uValue else 0
            vValue = if (vValue > 0) vValue else 0

            quadrantDataArray[i / quadrantHeight]!![j / quadrantWidth]!![0] += yValue
            quadrantDataArray[i / quadrantHeight]!![j / quadrantWidth]!![1] += uValue
            quadrantDataArray[i / quadrantHeight]!![j / quadrantWidth]!![2] += vValue

            histY[yValue]++

            ySum += yValue
            uSum += uValue
            vSum += vValue
          }
        }

        yAvg = ySum / frameSize
        uAvg = uSum / frameSize
        vAvg = vSum / frameSize

        quadrant.avgY = yAvg
        quadrant.frameSize = (quadrantHeight * quadrantWidth).toDouble()
        quadrant.processDataArray(quadrantDataArray)

        var sigmaY: Long = 0
        for (i in 0..255) {
          sigmaY += (histY[i] * (i - yAvg).pow(2.0)).toLong()
        }
        stdDevY = sqrt(sigmaY / frameSize)

        //Log.e("std", String.format("%f, %f, %f", yAvg, vAvg, stdDevY));
      } catch (e: NullPointerException) {
        Log.e("CameraUtils", "NPE while calculating YUV average")
        return null
      }

      return QuadrantColor(quadrant, doubleArrayOf(yAvg, uAvg, vAvg, stdDevY))
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun pickSmallest(choices: Array<Size>): Size {
      return choices.minBy {
        it.width * it.height
      }
    }

    fun getStringFromHardwareLevel(hardwareLevel: Int): String {
      return when (hardwareLevel) {
        -1 -> "camera1"
        0 -> "camera2 - limited"
        1 -> "camera2 - full"
        2 -> "camera2 - legacy"
        3 -> "camera2 - level3"
        else -> "undetected"
      }
    }
  }
}