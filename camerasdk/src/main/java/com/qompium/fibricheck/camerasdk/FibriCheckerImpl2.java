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
import com.qompium.fibricheck.camerasdk.listeners.CameraStateCallback;
import com.qompium.fibricheck.camerasdk.listeners.EmptyActivityLifecycleCallbacks;
import com.qompium.fibricheck.camerasdk.listeners.EmptySurfaceTextureListener;
import com.qompium.fibricheck.camerasdk.listeners.RawDataListener;
import com.qompium.fibricheck.camerasdk.measurement.Quadrant;
import com.qompium.fibricheck.camerasdk.measurement.QuadrantColor;
import com.qompium.fibricheck.camerasdk.models.CameraSettingsInfo;
import com.qompium.fibricheck.camerasdk.models.ProcessMeasurementWorker;
import com.qompium.fibricheck.camerasdk.utils.CameraUtils;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

enum ImplState {
  Initial,
  CreatingTexture,
  OpeningCamera,
  StartingSession,
  Ready
}

@RequiresApi(21)
public class FibriCheckerImpl2 extends FibriChecker {
  private static final String TAG = "FibriChecker2";

  private TextureView mTextureView;
  private CameraDevice mCameraDevice;
  private String mCameraId;
  private CameraCaptureSession mPreviewSession;

  private boolean isAdvancedCamera2Implementation = false;
  private ImplState implState = ImplState.Initial;

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread mBackgroundThread;
  private Handler mBackgroundHandler;

  private HandlerThread mRawDataThread;
  private Handler mRawDataHandler;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
  private final Semaphore cameraSemaphore = new Semaphore(1);

  private CaptureRequest.Builder mCaptureRequest;
  private ImageReader mImageReader;
  private Map<String, String> mLastCameraData;

  public FibriCheckerImpl2 (ViewGroup viewGroup,Context context, FibriBuilder builder) {
    super(viewGroup, context, builder);
    startBackgroundThread();
  }

