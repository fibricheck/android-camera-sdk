package com.qompium.fibricheck_camera_sdk.listeners;

import com.qompium.fibricheck_camera_sdk.measurement.MeasurementData;

public interface IFibriListener {

  void onFingerDetected ();

  void onFingerRemoved (double y, double v, double stdDevY);

  void onHeartBeat (int value);

  void onPulseDetectionTimeExpired ();

  void onPulseDetected ();

  void onFingerDetectionTimeExpired ();

  void onMeasurementFinished ();

  void timeRemaining (int seconds);

  void onMeasurementProcessed (MeasurementData measurementData);

  void onMeasurementError (String message);

  void onMeasurementStart ();

  void onMovementDetected ();

  void onSampleReady (double ppg, double raw);

  void onCalibrationReady ();
}
