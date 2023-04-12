package com.qompium.fibricheck_camera_sdk.listeners;

public interface OnBeatEventListener {

  void onFingerDetected ();

  void onFingerRemoved (double y, double v, double stdDevY);

  void onHeartBeat (int value);

  void onPulseDetected ();
}
