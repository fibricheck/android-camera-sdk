package com.qompium.fibricheck_camera_sdk.listeners;

import com.qompium.fibricheck_camera_sdk.measurement.Quadrant;

public interface CameraListener {

  void onFrameReceived (final Quadrant quadrantData, final double[] yuvData, final long timestamp);

  void onCameraDestroyed ();
}