  @Override public void start() {
      if (implState != ImplState.Initial) {
        Log.w(TAG, "Please stop before starting");
      }

      Log.d(TAG, "Start");
	  try {
          Log.d(TAG, "Sem Acquire [Start]");
		  boolean acquired = cameraSemaphore.tryAcquire(10000, TimeUnit.MILLISECONDS);
          if (!acquired) {
            Log.w(TAG, "Sem Fail [Start]");
            return;
          }
          Log.d(TAG, "Sem Acquired [Start]");

          implState = ImplState.CreatingTexture;
          mTextureView = new TextureView(context);
          viewGroup.removeAllViews();
          viewGroup.addView(mTextureView, 0);


          Log.d(TAG, "Setting Surface Listener");
          if (mTextureView.isAvailable()) {
            onTextureReady(mTextureView.getSurfaceTexture());
            return;
          }

          WeakReference<FibriCheckerImpl2> weakThis = new WeakReference<>(this);
          mTextureView.setSurfaceTextureListener(new EmptySurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NotNull SurfaceTexture surface, int width, int height) {
              FibriCheckerImpl2 impl2 = weakThis.get();
              if (impl2 != null) {
                impl2.onTextureReady(surface);
              }
            }
          });
	  } catch (InterruptedException e) {
        Log.w(TAG, e.toString());
	  }
  }

  @Override
  public void stop() {
    if (mCaptureRequest != null) {
      // TODO remove targets?
      mCaptureRequest = null;
    }
    if (mTextureView != null) {
      mTextureView = null;
    }
    if (mImageReader != null) {
      mImageReader.close();
      mImageReader = null;
    }
    if (mCameraDevice != null) {
      mCameraDevice.close();
      mCameraDevice = null;
    }

    implState = ImplState.Initial;
  }

  @Override
  public void destroy() {
    stopBackgroundThread();
    super.destroy();
  }

  private void onCameraStartError(Exception error) {
    Log.e(TAG, error.toString());
    destroy();

    Log.d(TAG, "Sem Release [onCamearStartError]");
    cameraSemaphore.release();
  }

  private void onTextureReady(SurfaceTexture surface) {
    if (implState != ImplState.CreatingTexture) {
      Log.d(TAG, "onTextureReady but wrong state");
      return;
    }
    implState = ImplState.OpeningCamera;
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

    Log.d(TAG, "OnTextureReady");
    try {
      mCameraId = manager.getCameraIdList()[0];
      WeakReference<FibriCheckerImpl2> weakThis = new WeakReference<>(this);

      manager.openCamera(mCameraId, new CameraStateCallback(
          camera -> {
            Log.d(TAG, "onCameraOpened");
            FibriCheckerImpl2 impl2 = weakThis.get();
            if (impl2 != null) {
              impl2.onCameraOpened(camera);
            }
            return null;
          },
          camera -> {
            Log.d(TAG, "onCameraClosed");
            FibriCheckerImpl2 impl2 = weakThis.get();
            if (impl2 != null) {
              impl2.onCameraClosed(camera);
            }
            return null;
          },
          (camera, error) -> {
            Log.d(TAG, "onCameraError");
            FibriCheckerImpl2 impl2 = weakThis.get();
            if (impl2 != null) {
              impl2.onCameraStartError(new Exception("Camera error: " + error + ""));
            }
            return null;
          },
          camera -> {
            Log.d(TAG, "onCameraDisconnect");
            FibriCheckerImpl2 impl2 = weakThis.get();
            if (impl2 != null) {
              impl2.onCameraClosed(camera);
            }
            return null;
          }
      ), mBackgroundHandler);

    } catch (CameraAccessException e) {
      //activity.finish();
      onCameraStartError(e);
    } catch ( SecurityException | NullPointerException e) {
      onCameraStartError(e);
    } catch (RuntimeException e) {
      onCameraStartError(e);
    }
  }

  private QuadrantColor calculateAverageYUV (Image yuvImage) {
    int ySum = 0, uSum = 0, vSum = 0;
    double yAvg, uAvg, vAvg;
    double stdDevY = 0;
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

    return new QuadrantColor(quadrant, new double[] { yAvg, uAvg, vAvg, stdDevY });
  }

  private void startBackgroundThread () {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

    mRawDataThread = new HandlerThread("RawData");
    mRawDataThread.start();
    mRawDataHandler = new Handler(mRawDataThread.getLooper());
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread () {
    try {
      if (mBackgroundThread != null) {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundThread = null;
        mBackgroundHandler = null;
      }

      if (mRawDataThread != null) {
        mRawDataThread.quitSafely();
        mRawDataThread.join();
        mRawDataThread = null;
        mRawDataHandler = null;
      }
    } catch (InterruptedException | RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  private void onCameraOpened(CameraDevice camera) {
    if (implState != ImplState.OpeningCamera) {
      Log.d(TAG, "onCameraOpened but wrong state");
      return;
    }

    Log.d(TAG, "onCameraOpened called");
    implState = ImplState.StartingSession;
    mCameraDevice = camera;

    try {
      CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

      // Choose the sizes for camera preview and video recording
      CameraCharacteristics chars = manager.getCameraCharacteristics(mCameraId);
      StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      if (hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
          && hardwareLevel > CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
        isAdvancedCamera2Implementation = true;
      }

      Size videoSize = CameraUtils.Companion.pickSmallest(map.getOutputSizes(MediaRecorder.class));
      Size previewSize = CameraUtils.Companion.pickSmallest(map.getOutputSizes(SurfaceTexture.class));
      cameraResolution = videoSize.toString();

      createImageReader(videoSize);

      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;

      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
      mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      Surface dataSurface = mImageReader.getSurface();
      Surface previewSurface = new Surface(texture);
      mCaptureRequest.addTarget(dataSurface);
      mCaptureRequest.addTarget(previewSurface);

      WeakReference<FibriCheckerImpl2> weakThis = new WeakReference<>(this);
      mCameraDevice.createCaptureSession(Arrays.asList(dataSurface, previewSurface), new CameraCaptureSession.StateCallback() {
        @Override public void onConfigured (@NonNull CameraCaptureSession cameraCaptureSession) {
          FibriCheckerImpl2 impl2 = weakThis.get();
          if (impl2 != null) {
            impl2.implState = ImplState.Ready;
            impl2.mPreviewSession = cameraCaptureSession;
            impl2.applyCameraSettings();
          }
        }

        @Override public void onConfigureFailed (@NonNull CameraCaptureSession cameraCaptureSession) {
          FibriCheckerImpl2 impl2 = weakThis.get();
          if (impl2 != null) {
            impl2.onCameraStartError(new Exception("Camera session failed to configure"));
          }
        }
      }, null);
    } catch (SecurityException | IllegalStateException | CameraAccessException | NullPointerException e) {
      onCameraStartError(e);
    }
  }

  private void createImageReader(Size videoSize) {
    mImageReader = ImageReader.newInstance(videoSize.getWidth(), videoSize.getHeight(), ImageFormat.YUV_420_888, 4);
    mImageReader.setOnImageAvailableListener(reader -> {
      try {
        Image img = reader.acquireLatestImage();
        long sampleTimestamp = SystemClock.uptimeMillis();

        notifyRawDataIfEnabled(img, sampleTimestamp);
        QuadrantColor quadrantColor = calculateAverageYUV(img);
        if (quadrantColor != null) {
          onFrameReceived(quadrantColor.quadrant, quadrantColor.yuvData, sampleTimestamp);
        }
      } catch (IllegalStateException e) {
        Log.w(TAG, e.toString());
      }
    }, mBackgroundHandler);
  }

  private void onCameraClosed(CameraDevice camera) {
    mCameraDevice = null;

    stop();
    Log.d(TAG, "Sem Release [onCameraClose]");
    cameraSemaphore.release();
  }

  @Override public void onFrameReceived (final Quadrant quadrantData, final double[] yuvData, final long timestamp) {
    ((Activity)context).runOnUiThread(() -> handleStates(quadrantData, yuvData, getMotionData(), timestamp));
  }

  private void updateCameraValues (CaptureResult result) {
    if (!isAdvancedCamera2Implementation) {
      return;
    }

    cameraSettings.onSettingsChanged(result);
  }

  @Override
  protected void applyCameraSettings() {
    if (implState != ImplState.Ready) {
      Log.w(TAG, "Apply Camera Settings called before ready");
      return;
    }

    applyTorch();
    applyExposure();
    applyWhiteBalance();
    applyFocus();
    applyRequest();
  }

  private void applyTorch() {
    boolean showFlash = this.flashEnabled && state != State.PREVIEW;
    mCaptureRequest.set(CaptureRequest.FLASH_MODE, showFlash ? CameraMetadata.FLASH_MODE_TORCH: CameraMetadata.FLASH_MODE_OFF);
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
      WeakReference<FibriCheckerImpl2> weakThis = new WeakReference<>(this);

      mPreviewSession.setRepeatingRequest(mCaptureRequest.build(), new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted (@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
          FibriCheckerImpl2 impl2 = weakThis.get();
          if (impl2 != null) {
            impl2.updateCameraValues(result);
            if (impl2.cameraSettings.getRawDataEnabled()) {
              impl2.mLastCameraData = TotalCaptureResultKt.toMap(result);
            }
          }
        }
        }, mRawDataHandler);
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

  private void notifyRawDataIfEnabled(Image image, long sampleTimestamp) {
    if (!cameraSettings.getRawDataEnabled() || rawDataListener == null || image == null || mLastCameraData == null || state == State.PREVIEW) {
      return;
    }

    byte[] rawImageData = ImageKt.toByteArray(image);
    mLastCameraData.put("image.width", String.valueOf(image.getWidth()));
    mLastCameraData.put("image.height", String.valueOf(image.getHeight()));
    mLastCameraData.put("image.cropRect", TotalCaptureResultKt.toCustomString(image.getCropRect()));
    mLastCameraData.put("measurement.sampleTimestamp", String.valueOf(sampleTimestamp));

    rawDataListener.onNewData(rawImageData, mLastCameraData);
  }
}
