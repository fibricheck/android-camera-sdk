package com.qompium.fibricheck_camera_sdk.measurement;

import java.io.Serializable;

/**
 * Object for storing one frame of Yuv Quadrants
 * This wil later be sorted and merged in a quadrant list
 */
public class Quadrant implements Serializable {

  public static final int QUADRANT_ROWS = 4;

  public static final int QUADRANT_COLS = 4;

  private Yuv[][] quadrants;

  public double avgY;

  public double frameSize;

  public Quadrant() {

    quadrants = new Yuv[QUADRANT_ROWS][QUADRANT_COLS];
  }

  public Yuv getYuv(int row, int col) {

    return quadrants[row][col];
  }

  public void processDataArray(int[][][] quadrantDataArray) {

    for (int i = 0; i < QUADRANT_ROWS; i++) {
      for (int j = 0; j < QUADRANT_COLS; j++) {
        quadrants[i][j] = new Yuv(quadrantDataArray[i][j], frameSize);
      }
    }
  }
}