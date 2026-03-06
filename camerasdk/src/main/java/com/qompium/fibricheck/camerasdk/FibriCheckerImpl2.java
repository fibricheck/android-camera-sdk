package com.qompium.fibricheck.camerasdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.qompium.fibricheck.camerasdk.listeners.EmptyActivityLifecycleCallbacks;
import com.qompium.fibricheck.camerasdk.listeners.EmptySurfaceTextureListener;
import com.qompium.fibricheck.camerasdk.measurement.Quadrant;
import com.qompium.fibricheck.camerasdk.measurement.QuadrantColor;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FibriCheckerImpl2 extends FibriChecker {
  private static final String TAG = "FibriChecker2";

  private final TextureView.SurfaceTextureListener mSurfaceTextureListener;
  private final TextureView mTextureView;
  private CameraDevice mCameraDevice;
  private CameraCaptureSession mPreviewSession;

  private Size mPreviewSize;
  private boolean isAdvancedCamera2Implementation = false;

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread mBackgroundThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler mBackgroundHandler;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

  /**
   * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
   */
  private final CameraDevice.StateCallback mStateCallback;
  private CameraCharacteristics mCharacteristics;

  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener;
  private CaptureRequest.Builder mCaptureRequest;
  private ImageReader mImageReader;

  private final CameraCaptureSession.CaptureCallback mCaptureCallback;

  public FibriCheckerImpl2(ViewGroup viewGroup, Context context, FibriBuilder builder) {
    super(viewGroup, context, builder);

    mTextureView = new TextureView(context);
    viewGroup.addView(mTextureView);

    ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(createLifecycleListener());

    this.mSurfaceTextureListener = createSurfaceTextureListener();
    this.mStateCallback = createStateCallback();
    this.mOnImageAvailableListener = createOnImageAvailableListener();
    this.mCaptureCallback = createCaptureCallback();
  }

  @Override
  public void start() {
    startBackgroundThread();
    state = State.DETECTING_FINGER;

    if (mTextureView.isAvailable()) {
      activateCamera();
    } else {
      Log.e(TAG, "No texture was available to attach on");
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  private static Size chooseVideoSize(Size[] choices) {
    Arrays.sort(choices, Comparator.comparingLong(s -> s.getWidth() * (long) s.getHeight()));
    return choices[0];
  }

  private QuadrantColor calculateAverageYUV(Image yuvImage) {
    int ySum = 0, uSum = 0, vSum = 0;
    double yAvg, uAvg, vAvg;
    double stdDevY;
    int[] histY = new int[256];

    Quadrant quadrant = new Quadrant();
    int[][][] quadrantDataArray = new int[quadrantRows][quadrantCols][3];

    try {
      if (yuvImage == null) {
        Log.w(TAG, "YUV image was null..");
        return null;
      }

      int width = yuvImage.getWidth();
      int height = yuvImage.getHeight();
      double frameSize = width * height;

      int quadrantWidth = width / quadrantCols;
      int quadrantHeight = height / quadrantRows;

      Image.Plane yPlane = yuvImage.getPlanes()[0];
      Image.Plane uPlane = yuvImage.getPlanes()[1];
      Image.Plane vPlane = yuvImage.getPlanes()[2];

      ByteBuffer yBuf = yPlane.getBuffer();
      ByteBuffer uBuf = uPlane.getBuffer();
      ByteBuffer vBuf = vPlane.getBuffer();

      yBuf.rewind();
      uBuf.rewind();
      vBuf.rewind();

      //The U/V planes are guaranteed to have the same row stride and pixel stride.
      //In particular, uPlane.getRowStride() == vPlane.getRowStride() and uPlane.getPixelStride() == vPlane.getPixelStride();
      int yRowStride = yPlane.getRowStride();
      int uvRowStride = vPlane.getRowStride();

      int yPixStride = yPlane.getPixelStride();
      int uvPixStride = vPlane.getPixelStride();

      byte[] yFullRow = new byte[yPixStride * (width - 1) + 1];
      byte[] uFullRow = new byte[uvPixStride * (width / 2 - 1) + 1];
      byte[] vFullRow = new byte[uvPixStride * (width / 2 - 1) + 1];

      int yValue, uValue, vValue;
      for (int i = 0; i < height; i++) {
        int halfH = i / 2;
        yBuf.position(yRowStride * i);
        yBuf.get(yFullRow);
        uBuf.position(uvRowStride * halfH);
        uBuf.get(uFullRow);
        vBuf.position(uvRowStride * halfH);
        vBuf.get(vFullRow);
        for (int j = 0; j < width; j++) {

          int halfW = j / 2;
          yValue = yFullRow[yPixStride * j] & 0xFF;
          uValue = uFullRow[uvPixStride * halfW] & 0xFF;
          vValue = vFullRow[uvPixStride * halfW] & 0xFF;

          yValue = yValue > 0 ? yValue : 0;
          uValue = uValue > 0 ? uValue : 0;
          vValue = vValue > 0 ? vValue : 0;

          quadrantDataArray[i / quadrantHeight][j / quadrantWidth][0] += yValue;
          quadrantDataArray[i / quadrantHeight][j / quadrantWidth][1] += uValue;
          quadrantDataArray[i / quadrantHeight][j / quadrantWidth][2] += vValue;

          histY[yValue]++;

          ySum += yValue;
          uSum += uValue;
          vSum += vValue;
        }
      }

      yAvg = ySum / frameSize;
      uAvg = uSum / frameSize;
      vAvg = vSum / frameSize;

      quadrant.avgY = yAvg;
      quadrant.frameSize = quadrantHeight * quadrantWidth;
      quadrant.processDataArray(quadrantDataArray);

      long sigmaY = 0;
      for (int i = 0; i < 256; i++) {
        sigmaY += (long) (histY[i] * Math.pow(i - yAvg, 2));
      }
      stdDevY = Math.sqrt(sigmaY / frameSize);

      //Log.e("std", String.format("%f, %f, %f", yAvg, vAvg, stdDevY));
    } catch (NullPointerException e) {
      Log.e(TAG, "NPE while calculating YUV average");
      return null;
    } finally {
      if (yuvImage != null) {
        yuvImage.close();
      }
    }

    return new QuadrantColor(quadrant, new double[]{yAvg, uAvg, vAvg, stdDevY});
  }

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread() {
    try {
      if (mBackgroundThread != null) {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundThread = null;
        mBackgroundHandler = null;
      }
    } catch (InterruptedException | RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  /**
   * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
   */
  public void activateCamera() {
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      String cameraId = manager.getCameraIdList()[0];

      // Choose the sizes for camera preview and video recording
      mCharacteristics = manager.getCameraCharacteristics(cameraId);
      StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      hardwareLevel = mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      if (hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
          && hardwareLevel > CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
        isAdvancedCamera2Implementation = true;
      }
      Log.i(TAG, "Hardwarelevel: " + hardwareLevel);

      Size mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
      mPreviewSize = chooseVideoSize(map.getOutputSizes(SurfaceTexture.class));
      cameraResolution = mVideoSize.toString();

      Log.i(TAG, "Chosen video/preview size: " + mVideoSize + "/" + mPreviewSize.toString());
      mImageReader = ImageReader.newInstance(mVideoSize.getWidth(), mVideoSize.getHeight(), ImageFormat.YUV_420_888, 4);
      mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
      manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      //activity.finish();
      Log.e(TAG, e.toString());
    } catch (InterruptedException | SecurityException | NullPointerException e) {
      Log.e(TAG, e.toString());
    } catch (RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  public void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();
      closePreviewSession();
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted while trying to lock camera closing.");
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  /**
   * Start the camera preview.
   */
  private void startPreview() {
    if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
      return;
    }

    try {
      closePreviewSession();

      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
      mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      if (flashEnabled) {
        mCaptureRequest.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
      }

      Surface mImageSurface = mImageReader.getSurface();
      Surface textureSurface = new Surface(texture);
      mCaptureRequest.addTarget(mImageSurface);
      mCaptureRequest.addTarget(textureSurface);

      mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, textureSurface), new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
          mPreviewSession = cameraCaptureSession;
          updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
          Log.e(TAG, "configure failed");
        }
      }, null);

      applyCameraSettings();
    } catch (SecurityException | IllegalStateException | CameraAccessException |
             NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  /**
   * Update the camera preview. {@link #startPreview()} needs to be called in advance.
   */
  private void updatePreview() {

    if (null == mCameraDevice) {
      return;
    }
    try {
      //setUpCaptureRequestBuilder(mPreviewBuilder);
      HandlerThread thread = new HandlerThread("CameraPreview");
      thread.start();
      mPreviewSession.setRepeatingRequest(mCaptureRequest.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, e.toString());
    } catch (IllegalStateException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  public void onFrameReceived(final Quadrant quadrantData, final double[] yuvData, final long timestamp) {
    ((Activity) context).runOnUiThread(() -> handleStates(quadrantData, yuvData, getMotionData(), timestamp));
  }

  private void closePreviewSession() {
    if (mPreviewSession != null) {
      mPreviewSession.close();
      mPreviewSession = null;
    }
  }

  private void updateCameraValues(CaptureResult result) {
    if (!isAdvancedCamera2Implementation) {
      return;
    }

    cameraSettings.onSettingsChanged(result);
  }

  @Override
  protected void applyCameraSettings() {
    if (mCaptureRequest == null) {
      Log.e(TAG, "previewBuilder was null");
      return;
    }

    applyExposure();
    applyWhiteBalance();
    applyFocus();
    applyRequest();
  }

  private void applyExposure() {
    if (cameraSettings.isAutoExposure()) {
      mCaptureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
      mCaptureRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      mCaptureRequest.set(CaptureRequest.SENSOR_SENSITIVITY, null);
      mCaptureRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null);
      Log.d(TAG, "Exposure Auto");
      return;
    }

    mCaptureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
    if (!isAdvancedCamera2Implementation) {
      mCaptureRequest.set(CaptureRequest.CONTROL_AE_LOCK, true);
      return;
    }

    Log.d(TAG, "Exposure Locked ISO: " + cameraSettings.getIso() + ", Time: " + cameraSettings.getExposureTime());
    mCaptureRequest.set(CaptureRequest.SENSOR_SENSITIVITY, cameraSettings.getIso());
    mCaptureRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, cameraSettings.getExposureTime());
    mCaptureRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
  }

  private void applyWhiteBalance() {
    if (cameraSettings.isAutoWhiteBalance()) {
      mCaptureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
      mCaptureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
      mCaptureRequest.set(CaptureRequest.COLOR_CORRECTION_GAINS, null);
      Log.d(TAG, "White Balance Auto");
      return;
    }

    Log.d(TAG, "White Balance Locked: " + cameraSettings.getWhiteBalance());
    mCaptureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
    mCaptureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
    mCaptureRequest.set(CaptureRequest.COLOR_CORRECTION_GAINS, cameraSettings.getWhiteBalanceRggb());
  }

  private void applyFocus() {
    if (cameraSettings.isAutoFocus()) {
      mCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
      mCaptureRequest.set(CaptureRequest.LENS_FOCUS_DISTANCE, null);
      mCaptureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
      Log.d(TAG, "Focus Auto");
      return;
    }

    mCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
    mCaptureRequest.set(CaptureRequest.LENS_FOCUS_DISTANCE, cameraSettings.getFocus());
    mCaptureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
    Log.d(TAG, "Focus Locked " + cameraSettings.getFocus());
  }

  private void applyRequest() {
    try {
      mPreviewSession.setRepeatingRequest(mCaptureRequest.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  public CameraSettingsInfo getCameraInfo() {
    try {
      CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
      String cameraId = manager.getCameraIdList()[0];

      // Choose the sizes for camera preview and video recording
      CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
      return CameraSettingsInfo.Companion.from(chars);
    } catch (Exception e) {
      return null;
    }
  }

  private Application.ActivityLifecycleCallbacks createLifecycleListener() {
    return new EmptyActivityLifecycleCallbacks() {
      @Override
      public void onActivityPaused(@NonNull Activity activity) {
        Log.d(TAG, "closing camera reason: activity paused");
        closeCamera();
        stopBackgroundThread();
      }

      @Override
      public void onActivityDestroyed(@NonNull Activity activity) {
        clearResources();
      }
    };
  }

  private TextureView.SurfaceTextureListener createSurfaceTextureListener() {
    return new EmptySurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        activateCamera();
      }
    };
  }

  private CameraDevice.StateCallback createStateCallback() {
    return new CameraDevice.StateCallback() {
      @Override
      public void onOpened(@NonNull CameraDevice cameraDevice) {
        mCameraDevice = cameraDevice;
        startPreview();
        mCameraOpenCloseLock.release();
      }

      @Override
      public void onDisconnected(@NonNull CameraDevice cameraDevice) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }

      @Override
      public void onError(@NonNull CameraDevice cameraDevice, int error) {
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }
    };
  }

  private ImageReader.OnImageAvailableListener createOnImageAvailableListener() {
    return reader -> {
      Image img = reader.acquireLatestImage();
      long sampleTimestamp = SystemClock.uptimeMillis();

      QuadrantColor quadrantColor = calculateAverageYUV(img);
      if (quadrantColor != null) {
        onFrameReceived(quadrantColor.quadrant, quadrantColor.yuvData, sampleTimestamp);
      }
    };
  }

  private CameraCaptureSession.CaptureCallback createCaptureCallback() {
    return new CameraCaptureSession.CaptureCallback() {
      @Override
      public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        updateCameraValues(result);
      }
    };
  }
}
