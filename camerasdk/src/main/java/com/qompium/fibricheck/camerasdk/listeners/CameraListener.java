package com.qompium.fibricheck.camerasdk.listeners;

import com.qompium.fibricheck.camerasdk.measurement.Quadrant;

public interface CameraListener {

  void onFrameReceived (final Quadrant quadrantData, final double[] yuvData, final long timestamp);

  void onCameraDestroyed ();
}
