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
import androidx.annotation.RequiresApi;

import com.qompium.fibricheck.camerasdk.extensions.ImageKt;
import com.qompium.fibricheck.camerasdk.extensions.TotalCaptureResultKt;
import com.qompium.fibricheck.camerasdk.listeners.EmptyActivityLifecycleCallbacks;
import com.qompium.fibricheck.camerasdk.listeners.EmptySurfaceTextureListener;
import com.qompium.fibricheck.camerasdk.listeners.RawDataListener;
import com.qompium.fibricheck.camerasdk.measurement.Quadrant;
import com.qompium.fibricheck.camerasdk.measurement.QuadrantColor;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo;
import com.qompium.fibricheck.camerasdk.utils.CameraUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiresApi(21)
public class FibriCheckerImpl2 extends FibriChecker {
  private static final String TAG = "FibriChecker2";

  private final TextureView.SurfaceTextureListener mSurfaceTextureListener;
  private final TextureView mTextureView;

  private Size mPreviewSize;
  private boolean isAdvancedCamera2Implementation = false;

  private final HandlerThread mImageThread;
  private final Handler mImageHandler;
  private final HandlerThread mCaptureCallbackThread;
  private final Handler mCaptureCallbackHandler;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

  private CameraDevice mCameraDevice;
  private CameraCaptureSession mCameraSession;

  private CaptureRequest.Builder mCaptureRequest;
  private ImageReader mImageReader;
  private Map<String, String> mLastCameraData;

  /**
   * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
   */
  private final CameraDevice.StateCallback mStateCallback;
  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener;
  private final CameraCaptureSession.CaptureCallback mCaptureCallback;
  private final CameraCaptureSession.StateCallback mCaptureStateCallback;
  private final Application.ActivityLifecycleCallbacks mActivityCallback;

  public FibriCheckerImpl2 (ViewGroup viewGroup,Context context, FibriBuilder builder) {
    super(viewGroup, context, builder);

    mTextureView = new TextureView(context);
    viewGroup.addView(mTextureView);


    mActivityCallback = createLifecycleListener();
    mSurfaceTextureListener = createSurfaceTextureListener();
    mStateCallback = createStateCallback();
    mOnImageAvailableListener = createOnImageAvailableListener();
    mCaptureCallback = createCaptureCallback();
    mCaptureStateCallback = createCaptureStateCallback();

    ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(mActivityCallback);

    mImageThread = new HandlerThread("CameraBackground");
    mImageThread.start();
    mImageHandler = new Handler(mImageThread.getLooper());

    mCaptureCallbackThread = new HandlerThread("RawDataBackground");
    mCaptureCallbackThread.start();
    mCaptureCallbackHandler = new Handler(mCaptureCallbackThread.getLooper());
  }

  @Override
  public void destroy() {
    super.destroy();

    if (null != mImageReader) {
      Log.d(TAG, "[closeCamera] closing image reader");
      mImageReader.close();
      mImageReader = null;
    }

    stopThreads();
    if (context != null) {
      Application app = (Application) context.getApplicationContext();
      if (app != null) {
        app.unregisterActivityLifecycleCallbacks(mActivityCallback);
      }
    }
  }

  @Override public void start () {
    state = State.DETECTING_FINGER;
    activateCamera();
  }

