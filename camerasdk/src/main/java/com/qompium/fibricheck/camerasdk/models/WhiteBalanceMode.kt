package com.qompium.fibricheck.camerasdk.models

enum class WhiteBalanceMode {
    // Keep white-balancing on automatic all the time
    Auto,

    // Set white-balancing to auto, but lock it during measurement
    Locked,

    // Manually set a value for the white balance in RGB
    ManualRgb,

    // Manually set a value for the white balance in Kelvin (not recommended)
    ManualKelvin
}