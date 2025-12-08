package com.qompium.fibricheck.camerasdk.measurement

data class Yuv(
  var y: Double,
  var u: Double,
  var v: Double
) {
  constructor(yuvArray: IntArray, frameSize: Double) : this(
    yuvArray[0] / frameSize,
    yuvArray[1] / frameSize,
    yuvArray[2] / frameSize
  )

  override fun toString(): String {
    return "{ \"y\": ${y}, \"u\": ${u}, \"v\": ${v} }"
  }
}