  /**
   * Stops the background threads and their {@link Handler}.
   */
  private void stopThreads() {
    Log.d(TAG, "Stop background thread");
    try {
      mImageThread.quitSafely();
      mImageThread.join();

      mCaptureCallbackThread.quitSafely();
      mCaptureCallbackThread.join();
    } catch (InterruptedException | RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  public void activateCamera() {
    if (mCameraDevice != null) {
      Log.d(TAG, "[activateCamera] camera already active, skipping");
      applyCameraSettings();
      return;
    }

    if (mTextureView.isAvailable()) {
      _activateCamera();
      applyCameraSettings();
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  /**
   * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
   */
  private void _activateCamera () {
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    Log.d(TAG, "[_activateCamera] called");

    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }

      String cameraId = manager.getCameraIdList()[0];

      // Choose the sizes for camera preview and video recording
      CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
      StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      hardwareLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      if (hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
          && hardwareLevel > CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
        isAdvancedCamera2Implementation = true;
      }
      // Log.i(TAG, "Hardwarelevel: " + hardwareLevel);

      Size videoSize = CameraUtils.Companion.pickSmallest(map.getOutputSizes(MediaRecorder.class));
      mPreviewSize = CameraUtils.Companion.pickSmallest(map.getOutputSizes(SurfaceTexture.class));
      cameraResolution = videoSize.toString();

      // Log.i(TAG, "Chosen video/preview size: " + mVideoSize.toString() + "/" + mPreviewSize.toString());
      mImageReader = ImageReader.newInstance(videoSize.getWidth(), videoSize.getHeight(), ImageFormat.YUV_420_888, 4);
      mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mImageHandler);
      manager.openCamera(cameraId, mStateCallback, mImageHandler);
    } catch (CameraAccessException e) {
      //activity.finish();
      Log.e(TAG, e.toString());
    } catch (InterruptedException | SecurityException | NullPointerException e) {
      Log.e(TAG, e.toString());
    } catch (RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override public void closeCamera () {
    try {
      Log.d(TAG, "[closeCamera] called");
      mCameraOpenCloseLock.acquire();
      closeCameraSession();
      if (null != mCameraDevice) {
        Log.d(TAG, "[closeCamera] closing camera device");
        mCameraDevice.close();
        mCameraDevice = null;
      }
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted while trying to lock camera closing.");
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  @Override
  public void preview() {
    Log.d(TAG, "preview");
    super.preview();

    unlockSettings();
    activateCamera();
  }

  /**
   * Start the camera preview.
   */
  private void startCameraSession() {
    Log.d(TAG, "[startCameraSession] called");
    if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
      Log.d(TAG, "[startCameraSession] pre-conditions not met, early return");
      return;
    }

    try {
      closeCameraSession();

      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
      mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      Surface mImageSurface = mImageReader.getSurface();
      Surface textureSurface = new Surface(texture);
      mCaptureRequest.addTarget(mImageSurface);
      mCaptureRequest.addTarget(textureSurface);
      mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, textureSurface), mCaptureStateCallback, null);

      applyCameraSettings();
    } catch (SecurityException | IllegalStateException | CameraAccessException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  /**
   * Update the camera preview. {@link #startCameraSession()} needs to be called in advance.
   */
  private void updateCameraSession() {
    Log.d(TAG, "updatePreview");
    if (null == mCameraSession) {
      return;
    }
    try {
      mCameraSession.setRepeatingRequest(mCaptureRequest.build(), mCaptureCallback, mCaptureCallbackHandler);
    } catch (CameraAccessException | IllegalStateException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  private void closeCameraSession() {
    Log.d(TAG, "[closeCameraSession] called");
    if (mCameraSession == null) {
      return;
    }

    mCameraSession.close();
    mCameraSession = null;
  }

  private void updateCameraValues (CaptureResult result) {
    if (!isAdvancedCamera2Implementation) {
      return;
    }

    cameraSettings.onSettingsChanged(result);
  }

  @Override
  protected void applyCameraSettings() {
    Log.d(TAG, "[applyCameraSettings] called");
    if (mCaptureRequest == null) {
      Log.e(TAG, "[applyCameraSettings] capture request was null");
      return;
    }

    cameraSettings.apply(mCaptureRequest, isAdvancedCamera2Implementation);
    mCaptureRequest.set(CaptureRequest.FLASH_MODE, flashEnabled && state != State.PREVIEW ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);

    try {
      if (mCameraSession != null) {
        mCameraSession.setRepeatingRequest(mCaptureRequest.build(), mCaptureCallback, mCaptureCallbackHandler);
      }
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
    }
    catch (Exception e) {
      return null;
    }
  }

  public void setRawDataListener(RawDataListener listener) {
    super.rawDataListener = listener;
  }

  @Override public void onFrameReceived (final Quadrant quadrantData, final double[] yuvData, final long timestamp) {
    ((Activity)context).runOnUiThread(() -> handleStates(quadrantData, yuvData, getMotionData(), timestamp));
  }

  private void notifyRawDataIfEnabled(Image image, long sampleTimestamp) {
    if (!cameraSettings.getRawDataEnabled() || rawDataListener == null || image == null || mLastCameraData == null) {
      return;
    }

    byte[] rawImageData = ImageKt.toByteArray(image);
    mLastCameraData.put("image.width", String.valueOf(image.getWidth()));
    mLastCameraData.put("image.height", String.valueOf(image.getHeight()));
    mLastCameraData.put("image.cropRect", TotalCaptureResultKt.toCustomString(image.getCropRect()));
    mLastCameraData.put("measurement.sampleTimestamp", String.valueOf(sampleTimestamp));

    rawDataListener.onNewData(rawImageData, mLastCameraData);
    rawImageData = null;
  }

  private CameraCaptureSession.CaptureCallback createCaptureCallback() {
    return new CameraCaptureSession.CaptureCallback() {
      @Override
      public void onCaptureCompleted (@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        updateCameraValues(result);
        if (cameraSettings.getRawDataEnabled()) {
          mLastCameraData = TotalCaptureResultKt.toMap(result);
        }
      }
    };
  }

  private Application.ActivityLifecycleCallbacks createLifecycleListener() {
    return new EmptyActivityLifecycleCallbacks() {
      @Override public void onActivityPaused (@NonNull Activity activity) {
        Log.d(TAG, "closing camera reason: activity paused");
        closeCamera();
      }

      @Override public void onActivityDestroyed (@NonNull Activity activity) {
        Log.d(TAG, "activity destroyed");
        destroy();
      }
    };
  }

  private TextureView.SurfaceTextureListener createSurfaceTextureListener () {
    return new EmptySurfaceTextureListener() {
      @Override public void onSurfaceTextureAvailable (@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        activateCamera();
      }
    };
  }

  private CameraDevice.StateCallback createStateCallback() {
    return new CameraDevice.StateCallback() {
      @Override public void onOpened (@NonNull CameraDevice cameraDevice) {
        Log.d(TAG, "camera opened");
        mCameraDevice = cameraDevice;
        startCameraSession();
        mCameraOpenCloseLock.release();
      }

      @Override public void onDisconnected (@NonNull CameraDevice cameraDevice) {
        Log.d(TAG, "camera disconnected");
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }

      @Override public void onError (@NonNull CameraDevice cameraDevice, int error) {
        Log.d(TAG, "camera error: " + error);
        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }
    };
  }

  private CameraCaptureSession.StateCallback createCaptureStateCallback() {
    return new CameraCaptureSession.StateCallback() {
      @Override public void onConfigured (@NonNull CameraCaptureSession cameraCaptureSession) {
        mCameraSession = cameraCaptureSession;
        updateCameraSession();
      }

      @Override public void onConfigureFailed (@NonNull CameraCaptureSession cameraCaptureSession) {
        Log.e(TAG, "configure failed");
      }
    };
  }

  private ImageReader.OnImageAvailableListener createOnImageAvailableListener() {
    return reader -> {
      Image img = reader.acquireLatestImage();
      long sampleTimestamp = SystemClock.uptimeMillis();

      if (img == null) {
        return;
      }

      notifyRawDataIfEnabled(img, sampleTimestamp);
      QuadrantColor quadrantColor = CameraUtils.Companion.calculateAverageYUV(img, quadrantRows, quadrantCols);
      img.close();

      if (quadrantColor != null) {
        onFrameReceived(quadrantColor.quadrant, quadrantColor.yuvData, sampleTimestamp);
      }
    };
  }
}
