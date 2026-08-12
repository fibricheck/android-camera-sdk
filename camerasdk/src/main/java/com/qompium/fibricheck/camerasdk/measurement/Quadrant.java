package com.qompium.fibricheck.camerasdk.measurement;

import java.io.Serializable;

/**
 * Object for storing one frame of Yuv Quadrants
 * This wil later be sorted and merged in a quadrant list
 */
public class Quadrant implements Serializable {
  public static final int QUADRANT_ROWS = 4;
  public static final int QUADRANT_COLS = 4;

  private final Yuv[][] quadrants;
  private final int rows;
  private final int cols;

  public double avgY;
  public double frameSize;

  public Quadrant() {
    this(QUADRANT_ROWS, QUADRANT_COLS);
  }

  public Quadrant(int rows, int cols) {
    this.rows = rows;
    this.cols = cols;
    quadrants = new Yuv[rows][cols];
  }

  public Yuv getYuv(int row, int col) {
    return quadrants[row][col];
  }

  public void processDataArray(int[][][] quadrantDataArray) {
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        quadrants[i][j] = new Yuv(quadrantDataArray[i][j], frameSize);
      }
    }
  }
}
