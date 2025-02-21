package com.qompium.fibricheck.camerasdk.listeners

import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
open class EmptyCaptureCallback: CaptureCallback() {
}