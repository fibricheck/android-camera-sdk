package com.qompium.fibricheck.camerasdk;

import android.content.Context;
import android.hardware.Sensor;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.util.Log;
import com.qompium.fibricheck.camerasdk.filters.FirFilter;
import com.qompium.fibricheck.camerasdk.filters.SGFilter;
import com.qompium.fibricheck.camerasdk.listeners.BeatListener;
import com.qompium.fibricheck.camerasdk.listeners.CameraListener;
import com.qompium.fibricheck.camerasdk.listeners.FibriListener;
import com.qompium.fibricheck.camerasdk.listeners.IFibriListener;
import com.qompium.fibricheck.camerasdk.listeners.OnBeatEventListener;
import com.qompium.fibricheck.camerasdk.listeners.RawDataListener;
import com.qompium.fibricheck.camerasdk.listeners.SensorListener;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementRaw;
import com.qompium.fibricheck.camerasdk.measurement.Quadrant;
import com.qompium.fibricheck.camerasdk.models.CameraSettings;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInput;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsState;
import com.qompium.fibricheck.camerasdk.models.ProcessMeasurementWorker;
import com.qompium.fibricheck.camerasdk.utils.CameraUtils;
import com.qompium.fibricheck.camerasdk.utils.LabelInfo;

import java.util.ArrayList;
import java.util.Map;

import static com.qompium.fibricheck.camerasdk.listeners.SensorListener.SENSOR_LISTENER_DATA_ACC;

import org.jetbrains.annotations.NotNull;

public abstract class FibriChecker implements CameraListener {
  private static final int MOVING_WINDOW_SIZE = 7; // used for Savitzky Golay filter

  private static final int CALIBRATION_DELAY = 1000;
  private static final long ONE_SECOND_NS = 1000000000L;

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

  // Measurement start time using SystemClock.uptimeMillis() to have a monotonic
  // source
  private long measurementStartTime;
  private long calibrationStartTime;

  private FirFilter firFilter;
  private SGFilter sgFilter;

  private BeatListener beatListener;
  private IFibriListener fibriListener = new FibriListener();
  private SensorListener sensorListener;
  protected RawDataListener rawDataListener = null;

  private State previousState = State.INITIAL;
  private ArrayList<MeasurementRaw> measurementRawList = new ArrayList<>();
  private MeasurementData measurementData;

  private Event event = Event.INIT;

  private int attempts = 0;

  private boolean skippedPulseDetection = false;
  private boolean skippedFingerDetection = false;
  private boolean skippedMovementDetection = false;

  protected String cameraResolution;

  int hardwareLevel;

  protected CameraSettings cameraSettings = new CameraSettings();

  Context context;
  ViewGroup viewGroup;

  State state = State.INITIAL;

  private ProcessMeasurementWorker mMeasurementWorker = new ProcessMeasurementWorker();

  FibriChecker(ViewGroup viewGroup, Context context, FibriBuilder builder) {
    this.viewGroup = viewGroup;
    this.context = context;
    exportBuilderData(builder);
    init();
  }

  public static @NotNull Map<String, String> getLabel() {
    return LabelInfo.Companion.getLabel();
  }

  private void init() {
    // Values calculated in MathLab to get a LP/HP/BP/Notch-filter
    firFilter = new FirFilter(new double[] { 1.0, 0.0, 0.0, 0.0 }, new double[] { 1.0, 1.0, 1.0, 1.0 });

    sgFilter = new SGFilter(MOVING_WINDOW_SIZE);

    initBeatListener();
    initializeListeners();
  }

