package com.qompium.fibricheck.camerasdk.extensions

import android.hardware.camera2.CaptureResult
import android.os.Build
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun CaptureResult.toMap (): Map<String, String> {
  return keys.filter {
    val value = get(it)
    value != null
  }.associate {
    val rawValue = get(it)
    val value = rawValue.toString()
    it.name to value
  }
}