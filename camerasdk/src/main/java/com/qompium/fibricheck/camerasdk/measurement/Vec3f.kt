package com.qompium.fibricheck.camerasdk.measurement

data class Vec3f(
  val r: Float = 0.0f,
  val g: Float = 0.0f,
  val b: Float = 0.0f
) {
  override fun toString(): String {
    return "r: $r, g: $g, b: $b"
  }
}
