package com.qompium.fibricheck.camerasdk.extensions

import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.qompium.fibricheck.camerasdk.measurement.Vec3f

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun RggbChannelVector.toRgb(): Vec3f {
  return Vec3f(red, greenOdd + greenEven, blue)
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun RggbChannelVector.toCustomString(): String {
  return "{\"r\":${red}, \"g_even\":${greenEven}, \"g_odd\":${greenOdd}, \"b\":${blue}}"
}