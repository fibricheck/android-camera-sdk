package com.qompium.fibricheck_camera_sdk;

import android.content.Context;
import android.hardware.Sensor;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.view.ViewGroup;
import com.qompium.fibricheck_camera_sdk.filters.FirFilter;
import com.qompium.fibricheck_camera_sdk.filters.SGFilter;
import com.qompium.fibricheck_camera_sdk.listeners.BeatListener;
import com.qompium.fibricheck_camera_sdk.listeners.CameraListener;
import com.qompium.fibricheck_camera_sdk.listeners.FibriListener;
import com.qompium.fibricheck_camera_sdk.listeners.IFibriListener;
import com.qompium.fibricheck_camera_sdk.listeners.OnBeatEventListener;
import com.qompium.fibricheck_camera_sdk.listeners.SensorListener;
import com.qompium.fibricheck_camera_sdk.measurement.MeasurementData;
import com.qompium.fibricheck_camera_sdk.measurement.MeasurementRaw;
import com.qompium.fibricheck_camera_sdk.measurement.Quadrant;
import java.util.ArrayList;
import java.util.Collections;

import static com.qompium.fibricheck_camera_sdk.listeners.SensorListener.SENSOR_LISTENER_DATA_ACC;
import static com.qompium.fibricheck_camera_sdk.listeners.SensorListener.SENSOR_LISTENER_DATA_GRAV;
import static com.qompium.fibricheck_camera_sdk.listeners.SensorListener.SENSOR_LISTENER_DATA_GYRO;
import static com.qompium.fibricheck_camera_sdk.listeners.SensorListener.SENSOR_LISTENER_DATA_ROTATION;

public abstract class FibriChecker implements CameraListener {

  private static final int MOVING_WINDOW_SIZE = 7; //used for Savitzky Golay filter

  private static final int CALIBRATION_DELAY = 1000;

  private static final String TAG = "FibriChecker";

  public int sampleTime;

  public int pulseDetectionExpiryTime;

  public int fingerDetectionExpiryTime;

  public int minYValue;

  public int maxYValue;

  public int minVValue;

  public int maxStdDevYValue;

  public int upperMovementLimit;

  public int lowerMovementLimit;

  public boolean flashEnabled;

  public boolean gyroEnabled;

  public boolean accEnabled;

  public boolean gravEnabled;

  public boolean rotationEnabled;

  public boolean movementDetectionEnabled;

  public boolean waitForStartRecordingSignal;

  public int quadrantCols;

  public int quadrantRows;

  private boolean calibrationReadyDispatched = false;

  private long pulseDetectionStartTime;

  private long fingerDetectionStartTime;

  private double previousDataValue = 0;

  private int previousTime;

  private long measurementStartTime;

  private long calibrationStartTime;

  private FirFilter firFilter;

  private SGFilter sgFilter;

  private BeatListener beatListener;

  private IFibriListener fibriListener = new FibriListener();

  private SensorListener sensorListener;

  private State previousState = State.DETECTING_FINGER;

  private ArrayList<MeasurementRaw> measurementRawList = new ArrayList<>();

  private MeasurementData measurementData;

  private Event event = Event.INIT;

  private int attempts = 0;

  private boolean skippedPulseDetection = false;

  private boolean skippedFingerDetection = false;

  private boolean skippedMovementDetection = false;

  protected String cameraResolution;

  int hardwareLevel;

  int currentIso = 0;

  long currentExposureTime = 0;

  Context context;

  ViewGroup viewGroup;

  State state = State.DETECTING_FINGER;

  FibriChecker(ViewGroup viewGroup, Context context, FibriBuilder builder) {

    this.viewGroup = viewGroup;
    this.context = context;
    exportBuilderData(builder);
    init();
  }

  private void init() {

    // Values calculated in MathLab to get a LP/HP/BP/Notch-filter
    firFilter =
      new FirFilter(new double[] { 1.0, 0.0, 0.0, 0.0 }, new double[] { 1.0, 1.0, 1.0, 1.0 });

    sgFilter = new SGFilter(MOVING_WINDOW_SIZE);

    initBeatListener();
    initializeListeners();
  }

