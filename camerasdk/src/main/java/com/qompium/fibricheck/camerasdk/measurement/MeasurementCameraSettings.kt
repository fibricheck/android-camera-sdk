package com.qompium.fibricheck.camerasdk.measurement

import com.google.gson.annotations.SerializedName

data class WhiteBalanceLog(
    @SerializedName("r")
    val r: List<Float>,
    @SerializedName("g")
    val g: List<Float>,
    @SerializedName("b")
    val b: List<Float>
)

data class MeasurementCameraSettings(
    @SerializedName("exposure_mode")
    val exposureMode: String?,
    @SerializedName("iso")
    val iso: List<Int>?,
    @SerializedName("exposure_time")
    val exposureTime: List<Long>?,
    @SerializedName("white_balance_mode")
    val whiteBalanceMode: String?,
    @SerializedName("white_balance")
    val whiteBalance: WhiteBalanceLog?,
    @SerializedName("focus_mode")
    val focusMode: String?,
    @SerializedName("focus")
    val focus: List<Float>?
)
