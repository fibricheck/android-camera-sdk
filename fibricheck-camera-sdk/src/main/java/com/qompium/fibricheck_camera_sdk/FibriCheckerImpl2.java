package com.qompium.fibricheck_camera_sdk;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import com.qompium.fibricheck_camera_sdk.measurement.Quadrant;
import com.qompium.fibricheck_camera_sdk.measurement.QuadrantColor;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FibriCheckerImpl2 extends FibriChecker {

  private static final String TAG = "FibriChecker2";

  private TextureView mTextureView;

  private CameraDevice mCameraDevice;

  private CameraCaptureSession mPreviewSession;

  private TextureView.SurfaceTextureListener mSurfaceTextureListener;

  private Size mPreviewSize;

  private Size mVideoSize;

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
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  /**
   * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
   */
  private CameraDevice.StateCallback mStateCallback;

  private ImageReader.OnImageAvailableListener mOnImageAvailableListener;

  private CaptureRequest.Builder mPreviewBuilder;

  private ImageReader mImageReader;

  private Range<Integer> fps[];

  private CameraCaptureSession.CaptureCallback mCaptureCallback;

  @TargetApi (21) public FibriCheckerImpl2 (ViewGroup viewGroup,Context context, FibriBuilder builder) {

    super(viewGroup, context, builder);

    mTextureView = new TextureView(context);
    viewGroup.addView(mTextureView);

    ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

      @Override public void onActivityCreated (Activity activity, Bundle bundle) {

      }

      @Override public void onActivityStarted (Activity activity) {

      }

      @Override public void onActivityResumed (Activity activity) {

      }

      @Override public void onActivityPaused (Activity activity) {

        closeCamera();
        stopBackgroundThread();
      }

      @Override public void onActivityStopped (Activity activity) {

      }

      @Override public void onActivitySaveInstanceState (Activity activity, Bundle bundle) {

      }

      @Override public void onActivityDestroyed (Activity activity) {

        clearResources();
      }
    });


    this.mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

      @Override public void onSurfaceTextureAvailable (SurfaceTexture surfaceTexture, int width, int height) {

        activateCamera();
      }

      @Override public void onSurfaceTextureSizeChanged (SurfaceTexture surfaceTexture, int width, int height) {

      }

      @Override public boolean onSurfaceTextureDestroyed (SurfaceTexture surfaceTexture) {

        return true;
      }

      @Override public void onSurfaceTextureUpdated (SurfaceTexture surfaceTexture) {

      }
    };

    //declare final for finishing on error

    this.mStateCallback = new CameraDevice.StateCallback() {

      @Override public void onOpened (CameraDevice cameraDevice) {

        mCameraDevice = cameraDevice;
        startPreview();
        mCameraOpenCloseLock.release();
      }

      @Override public void onDisconnected (CameraDevice cameraDevice) {

        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }

      @Override public void onError (CameraDevice cameraDevice, int error) {

        mCameraOpenCloseLock.release();
        cameraDevice.close();
        mCameraDevice = null;
      }
    };

    this.mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

      @Override public void onImageAvailable (ImageReader reader) {

        QuadrantColor quadrantColor = calculateAverageYUV(reader.acquireLatestImage());
        if (quadrantColor != null) {
          onFrameReceived(quadrantColor.quadrant, quadrantColor.yuvData, SystemClock.uptimeMillis());
        }
      }
    };

    mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

      private void process (CaptureResult result) {

      }

      @Override
      public void onCaptureProgressed (CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

        process(partialResult);
      }

      @Override
      public void onCaptureCompleted (CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

        updateCameraValues(result);
        process(result);
      }
    };
  }

  @Override public void start () {

    startBackgroundThread();
    state = State.DETECTING_FINGER;

    if (mTextureView.isAvailable()) {
      activateCamera();
    } else {
      Log.e(TAG, "No texture was available to attach on");
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  @TargetApi (21) private static Size chooseVideoSize (Size[] choices) {

    return choices[choices.length - 1];
  }

  private QuadrantColor calculateAverageYUV (Image yuvImage) {

    int ySum = 0, uSum = 0, vSum = 0;
    double yAvg = 0, uAvg = 0, vAvg = 0;
    double stdDevY = 0;// stdDevU, stdDevV;
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

      //yBuf.rewind();
      //uBuf.rewind();
      //vBuf.rewind();

      yAvg = ySum / frameSize;
      uAvg = uSum / frameSize;
      vAvg = vSum / frameSize;

      quadrant.avgY = yAvg;
      quadrant.frameSize = quadrantHeight * quadrantWidth;
      quadrant.processDataArray(quadrantDataArray);

      long sigmaY = 0;
      for (int i = 0; i < 256; i++) {
        sigmaY += histY[i] * Math.pow(i - yAvg, 2);
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
    } catch (InterruptedException | NullPointerException e) {
      Log.e(TAG, e.toString());
    } catch (RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  /**
   * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
   */
  @TargetApi (21) public void activateCamera () {

    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      String cameraId = manager.getCameraIdList()[0];

      // Choose the sizes for camera preview and video recording
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
      StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      if (hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
          && hardwareLevel > CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
        isAdvancedCamera2Implementation = true;
      }
      Log.i(TAG, "Hardwarelevel: " + hardwareLevel);

      mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
      mPreviewSize = chooseVideoSize(map.getOutputSizes(SurfaceTexture.class));
      cameraResolution = mVideoSize.toString();

      Log.i(TAG, "Chosen video/preview size: " + mVideoSize.toString() + "/" + mPreviewSize.toString());
      fps = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

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

  @Override @TargetApi (21) public void closeCamera () {

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
  @TargetApi (21) private void startPreview () {

    if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
      return;
    }
    try {
      closePreviewSession();
      SurfaceTexture texture = mTextureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
      mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      if (flashEnabled) {
        mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
      }

      Surface mImageSurface = mImageReader.getSurface();
      mPreviewBuilder.addTarget(mImageSurface);

      mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface), new CameraCaptureSession.StateCallback() {

        @Override public void onConfigured (CameraCaptureSession cameraCaptureSession) {

          mPreviewSession = cameraCaptureSession;
          updatePreview();
        }

        @Override public void onConfigureFailed (CameraCaptureSession cameraCaptureSession) {

          Log.e(TAG, "configure failed");
        }
      }, null);
    } catch (SecurityException | IllegalStateException | CameraAccessException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  /**
   * Update the camera preview. {@link #startPreview()} needs to be called in advance.
   */
  @TargetApi (21) private void updatePreview () {

    if (null == mCameraDevice) {
      return;
    }
    try {
      //setUpCaptureRequestBuilder(mPreviewBuilder);
      HandlerThread thread = new HandlerThread("CameraPreview");
      thread.start();
      mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, e.toString());
    } catch (IllegalStateException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override public void onFrameReceived (final Quadrant quadrantData, final double[] yuvData, final long timestamp) {

    ((Activity)context).runOnUiThread(new Runnable() {

      @Override public void run () {

        handleStates(quadrantData, yuvData, getMotionData(), timestamp);
      }
    });
  }

  @TargetApi (21) private void closePreviewSession () {

    if (mPreviewSession != null) {
      mPreviewSession.close();
      mPreviewSession = null;
    }
  }

  @TargetApi (21) private void updateCameraValues (CaptureResult result) {

    if (isAdvancedCamera2Implementation) {
      currentExposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
      currentIso = result.get(CaptureResult.SENSOR_SENSITIVITY);
    }
  }

  @TargetApi (21) @Override public void unlockExposure () {

    if (mPreviewBuilder == null) {
      Log.e(TAG, "previewBuilder was null");
      return;
    }

    try {
      mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
      mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
      mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  @TargetApi (21) @Override public void lockExposure () {

    if (mPreviewBuilder == null) {
      Log.e(TAG, "previewBuilder was null");
      return;
    }

    try {
      if (isAdvancedCamera2Implementation) {
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso);
        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTime);
        Log.i(TAG, "Exposure locked to " + currentExposureTime + " and iso " + currentIso);
      } else {
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        Log.i(TAG, "Exposure locked");
      }
      mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
    } catch (CameraAccessException | NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }
}