  private void initBeatListener() {
    this.beatListener = new BeatListener(minYValue, maxYValue, maxStdDevYValue, minVValue);
    beatListener.setBeatEventListener(new OnBeatEventListener() {

      @Override
      public void onFingerDetected() {
        event = Event.FINGER_DETECTED;
        fibriListener.onFingerDetected();
      }

      @Override
      public void onFingerRemoved(double y, double v, double stdDevY) {
        if (fingerDetectionExpiryTime != 0) {
          event = Event.FINGER_REMOVED;
          fibriListener.onFingerRemoved(y, v, stdDevY);
        }
      }

      @Override
      public void onPulseDetected() {
        event = Event.PULSE_DETECTED;
        fibriListener.onPulseDetected();
      }

      @Override
      public void onHeartBeat(int value) {
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
    this.cameraSettings = builder.manualCameraSettings;

    if (builder.fibriListener != null) {
      this.fibriListener = builder.fibriListener;
    } else {
      this.fibriListener = new FibriListener();
    }
    this.rawDataListener = builder.rawDataListener;

    this.skippedMovementDetection = !builder.movementDetectionEnabled;
  }

  public void record() {
    stop();
    state = State.DETECTING_FINGER;
    start();
  }

  protected abstract void start();

  public void preview() {
    stop();
    state = State.PREVIEW;
    start();
  }

  public void startRecording() {
    if (!calibrationReadyDispatched) {
      throw new IllegalStateException("Measurement must be calibrated to start a recording");
    }
    this.event = Event.START_RECORDING;
  }

  abstract public void stop();

  public void destroy() {
    reset();
    destroyListeners();
    state = State.INITIAL;
  }

  protected abstract void applyCameraSettings();

  void lockSettings() {
    this.cameraSettings.setCameraSettingsState(CameraSettingsState.Recording);
    applyCameraSettings();
  }

  void unlockSettings() {
    this.cameraSettings.setCameraSettingsState(CameraSettingsState.Calibrating);
    applyCameraSettings();
  }

  public abstract CameraSettingsInfo getCameraInfo();

  public void setCameraSettings(CameraSettingsInput settings) {
    this.cameraSettings.set(settings);
    applyCameraSettings();
  }

  public void setFibriListener(IFibriListener listener) {
    fibriListener = listener;
  }

  protected void startMeasurement(long startTimestamp) {
    measurementData = new MeasurementData();
    measurementStartTime = startTimestamp;
    measurementData.measurementTimestamp = System.currentTimeMillis();
    attempts++;
  }

  @Override
  public void onCameraDestroyed() {
    Log.d(TAG, "closing camera reason: camera destroyed");
    reset();
    destroy();
  }

  protected void handleStates(final Quadrant quadrantData, final double[] yuvData,
      final float[][] motionData, final long timestamp) {
    double dataPoint;

    switch (state) {
      case DETECTING_FINGER:
        if (previousState != State.DETECTING_FINGER) {
          unlockSettings();
          reset();
          fingerDetectionStartTime = SystemClock.uptimeMillis();
          previousState = State.DETECTING_FINGER;
        }

        if (fingerDetectionExpiryTime == 0 || event == Event.FINGER_DETECTED
            || event == Event.FINGER_DETECTION_TIME_EXPIRED) {
          state = State.DETECTING_PULSE;
          break;
        }

        checkFingerDetectionTimer();
        processData(yuvData);
        break;
      case DETECTING_PULSE:
        if (event == Event.FINGER_REMOVED) {
          state = State.DETECTING_FINGER;
          break;
        }
        if (pulseDetectionExpiryTime == 0 || event == Event.PULSE_DETECTED
            || event == Event.PULSE_DETECTION_TIME_EXPIRED) {
          state = State.CALIBRATING;
          break;
        }

        if (previousState != State.DETECTING_PULSE) {
          pulseDetectionStartTime = SystemClock.uptimeMillis();
          previousState = State.DETECTING_PULSE;
        }

        checkPulseDetectionTimer();
        // remove --> issue #FIB-608
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
          lockSettings();
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
          fibriListener.onMeasurementStart(timestamp);
          startMeasurement(timestamp);
          previousState = State.RECORDING;
        }
        checkForMeasurementCompletion();
        checkForMovements();

        measurementRawList.add(
            new MeasurementRaw(quadrantData, motionData, updateTimer(timestamp)));
        dataPoint = processData(yuvData);
        fibriListener.onSampleReady(dataPoint, yuvData[0]);

        break;
      case FINISHED:
        if (previousState != State.FINISHED) {
          finishMeasurement();
          fibriListener.onMeasurementFinished(timestamp);
          previousState = State.FINISHED;
        }

        break;
      case PREVIEW:
        if (previousState != State.PREVIEW) {
          fibriListener.onPreviewStarted();
          previousState = State.PREVIEW;
        }

        break;
    }
  }

  public void initializeListeners() {
    destroyListeners();

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

    sgFilter.pushData(value_filtered);// push data to the shift register for calculating SG-filter
    previousDataValue = yValue;

    final double dataPoint = sgFilter.applyFilter();
    beatListener.correlateWithValue(dataPoint, yuvData);

    return dataPoint;
  }

  private void checkForMovements() {
    double vector = calculateVector(sensorListener.getData()[SENSOR_LISTENER_DATA_ACC]);
    if (vector > upperMovementLimit || vector < lowerMovementLimit) {
      fibriListener.onMovementDetected();
      if (movementDetectionEnabled) {
        state = State.DETECTING_FINGER;
      }
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
    if (pulseDetectionExpiryTime > 0
        && (SystemClock.uptimeMillis() - pulseDetectionStartTime) > pulseDetectionExpiryTime) {
      skippedPulseDetection = true;
      event = Event.PULSE_DETECTION_TIME_EXPIRED;
      fibriListener.onPulseDetectionTimeExpired();
    }
  }

  private void checkFingerDetectionTimer() {
    if (fingerDetectionExpiryTime > 0
        && (SystemClock.uptimeMillis() - fingerDetectionStartTime) > fingerDetectionExpiryTime) {
      skippedFingerDetection = true;
      event = Event.FINGER_DETECTION_TIME_EXPIRED;
      fibriListener.onFingerDetectionTimeExpired();
    }
  }

  private void checkForMeasurementCompletion() {
    if (measurementData == null) {
      return;
    }

    final long measurementDelta = SystemClock.uptimeMillis() - measurementStartTime;
    if (measurementDelta >= sampleTime * 1000L) {
      event = Event.TIMER_ABOVE_SAMPLE_TIME;
    }
  }

  protected void reset() {
    calibrationReadyDispatched = false;
    measurementRawList = new ArrayList<>();
    cameraSettings.clear();
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

    if (measurementData == null) {
      fibriListener.onMeasurementError("Cancelled");
      return;
    }

    mMeasurementWorker.execute(
        measurementData,
        measurementRawList,
        gyroEnabled,
        accEnabled,
        rotationEnabled,
        gravEnabled,
        processedMeasurement -> {
          onMeasurementProcessed(processedMeasurement);
          return null;
        },
        () -> {
          fibriListener.onMeasurementError("Cancelled");
          return null;
        }
    );
  }

  private void onMeasurementProcessed(MeasurementData data) {
    data.heartrate = beatListener.getHeartRate();
    data.technical_details.put("camera_hardware_level", CameraUtils.Companion.getStringFromHardwareLevel(hardwareLevel));

    if (cameraResolution != null) {
      data.technical_details.put("camera_resolution", cameraResolution);
    }

    data.cameraSettings = cameraSettings.toOutput();
    cameraSettings.addTo(data.technical_details);

    data.attempts = attempts;
    data.skippedPulseDetection = skippedPulseDetection;
    data.skippedFingerDetection = skippedFingerDetection;
    data.skippedMovementDetection = !movementDetectionEnabled;

    fibriListener.onMeasurementProcessed(data);
  }

  public void setFibriListener(FibriListener fibriListener) {
    this.fibriListener = fibriListener;
  }

  protected enum State {
    INITIAL, PREVIEW, DETECTING_FINGER, DETECTING_PULSE, CALIBRATING, RECORDING, FINISHED,
  }

  private enum Event {
    INIT, FINGER_DETECTED, FINGER_REMOVED, PULSE_DETECTED, TIMER_ABOVE_SAMPLE_TIME, PULSE_DETECTION_TIME_EXPIRED,
    FINGER_DETECTION_TIME_EXPIRED, START_RECORDING
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
    private RawDataListener rawDataListener = null;

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

    private CameraSettings manualCameraSettings = new CameraSettings();

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

    public FibriBuilder rawDataListener(RawDataListener rawDataListener) {
      this.rawDataListener = rawDataListener;
      return this;
    }

    public FibriBuilder cameraSettings(CameraSettings cameraSettings) {
      this.manualCameraSettings = cameraSettings;
      return this;
    }

    public FibriChecker build() throws IllegalStateException {
      return (android.os.Build.VERSION.SDK_INT >= 22) ? new FibriCheckerImpl2(viewGroup, context, this)
          : new FibriCheckerImpl1(viewGroup, context, this);
    }
  }
}