  private void initBeatListener() {

    this.beatListener = new BeatListener(minYValue, maxYValue, maxStdDevYValue, minVValue);
    beatListener.setBeatEventListener(new OnBeatEventListener() {

      @Override public void onFingerDetected() {

        event = Event.FINGER_DETECTED;
        fibriListener.onFingerDetected();
      }

      @Override public void onFingerRemoved(double y, double v, double stdDevY) {

        if (fingerDetectionExpiryTime != 0) {
          event = Event.FINGER_REMOVED;
          fibriListener.onFingerRemoved(y, v, stdDevY);
        }
      }

      @Override public void onPulseDetected() {

        event = Event.PULSE_DETECTED;
        fibriListener.onPulseDetected();
      }

      @Override public void onHeartBeat(int value) {

        fibriListener.onHeartBeat(value);
      }
    });
  }

  private void exportBuilderData(FibriBuilder builder) {

    this.quadrantRows = builder.quadrantRows;

    this.quadrantCols = builder.quadrantCols;

    this.sampleTime = builder.sampleTime;

    this.previousTime = builder.sampleTime;

    this.flashEnabled = builder.flashEnabled;

    this.gyroEnabled = builder.gyroEnabled;

    this.accEnabled = builder.accEnabled;

    this.gravEnabled = builder.gravEnabled;

    this.rotationEnabled = builder.rotationEnabled;

    this.movementDetectionEnabled = builder.movementDetectionEnabled;

    this.upperMovementLimit = builder.upperMovementLimit;

    this.lowerMovementLimit = builder.lowerMovementLimit;

    this.waitForStartRecordingSignal = builder.waitForStartRecordingSignal;

    this.pulseDetectionExpiryTime = builder.pulseDetectionExpiryTime;

    this.fingerDetectionExpiryTime = builder.fingerDetectionExpiryTime;

    this.minYValue = builder.minYValue;

    this.maxYValue = builder.maxYValue;

    this.maxStdDevYValue = builder.maxStdDevYValue;

    this.minVValue = builder.minVValue;

    if (builder.fibriListener != null) {
      this.fibriListener = builder.fibriListener;
    } else {
      this.fibriListener = new FibriListener();
    }

    this.skippedMovementDetection = !builder.movementDetectionEnabled;
  }

  public abstract void start();

  public void startRecording() {

    if (!calibrationReadyDispatched) {
      throw new IllegalStateException("Measurement must be calibrated to start a recording");
    }
    this.event = Event.START_RECORDING;
  }

  public void stop() {

    if (state == State.RECORDING) {
      state = State.FINISHED;
    } else {
      clearResources();
    }
  }

  abstract void activateCamera();

  abstract void closeCamera();

  abstract void lockExposure();

  abstract void unlockExposure();

  public void setFibriListener(IFibriListener listener) {

    fibriListener = listener;
  }

  protected void startMeasurement() {

    measurementData = new MeasurementData();
    measurementStartTime = SystemClock.uptimeMillis();
    measurementData.measurementTimestamp = System.currentTimeMillis();
    attempts++;
  }

  @Override public void onCameraDestroyed() {

    closeCamera();
    reset();
    destroyListeners();
  }

