package com.qompium.fibricheck.camerasdk.extensions

import android.graphics.Rect
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun RggbChannelVector.toCustomString(): String {
  return "{\"r\":${red}, \"g_even\":${greenEven}, \"g_odd\":${greenOdd}, \"b\":${blue}}"
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun ColorSpaceTransform.toCustomString(): String {
  val elements = listOf(
    getElement(0, 0),
    getElement(1, 0),
    getElement(2, 0),
    getElement(0, 1),
    getElement(1, 1),
    getElement(2, 1),
    getElement(0, 2),
    getElement(1, 2),
    getElement(2, 2),
  )

  return "[${elements.joinToString(", ")}]"
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun MeteringRectangle.toCustomString(): String {
  return "{\"w\":${width}, \"h\":${height}, \"x\":${x}, \"y\":${y}, \"weight\": ${meteringWeight}}"
}

fun <T, S> android.util.Pair<T, S>.toCustomString(): String {
  return "[${first}, ${second}]"
}

fun Rect.toCustomString(): String {
  return "{\"left\": ${left}, \"top\": ${top}, \"right\": ${right}, \"bottom\": ${bottom}}"
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun Rational.toCustomString(): String {
  return "{\"numerator\":${numerator}, \"denominator\":${denominator}, \"value\":${toDouble()}}"
}

val DenyList = listOf(
  "android.statistics.faces",
)

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
val KeyParsers = mapOf<String, (Any) -> String >(
  "android.colorCorrection.gains" to { data: Any ->
    (data as RggbChannelVector).toCustomString()
  },
  "android.colorCorrection.transform" to { data: Any -> (data as ColorSpaceTransform).toCustomString() },
  "android.control.aeRegions" to { data: Any -> "[${(data as Array<MeteringRectangle>).joinToString(", ") { it.toCustomString() }}]" },
  "android.control.afRegions" to { data: Any -> "[${(data as Array<MeteringRectangle>).joinToString(", ") { it.toCustomString() }}]" },
  "android.lens.focusRange" to { data: Any -> (data as android.util.Pair<Float, Float>).toCustomString() },
  "android.lens.intrinsicCalibration" to { data: Any -> "[${(data as FloatArray).joinToString(", ")}]" },
  "android.lens.poseRotation" to { data: Any -> "[${(data as FloatArray).joinToString(", ")}]" },
  "android.lens.poseTranslation" to { data: Any -> "[${(data as FloatArray).joinToString(", ")}]" },
  "android.scaler.cropRegion" to { data: Any -> (data as Rect).toCustomString() },
  "android.sensor.dynamicBlackLevel" to { data: Any -> "[${(data as FloatArray).joinToString(", ")}]" },
  "android.sensor.neutralColorPoint" to { data: Any -> "[${(data as Array<Rational>).joinToString { it.toCustomString()}}]" },
  "android.sensor.noiseProfile" to { data: Any -> "[${(data as Array<android.util.Pair<Double, Double>>).joinToString(", ") { it.toCustomString() }}]" },
  "android.sensor.testPatternData" to { data: Any -> "[${(data as IntArray).joinToString(", ")}]" },

)

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun TotalCaptureResult.toMap (): Map<String, String> {
  return keys.filter {
    !DenyList.contains(it.name) && it.name.startsWith("android")
  }.map {
    it to get(it)
  }.filter {
    it.second != null
  }.map {
    val valueParser = KeyParsers[it.first.name] ?: { data: Any -> data.toString() }
    it.first.name to valueParser(it.second!!)
  }.toMap()
}