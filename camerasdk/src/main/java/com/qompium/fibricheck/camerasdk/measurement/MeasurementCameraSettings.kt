package com.qompium.fibricheck.camerasdk.measurement

import com.google.gson.annotations.SerializedName

data class MeasurementCameraSettings(
    @SerializedName("exposure_mode")
    var exposureMode: String?,
    @SerializedName("iso")
    var iso: List<List<Int>>?,
    @SerializedName("exposure_time")
    var exposureTime: List<List<Any>>?,
    @SerializedName("white_balance_mode")
    var whiteBalanceMode: String?,
    @SerializedName("white_balance")
    var whiteBalance: List<List<Any>>?,
    @SerializedName("focus_mode")
    var focusMode: String?,
    @SerializedName("focus")
    var focus: List<List<Any>>?,
    @SerializedName("hdr_mode")
    var hdrMode: String?,
    @SerializedName("tonemap_mode")
    var tonemapMode: String?,
    @SerializedName("hdr_profile")
    var hdrProfile: String?,
    @SerializedName("hdr")
    var hdr: List<List<Any>>?
)
