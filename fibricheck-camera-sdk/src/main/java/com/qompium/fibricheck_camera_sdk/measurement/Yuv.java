package com.qompium.fibricheck_camera_sdk.measurement;

public class Yuv {

  public double y;

  public double u;

  public double v;

  public Yuv (double y, double u, double v) {

    this.y = y;
    this.u = u;
    this.v = v;
  }

  public Yuv (int[] yuvArray, double frameSize) {

    this.y = yuvArray[0] / frameSize;
    this.u = yuvArray[1] / frameSize;
    this.v = yuvArray[2] / frameSize;
  }
}
