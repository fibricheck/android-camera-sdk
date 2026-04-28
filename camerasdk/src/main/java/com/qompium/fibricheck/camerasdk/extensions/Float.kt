package com.qompium.fibricheck.camerasdk.extensions

import kotlin.math.abs

fun Float.differs(other: Float, delta: Float = 0.001f): Boolean {
    return abs(this - other) > delta
}