  protected void handleStates(final Quadrant quadrantData, final double[] yuvData,
                              final float[][] motionData, final long timestamp) {

    double dataPoint;

    switch (state) {
      case DETECTING_FINGER:
        if (fingerDetectionExpiryTime == 0 || event == Event.FINGER_DETECTED || event == Event.FINGER_DETECTION_TIME_EXPIRED) {
          state = State.DETECTING_PULSE;
          break;
        }

        if (previousState != State.DETECTING_FINGER) {
          unlockExposure();
          reset();
          fingerDetectionStartTime = SystemClock.uptimeMillis();
          previousState = State.DETECTING_FINGER;
        }

        checkFingerDetectionTimer();

        processData(yuvData);
        break;
      case DETECTING_PULSE:
        if (event == Event.FINGER_REMOVED) {
          state = State.DETECTING_FINGER;
          break;
        }
        if (pulseDetectionExpiryTime == 0 || event == Event.PULSE_DETECTED || event == Event.PULSE_DETECTION_TIME_EXPIRED) {
          state = State.CALIBRATING;
          break;
        }

        if (previousState != State.DETECTING_PULSE) {
          pulseDetectionStartTime = SystemClock.uptimeMillis();
          previousState = State.DETECTING_PULSE;
        }

        checkPulseDetectionTimer();
        //remove --> issue #FIB-608
        dataPoint = processData(yuvData);
        fibriListener.onSampleReady(dataPoint, yuvData[0]);

        break;
      case CALIBRATING:
        if (event == Event.FINGER_REMOVED) {
          state = State.DETECTING_FINGER;
          break;
        }

        if (previousState != State.CALIBRATING) {
          calibrationStartTime = SystemClock.uptimeMillis();
          lockExposure();
          previousState = State.CALIBRATING;
        }

        dataPoint = processData(yuvData);
        fibriListener.onSampleReady(dataPoint, yuvData[0]);

        // Wait while the exposure lock is setting
        if ((SystemClock.uptimeMillis() - calibrationStartTime > CALIBRATION_DELAY)
          && !calibrationReadyDispatched) {
          fibriListener.onCalibrationReady();
          calibrationReadyDispatched = true;
        }

        // Wait for a go when waitForStartRecordingSignal is enabled
        if (calibrationReadyDispatched && !(waitForStartRecordingSignal && event != Event.START_RECORDING)) {
          state = State.RECORDING;
        }

        break;
      case RECORDING:
        if (event == Event.FINGER_REMOVED) {
          state = State.DETECTING_FINGER;
          break;
        }
        if (event == Event.TIMER_ABOVE_SAMPLE_TIME) {
          state = State.FINISHED;
          break;
        }

        if (previousState != State.RECORDING) {
          fibriListener.onMeasurementStart();
          startMeasurement();
          previousState = State.RECORDING;
        }

        checkForMeasurementCompletion();
        checkForMovements();
        dataPoint = processData(yuvData);

        measurementRawList.add(
          new MeasurementRaw(quadrantData, motionData, updateTimer(timestamp)));
        fibriListener.onSampleReady(dataPoint, yuvData[0]);

        break;
      case FINISHED:
        if (previousState != State.FINISHED) {
          finishMeasurement();
          fibriListener.onMeasurementFinished();
          previousState = State.FINISHED;
        }

        break;
      case ON_HOLD:
        if (previousState != State.ON_HOLD) {
          previousState = State.ON_HOLD;
        }

        break;
    }
  }

  public void initializeListeners() {

    sensorListener = new SensorListener(context);
    sensorListener.addListener(Sensor.TYPE_ACCELEROMETER);
    if (gyroEnabled) {
      sensorListener.addListener(Sensor.TYPE_GYROSCOPE);
    }
    if (rotationEnabled) {
      sensorListener.addListener(Sensor.TYPE_ROTATION_VECTOR);
    }
    if (gravEnabled) {
      sensorListener.addListener(Sensor.TYPE_GRAVITY);
    }
  }

  private double processData(double[] yuvData) {

    final double yValue = yuvData[0];
    final double value_diff = previousDataValue - yValue;
    final double value_filtered = firFilter.calculateOutput(value_diff);

    sgFilter.pushData(value_filtered);//push data to the shift register for calculating SG-filter
    previousDataValue = yValue;

    final double dataPoint = sgFilter.applyFilter();
    beatListener.correlateWithValue(dataPoint, yuvData);

    return dataPoint;
  }

  private void checkForMovements() {

    if (!movementDetectionEnabled) {
      return;
    }
    double vector = calculateVector(sensorListener.getData()[SENSOR_LISTENER_DATA_ACC]);
    if (vector > upperMovementLimit || vector < lowerMovementLimit) {
      fibriListener.onMovementDetected();
      state = State.DETECTING_FINGER;
    }
  }

  protected float[][] getMotionData() {

    return sensorListener.getData();
  }

  private double calculateVector(float[] data) {

    double sum = 0;

    for (int i = 0; i < data.length; i++) {
      sum += Math.pow(data[i], 2);
    }

    return Math.sqrt(sum);
  }

  private int updateTimer(long dataTimestamp) {

    int tms = (int) (dataTimestamp - measurementStartTime);
    int timeRemaining = (sampleTime) - (tms / 1000);

    if (timeRemaining != previousTime) {
      previousTime = timeRemaining;
      fibriListener.onTimeRemaining(timeRemaining);
    }

    return tms;
  }

  private void checkPulseDetectionTimer() {

    if (pulseDetectionExpiryTime > 0 && (SystemClock.uptimeMillis() - pulseDetectionStartTime) > pulseDetectionExpiryTime) {
      skippedPulseDetection = true;
      event = Event.PULSE_DETECTION_TIME_EXPIRED;
      fibriListener.onPulseDetectionTimeExpired();
    }
  }

