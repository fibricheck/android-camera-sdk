package com.qompium.fibricheck.camerasdk.extensions

fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}