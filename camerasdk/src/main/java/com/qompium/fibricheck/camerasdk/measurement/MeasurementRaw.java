package com.qompium.fibricheck.camerasdk.measurement;

public class MeasurementRaw implements Comparable<MeasurementRaw>{

  public Quadrant quadrantData;

  public double[] yuvData;

  public float[][] motionData;

  public int timestamp;

  public MeasurementRaw (Quadrant quadrantData, double[] yuvData, float[][] motionData, int timestamp) {

    this.quadrantData = quadrantData;
    this.yuvData = yuvData;
    this.motionData = motionData;
    this.timestamp = timestamp;
  }

  public MeasurementRaw (Quadrant quadrantData, float[][] motionData, int timestamp) {

    this.quadrantData = quadrantData;
    this.motionData = motionData;
    this.timestamp = timestamp;
  }

  @Override public int compareTo (MeasurementRaw measurementRaw) {

    return this.timestamp - measurementRaw.timestamp;
  }
}
