package com.qompium.fibricheck.camerasdk.listeners;

import com.qompium.fibricheck.camerasdk.measurement.MeasurementData;

public interface IFibriListener {

  void onFingerDetected ();

  void onFingerRemoved (double y, double v, double stdDevY);

  void onHeartBeat (int value);

  void onPulseDetectionTimeExpired ();

  void onPulseDetected ();

  void onFingerDetectionTimeExpired ();

  void onMeasurementFinished ();

  void onTimeRemaining (int seconds);

  void onMeasurementProcessed (MeasurementData measurementData);

  void onMeasurementError (String message);

  void onMeasurementStart ();

  void onMovementDetected ();

  void onSampleReady (double ppg, double raw);

  void onCalibrationReady ();
}
