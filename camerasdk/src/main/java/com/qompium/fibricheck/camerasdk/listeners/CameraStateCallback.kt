package com.qompium.fibricheck.camerasdk.listeners

import android.hardware.camera2.CameraDevice
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraStateCallback(
  val open: (CameraDevice) -> Unit = {},
  val close: (CameraDevice) -> Unit = {},
  val error: (CameraDevice, Int) -> Unit = { _, _ -> },
  val disconnect: (CameraDevice) -> Unit = {}
): CameraDevice.StateCallback() {
  override fun onOpened(camera: CameraDevice) {
    open(camera)
  }

  override fun onClosed(camera: CameraDevice) {
    close(camera)
  }

  override fun onError(camera: CameraDevice, error: Int) {
    error(camera, error)
  }

  override fun onDisconnected(camera: CameraDevice) {
    disconnect(camera)
  }
}