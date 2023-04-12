package com.qompium.fibricheck_camera_sdk.measurement;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.qompium.fibricheck_camera_sdk.measurement.Quadrant.QUADRANT_COLS;
import static com.qompium.fibricheck_camera_sdk.measurement.Quadrant.QUADRANT_ROWS;

public class MeasurementData implements Serializable {

  public double heartrate;

  public int attempts;

  public List<List<YuvList>> quadrants;

  public HashMap<String, Object> technical_details;

  public List<Integer> time;

  public MotionData acc;

  public MotionData rotation;

  public MotionData grav;

  public MotionData gyro;

  public boolean skippedPulseDetection = false;

  public boolean skippedFingerDetection = false;

  public boolean skippedMovementDetection = false;

  public Number measurementTimestamp;

  public MeasurementData () {

    this.technical_details = new HashMap<>();
    this.time = new ArrayList<>();
    this.acc = new MotionData();
    this.grav = new MotionData();
    this.rotation = new MotionData();
    this.gyro = new MotionData();

    initQuadrants();
  }

  private void initQuadrants () {

    quadrants = new ArrayList<>();
    for (int i = 0; i < QUADRANT_ROWS; i++) {
      quadrants.add(i, new ArrayList<YuvList>());
      for (int j = 0; j < QUADRANT_COLS; j++) {
        quadrants.get(i).add(j, new YuvList());
      }
    }
  }

  public void addQuadrant (Quadrant quadrant) {

    for (int i = 0; i < QUADRANT_ROWS; i++) {
      for (int j = 0; j < QUADRANT_COLS; j++) {
        quadrants.get(i).get(j).addYUV(quadrant.getYuv(i, j));
      }
    }
  }

  public void addAcc (float[] data) {

    this.acc.addData(data);
  }

  public void addGyro (float[] data) {

    this.gyro.addData(data);
  }

  public void addRotation(float[] data) {

    this.rotation.addData(data);
  }

  public void addGrav(float[] data) {

    this.grav.addData(data);
  }

  public ArrayList<Double> getVList () {

    return this.getVList();
  }

  public class MotionData implements Serializable {

    public List<Float> x;

    public List<Float> y;

    public List<Float> z;

    public MotionData () {

      this.x = new ArrayList<>();
      this.y = new ArrayList<>();
      this.z = new ArrayList<>();
    }

    public void addData(float[] data) {
      this.x.add(data[0]);
      this.y.add(data[1]);
      this.z.add(data[2]);
    }
  }
}
