package com.qompium.fibricheck_camera_sdk.listeners;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.Arrays;

public class SensorListener implements SensorEventListener {

  public static int SENSOR_LISTENER_DATA_GYRO = 0;

  public static int SENSOR_LISTENER_DATA_ACC = 1;

  public static int SENSOR_LISTENER_DATA_ROTATION = 2;

  public static int SENSOR_LISTENER_DATA_GRAV = 3;

  private float[] gyroData = new float[3];

  private float[] accData = new float[3];

  private float[] gravData = new float[3];

  private float[] rotationData = new float[3];

  private float[] rotationMatrix = new float[9];

  private SensorManager mSensorManager;

  private HandlerThread mHandlerThread;

  private Handler handler;

  public SensorListener (Context context) {

    mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

    mHandlerThread = new HandlerThread("sensorThread");
    mHandlerThread.start();

    this.handler = new Handler(mHandlerThread.getLooper());
  }

  public void addListener (int sensorType) {

    mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_GAME, handler);
  }

  public void destroyListener () {

    if (mSensorManager != null) {
      mSensorManager.unregisterListener(this);
    }
    if (mHandlerThread.isAlive()) {
      mHandlerThread.quit();
    }
  }

  @Override public void onSensorChanged (SensorEvent event) {

    Sensor sensor = event.sensor;
    if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      this.accData = event.values;
    } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
      this.gyroData = event.values;
    } else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
      SensorManager.getOrientation(rotationMatrix, rotationData);
      convertToDegrees(rotationData);
    } else if (sensor.getType() == Sensor.TYPE_GRAVITY) {
      this.gravData = event.values;
    }
  }

  @Override public void onAccuracyChanged (Sensor sensor, int accuracy) {

  }

  public float[][] getData () {

    float[][] data = new float[4][3];
    data[SENSOR_LISTENER_DATA_GYRO] = Arrays.copyOf(gyroData, gyroData.length);
    data[SENSOR_LISTENER_DATA_ACC] = Arrays.copyOf(accData, accData.length);
    data[SENSOR_LISTENER_DATA_ROTATION] = Arrays.copyOf(rotationData, rotationData.length);
    data[SENSOR_LISTENER_DATA_GRAV] = Arrays.copyOf(gravData, gravData.length);

    return data;
  }

  private void convertToDegrees (float[] vector) {

    for (int i = 0; i < vector.length; i++) {
      vector[i] = Math.round(Math.toDegrees(vector[i]));
    }
  }
}
