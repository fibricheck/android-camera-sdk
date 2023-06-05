package com.qompium.fibricheck.camerasdk.measurement;

public class QuadrantColor {

  public Quadrant quadrant;

  public double[] yuvData;

  public QuadrantColor (Quadrant quadrant, double[] yuvData) {

    this.quadrant = quadrant;
    this.yuvData = yuvData;
  }
}