  private void checkFingerDetectionTimer() {

    if (fingerDetectionExpiryTime > 0 && (SystemClock.uptimeMillis() - fingerDetectionStartTime) > fingerDetectionExpiryTime) {
      skippedFingerDetection = true;
      event = Event.FINGER_DETECTION_TIME_EXPIRED;
      fibriListener.onFingerDetectionTimeExpired();
    }
  }

  private void checkForMeasurementCompletion() {

    if (measurementData != null) {
      if (SystemClock.uptimeMillis() - measurementStartTime > sampleTime * 1000) {
        event = Event.TIMER_ABOVE_SAMPLE_TIME;
      }
    }
  }

  protected void reset() {

    calibrationReadyDispatched = false;
    measurementRawList = new ArrayList<>();
    beatListener.reset();
  }

  protected void destroyListeners() {
    try {
      if (sensorListener != null) {
        sensorListener.destroyListener();
      }
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
  }

  private void finishMeasurement() {

    destroyListeners();
    new ProcessRawMeasurementTask(measurementData, measurementRawList).execute();
  }

  protected void clearResources() {

    reset();
    closeCamera();
    destroyListeners();
  }

  public void setFibriListener(FibriListener fibriListener) {

    this.fibriListener = fibriListener;
  }

  private class ProcessRawMeasurementTask extends AsyncTask<String, Void, MeasurementData> {

    MeasurementData measurementData;

    ArrayList<MeasurementRaw> measurementRawList;

    public ProcessRawMeasurementTask(MeasurementData measurementData,
                                     ArrayList<MeasurementRaw> measurementRawList) {

      this.measurementData = measurementData;
      this.measurementRawList = measurementRawList;
      if (state == State.FINISHED) {
        closeCamera();
      }
    }

    @Override protected MeasurementData doInBackground(String... params) {

      Collections.sort(measurementRawList);
      for (MeasurementRaw m : measurementRawList) {
        updateMeasurement(m.quadrantData, m.motionData, m.timestamp);
      }

      measurementData.heartrate = beatListener.getHeartRate();
      measurementData.technical_details.put("camera_hardware_level",
        getStringFromHardwareLevel(hardwareLevel));

      if (cameraResolution != null) {
        measurementData.technical_details.put("camera_resolution", cameraResolution);
      }
      if (currentIso != 0) {
        measurementData.technical_details.put("camera_iso", currentIso);
      }
      if (currentExposureTime != 0) {
        measurementData.technical_details.put("camera_exposure_time", currentExposureTime);
      }

      measurementData.attempts = attempts;
      measurementData.skippedPulseDetection = skippedPulseDetection;
      measurementData.skippedFingerDetection = skippedFingerDetection;
      measurementData.skippedMovementDetection = !movementDetectionEnabled;

      return measurementData;
    }

    @Override protected void onPostExecute(MeasurementData measurementdata) {

      fibriListener.onMeasurementProcessed(measurementdata);
    }

    private String getStringFromHardwareLevel(int hardwareLevel) {

      switch (hardwareLevel) {
        case -1:
          return "camera1";
        case 0:
          return "camera2 - limited";
        case 1:
          return "camera2 - full";
        case 2:
          return "camera2 - legacy";
        case 3:
          return "camera2 - level3";
        default:
          return "undetected";
      }
    }

    private void updateMeasurement(Quadrant quadrant, float[][] motionData, int timestamp) {

      measurementData.addQuadrant(quadrant);
      if (gyroEnabled) {
        measurementData.addGyro(motionData[SENSOR_LISTENER_DATA_GYRO]);
      }
      if (accEnabled) {
        measurementData.addAcc(motionData[SENSOR_LISTENER_DATA_ACC]);
      }
      if (rotationEnabled) {
        measurementData.addRotation(motionData[SENSOR_LISTENER_DATA_ROTATION]);
      }
      if (gravEnabled) {
        measurementData.addGrav(motionData[SENSOR_LISTENER_DATA_GRAV]);
      }
      measurementData.time.add(timestamp);
    }
  }

  protected enum State {
    ON_HOLD, DETECTING_FINGER, DETECTING_PULSE, CALIBRATING, RECORDING, FINISHED,
  }

  private enum Event {
    INIT, FINGER_DETECTED, FINGER_REMOVED, PULSE_DETECTED, TIMER_ABOVE_SAMPLE_TIME, PULSE_DETECTION_TIME_EXPIRED, FINGER_DETECTION_TIME_EXPIRED, START_RECORDING
  }

  public static class FibriBuilder {

    private final ViewGroup viewGroup;

    private final Context context;

    private boolean flashEnabled = true;

    private boolean gyroEnabled = false;

    private boolean accEnabled = false;

    private boolean gravEnabled = false;

    private boolean rotationEnabled = false;

    private boolean movementDetectionEnabled = true;

    private FibriListener fibriListener;

    protected int quadrantRows = 4;

    protected int quadrantCols = 4;

    private int sampleTime = 60;

    private int pulseDetectionExpiryTime = 10000;

    private int upperMovementLimit = 12;

    private int lowerMovementLimit = 8;

    private int fingerDetectionExpiryTime = -1;

    private int minYValue = 20;

    private int maxYValue = 160;
  
    private int minVValue = 135;
  
    private int maxStdDevYValue = 42;  

    private boolean waitForStartRecordingSignal = false;

    public FibriBuilder(Context context, ViewGroup viewGroup) {

      this.context = context;
      this.viewGroup = viewGroup;
    }

    public FibriBuilder enableFlash(boolean state) {

      this.flashEnabled = state;
      return this;
    }

    public FibriBuilder enableGyro(boolean state) {

      this.gyroEnabled = state;
      return this;
    }

    public FibriBuilder enableAccelero(boolean state) {

      this.accEnabled = state;
      return this;
    }

    public FibriBuilder enableGravitation(boolean state) {

      this.gravEnabled = state;
      return this;
    }

    public FibriBuilder enableRotation(boolean state) {

      this.rotationEnabled = state;
      return this;
    }

    public FibriBuilder quadrantSize(int rows, int cols) {

      this.quadrantRows = rows;
      this.quadrantCols = cols;
      return this;
    }

    public FibriBuilder sampleTime(int seconds) {

      this.sampleTime = seconds;
      return this;
    }

    public FibriBuilder movementLimts(int upperMovementLimit, int lowerMovementLimit) {

      this.upperMovementLimit = upperMovementLimit;
      this.lowerMovementLimit = lowerMovementLimit;
      return this;
    }

    public FibriBuilder enableMovementDetection(boolean state) {

      this.movementDetectionEnabled = state;
      return this;
    }

    public FibriBuilder waitForStartRecordingSignal(boolean state) {

      this.waitForStartRecordingSignal = state;
      return this;
    }

    /**
     *
     * Set the time to wait until a pulse is detected
     * Set value to -1 for no expiryTime --> Always wait for a detected pulse
     * Set value to 0 to skip the pulse detection
     *
     * Default value = 10 seconds
     *
     * @param seconds Time to expire the finger detection
     * @return FibriBuilder
     */
    public FibriBuilder pulseDetectionExpiryTime(float seconds) {

      this.pulseDetectionExpiryTime = (int) (seconds * 1000);
      return this;
    }

    /**
     *
     * Set the time to wait until a finger is detected
     * Set value to -1 for no expiryTime --> Always wait for a detected finger
     * Set value to 0 to skip the finger detection
     *
     * Default value = -1
     *
     * @param seconds Time to expire the finger detection
     * @return FibriBuilder
     */
    public FibriBuilder fingerDetectionExpiryTime(float seconds) {

      this.fingerDetectionExpiryTime = (int) (seconds * 1000);
      return this;
    }

    public FibriBuilder fingerDetectionValues(int minYValue, int maxYValue, int maxStdDevYValue,
                                              int minVValue) {

      if (minYValue > 0) {
        this.minYValue = minYValue;
      }
      if (maxYValue > 0) {
        this.maxYValue = maxYValue;
      }
      if (maxStdDevYValue > 0) {
        this.maxStdDevYValue = maxStdDevYValue;
      }
      if (minVValue > 0) {
        this.minVValue = minVValue;
      }
      return this;
    }

    public FibriBuilder fibriListener(FibriListener fibriListener) {

      this.fibriListener = fibriListener;
      return this;
    }

    public FibriChecker build() throws IllegalStateException {

      return (android.os.Build.VERSION.SDK_INT >= 22) ? new FibriCheckerImpl2(viewGroup, context, this)
        : new FibriCheckerImpl1(viewGroup, context, this);
    }
  }
}
