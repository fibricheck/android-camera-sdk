package com.qompium.fibricheck.camerasdk.extensions

import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import androidx.annotation.RequiresApi
import com.qompium.fibricheck.camerasdk.measurement.Vec3f

fun RggbChannelVector.toRgb(): Vec3f {
    return Vec3f(red, greenOdd + greenEven, blue)
}