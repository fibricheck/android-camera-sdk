package com.qompium.fibricheck.camerasdk.listeners;

import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class BeatListener {

  private static String TAG = "BeatListener";

  private static final int correlationCalculationLength = 2;

  private static final int timestampCalculationLength = 2;

  private static final int signalCalculationLength = 20;

  private static final int timestampLength = 3;

  private static final int patternLength = 21;

  private int minYValue;

  private int maxYValue;

  private int minVValue;

  private int maxStdDevYValue;

  protected double[] pattern;

  private double[] correlations;

  private long[] timeStamps;

  private double[] signals = new double[21];

  private int fingerOnCount = 0;

  private int fingerOffCount = 0;

  private int pulseCount = 0;

  private ArrayList<Integer> heartRates = new ArrayList<>();

  private double denominatorPattern; //noemer

  private double denominatorSignal;

  private double numerator; //teller

  private boolean fingerDetected = false;

  private boolean pulseDetected = false;

  private OnBeatEventListener beatEventListener;

  private long lastTime = 0;

  private long timeSinceLastPulse;

  public BeatListener (int minYValue, int maxYValue, int maxStdDevYValue, int minVValue) {

    pattern = new double[] {
        -0.260377750000000, -0.264072118421053, -0.690280657894737, 0.320902447368421, 0.636459210526316, 0.775235157894737, 0.616998750000000,
        0.258818513157895, -0.0155936315789474, -0.107737578947368, -0.0730700131578947, -0.00178090789473684, 0.0353458026315790, 0.0177359342105263,
        -0.0479446842105263, -0.145095802631579, -0.218533671052632, -0.243734131578947, -0.243979118421053, -0.229033460526316, -0.206577592105263
    };

    denominatorPattern = calculateDenumerator(pattern);

    correlations = new double[] { 0.0, 0.0, 0.0 };
    timeStamps = new long[] { 0, 0, 0 };

    this.minYValue = minYValue;
    this.maxYValue = maxYValue;
    this.maxStdDevYValue = maxStdDevYValue;
    this.minVValue = minVValue;
  }

  public void correlateWithValue (double value, double[] raw) {

    detectFinger(raw);
    if (!fingerDetected) {
      pulseDetected = false;
      return;
    }
    pushSignal(value);
    calculateNumerator();
    denominatorSignal = calculateDenumerator(signals);

    double correlation = numerator / Math.sqrt(denominatorPattern * denominatorSignal);
    pushCorrelation(correlation);

    if (isPeakDetected()) {
      lastTime = SystemClock.uptimeMillis();
      pushTimestamp(timeSinceLastPulse);
      if (isValidPulse()) {
        countPulse();
        int heartbeat = (int) (60000 / calculateAvgValue(timeStamps));
        heartRates.add(heartbeat);
        beatEventListener.onHeartBeat(getHeartRate());
      }
    }

    timeSinceLastPulse = SystemClock.uptimeMillis() - lastTime;
  }

  public void correlateWithValue (double value, double[] raw, long timestamp) {

    detectFinger(raw);
    if (!fingerDetected) {
      pulseDetected = false;
      return;
    }
    pushSignal(value);
    calculateNumerator();
    denominatorSignal = calculateDenumerator(signals);

    double correlation = numerator / Math.sqrt(denominatorPattern * denominatorSignal);
    pushCorrelation(correlation);

    if (isPeakDetected()) {
      lastTime = timestamp;
      pushTimestamp(timeSinceLastPulse);
      if (isValidPulse()) {
        countPulse();
        int heartbeat = (int) (60000 / calculateAvgValue(timeStamps));
        heartRates.add(heartbeat);
        beatEventListener.onHeartBeat(getHeartRate());
      }
    }
    timeSinceLastPulse = timestamp - lastTime;
  }

  private void pushTimestamp (long timeStamp) {

    for (int i = 0; i < timestampCalculationLength; i++) {
      timeStamps[i] = timeStamps[i + 1];
    }
    timeStamps[timestampCalculationLength] = timeStamp;
  }

  private void pushCorrelation (double correlation) {

    for (int i = 0; i < correlationCalculationLength; i++) {
      correlations[i] = correlations[i + 1];
    }
    correlations[correlationCalculationLength] = correlation;
  }

  private void pushSignal (double signal) {

    for (int i = 0; i < signalCalculationLength; i++) {
      signals[i] = signals[i + 1];
    }
    signals[signalCalculationLength] = signal;
  }

  private void detectFinger (double[] signal) {

    double yValue = signal[0];
    double vValue = signal[2];
    double stdDevY = signal[3];

    // Debug finger values
    // Log.e(TAG, yValue + "/" + vValue + "/" + stdDevY);

    if (yValue > minYValue && yValue < maxYValue && vValue > minVValue && stdDevY <= maxStdDevYValue) {
      fingerOnCount++;
      fingerOffCount = 0;
    } else {
      fingerOffCount++;
      fingerOnCount = 0;
    }

    if (fingerDetected && fingerOffCount >= 4) {
      fingerDetected = false;
      Log.i(TAG,"Finger removed: y/v/stdDevY: " + yValue + "/" + vValue + "/" + stdDevY);
      beatEventListener.onFingerRemoved(yValue, vValue, stdDevY);
    }

    if (!fingerDetected && fingerOnCount >= 30) {
      fingerDetected = true;
      beatEventListener.onFingerDetected();
    }
  }

  private void countPulse () {

    if (!pulseDetected) {
      pulseCount++;
    }

    if (pulseCount >= 3) {
      pulseCount = 0;
      pulseDetected = true;
      beatEventListener.onPulseDetected();
    }
  }

  private void calculateNumerator () {

    numerator = 0;
    for (int i = 0; i < patternLength; i++) {
      numerator += pattern[i] * signals[i];
    }
  }

  private double calculateDenumerator (double[] doubleArray) {

    double denominator = 0;
    for (int i = 0; i < doubleArray.length; i++) {
      denominator += Math.pow(doubleArray[i], 2);
    }
    return denominator;
  }

  private boolean isPeakDetected () {

    return (correlations[0] < correlations[1] && correlations[1] > correlations[2] && correlations[1] > 0.6);
  }

  private boolean isValidPulse () {

    long timeMax = calculateMaxValue(timeStamps);
    long timeMin = calculateMinValue(timeStamps);
    double timeAvg = calculateAvgValue(timeStamps);

    return (timeMax < 2000 && timeMin > 400 && timeMax < timeAvg * 1.20 && timeMin > timeAvg * 0.80);
  }

  private long calculateMinValue (long[] arrayLong) {

    long min = arrayLong[0];

    for (int i = 0; i < arrayLong.length; i++) {
      min = (arrayLong[i] < min) ? arrayLong[i] : min;
    }

    return min;
  }

  private long calculateMaxValue (long[] arrayLong) {

    long max = arrayLong[0];

    for (int i = 0; i < timestampLength; i++) {
      max = (arrayLong[i] > max) ? arrayLong[i] : max;
    }

    return max;
  }

  private double calculateAvgValue (long[] arrayLong) {

    long sum = 0;

    for (int i = 0; i < timestampLength; i++) {
      sum += arrayLong[i];
    }

    return sum / (double) arrayLong.length;
  }

  private int calculateAvgValue (List<Integer> list) {

    int sum = 0;
    int listSize = list.size();
    if (!list.isEmpty()) {
      for (int i = 0; i < listSize; i++) {
        sum += list.get(i);
      }
      return sum / list.size();
    }
    return sum;
  }

  public int getHeartRate () {

    return calculateAvgValue(heartRates);
  }

  public void reset () {

    signals = new double[21];
    pulseCount = 0;
    pulseDetected = false;
    fingerOnCount = 0;
    fingerDetected = false;
    heartRates = new ArrayList<>();
    correlations = new double[] { 0.0, 0.0, 0.0 };
    timeStamps = new long[] { 0, 0, 0 };
    lastTime = 0;
    timeSinceLastPulse = 0;
  }

  public void setBeatEventListener (OnBeatEventListener eventListener) {

    beatEventListener = eventListener;
  }
}
