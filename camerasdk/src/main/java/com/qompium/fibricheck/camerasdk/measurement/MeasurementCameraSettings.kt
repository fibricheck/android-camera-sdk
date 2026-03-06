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
    var exposureMode: String?,
    @SerializedName("iso")
    var iso: List<Int>?,
    @SerializedName("exposure_time")
    var exposureTime: List<Long>?,
    @SerializedName("white_balance_mode")
    var whiteBalanceMode: String?,
    @SerializedName("white_balance")
    var whiteBalance: WhiteBalanceLog?,
    @SerializedName("focus_mode")
    var focusMode: String?,
    @SerializedName("focus")
    var focus: List<Float>?,
    @SerializedName("hdr_mode")
    var hdrMode: String?,
    @SerializedName("hdr_supported")
    var hdrEnabled: Boolean?,
    @SerializedName("tonemap_mode")
    var tonemapMode: String?,
    @SerializedName("hdr_profile")
    var hdrProfile: String?
)
