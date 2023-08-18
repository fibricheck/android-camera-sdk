package com.qompium.fibricheck.camerasdk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.qompium.fibricheck.camerasdk.listeners.CameraListener;
import com.qompium.fibricheck.camerasdk.measurement.Quadrant;
import com.qompium.fibricheck.camerasdk.measurement.QuadrantColor;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera1SurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

  private static final int BUFFER_COUNT = 4; //used for camera callback

  private int quadrantRows;

  private int quadrantCols;

  private SurfaceHolder mHolder;

  private Camera mCamera;

  private CameraListener cameraListener;

  private boolean isAELocksupported = false;

  private boolean isPreviewRunning;

  private static final String TAG = "Camera1Surface";

  private Parameters params;

  private int width, height;

  private double frameSize;

  public Camera1SurfaceView (Context context, int quadrantRows, int quadrantCols, CameraListener cameraListener) {

    super(context);

    this.quadrantRows = quadrantRows;
    this.quadrantCols = quadrantCols;
    this.cameraListener = cameraListener;

    Log.d(TAG, "Surface View created");
    mHolder = getHolder();
    mHolder.addCallback(this);

    setWhiteBalance();

    if (mCamera == null) {
      mCamera = Camera.open();
    }
  }

  @Override public void surfaceCreated (SurfaceHolder holder) {

    synchronized (this) {
      Log.d(TAG, "surface created");

      setWillNotDraw(true);
    }
  }

  @Override public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {

    Log.d(TAG, "Surface changed");

    synchronized (this) {
      if (isPreviewRunning) {
        mCamera.stopPreview();
      }
      if (mCamera == null) {
        return;
      }
      initCamera();
    }
  }

  private void initCamera () {

    Log.d(TAG, "Camera init");
    params = mCamera.getParameters();
    List<int[]> fpsRanges = params.getSupportedPreviewFpsRange();
    int[] fpsRange = fpsRanges.get(fpsRanges.size() - 1);

    params.setPreviewFormat(ImageFormat.NV21);
    isAELocksupported = params.isAutoExposureLockSupported();

    final List<Size> sizes = params.getSupportedPreviewSizes();
    Collections.sort(sizes, new Comparator<Size>() {

      public int compare (Size o1, Size o2) {

        return new Integer(o2.width).compareTo(o1.width);
      }
    });
    Collections.reverse(sizes);

    for (Size s : sizes) {
      int w = s.width, h = s.height;

      try {
        this.width = w;
        this.height = h;
        this.frameSize = w * h;
        params.setPreviewSize(w, h);
        Log.d(TAG, "trying: " + w + "x" + h);
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.setParameters(params);

        params = mCamera.getParameters();
        params.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        mCamera.setParameters(params);

        mCamera.setPreviewDisplay(mHolder);
        mCamera.startPreview();
        Log.d(TAG, "Try succeeded");
        isPreviewRunning = true;
        break;
      } catch (RuntimeException rx) {
        // ups, camera did not like this size
        Log.d(TAG, "...nope, try next");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    for (int i = 0; i < BUFFER_COUNT; i++) {
      byte[] buffer = new byte[this.width * this.height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8];
      mCamera.addCallbackBuffer(buffer);
    }
  }

  @Override public void surfaceDestroyed (SurfaceHolder holder) {

    Log.d(TAG, "Surface destroyed");

    cameraListener.onCameraDestroyed();
  }

  public void openCamera () {

    if (mCamera == null) {
      mCamera = Camera.open();
    }
  }

  public void destroyCamera () {

    setFlash(false);

    try {
      if (mCamera != null) {
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        isPreviewRunning = false;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override public void onPreviewFrame (final byte[] data, Camera camera) {

    // Use uptimeMillis as timestamp because it's monotonic.
    long frameTimestamp = SystemClock.uptimeMillis();
    mCamera.addCallbackBuffer(data);
    QuadrantColor quadrantColor = calculateAverageYUV(data);

    cameraListener.onFrameReceived(quadrantColor.quadrant, quadrantColor.yuvData, frameTimestamp);
  }

  private QuadrantColor calculateAverageYUV (byte[] yuv420sp) {

    int ySum = 0, uSum = 0, vSum = 0;
    double yAvg = 0, uAvg = 0, vAvg = 0;
    int[] histY = new int[256];
    double stdDevY = 0;

    Quadrant quadrant = new Quadrant();
    int[][][] quadrantDataArray = new int[Quadrant.QUADRANT_ROWS][Quadrant.QUADRANT_COLS][3];

    try {
      if (yuv420sp == null) {
        throw new NullPointerException("cannot be null");
      }

      int quadrantWidth = width / quadrantCols;
      int quadrantHeight = height / quadrantRows;

      int yValue, uValue = 0, vValue = 0;
      for (int j = 0, yp = 0; j < height; j++) {
        int uvp = (int) frameSize + (j >> 1) * width;
        for (int i = 0; i < width; i++, yp++) {
          yValue = (0xff & (yuv420sp[yp]));
          if ((i & 1) == 0) {
            vValue = (0xff & yuv420sp[uvp++]);
            uValue = (0xff & yuv420sp[uvp++]);
          }

          yValue = yValue > 0 ? yValue : 0;
          uValue = uValue > 0 ? uValue : 0;
          vValue = vValue > 0 ? vValue : 0;

          quadrantDataArray[j / quadrantHeight][i / quadrantWidth][0] += yValue;
          quadrantDataArray[j / quadrantHeight][i / quadrantWidth][1] += uValue;
          quadrantDataArray[j / quadrantHeight][i / quadrantWidth][2] += vValue;

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
        sigmaY += histY[i] * Math.pow(i - yAvg, 2);
      }
      stdDevY = Math.sqrt(sigmaY / frameSize);

      //Log.e("std", String.format("%f, %f, %f", yAvg, vAvg, stdDevY));
    } catch (NullPointerException e) {
      Log.e(TAG, e.toString());
    }

    return new QuadrantColor(quadrant, new double[] { yAvg, uAvg, vAvg, stdDevY });
  }

  public boolean setFlash (boolean bool) {

    if (mCamera == null) {
      Log.e(TAG, "no camera fo flash..");
      return false;
    }

    if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
      try {
        Parameters params = mCamera.getParameters();
        if (!bool) {
          params.setFlashMode(Parameters.FLASH_MODE_OFF);
          mCamera.setParameters(params);
          return true;
        } else {
          params.setFlashMode(Parameters.FLASH_MODE_TORCH);
          mCamera.setParameters(params);
          Log.d(TAG, "TORCHING !!..");
          return true;
        }
      } catch (RuntimeException e) {
        Log.e(TAG, "Failed to turn on flash");
        return false;
      }
    }
    return false;
  }

  public void setWhiteBalance () {

    if (mCamera == null) {
      return;
    }
    Parameters params = mCamera.getParameters();

    params.setFocusMode(params.FOCUS_MODE_INFINITY);
  }

  public void setUiCallBackListener (CameraListener cameraListener) {

    this.cameraListener = cameraListener;
  }

  public void setExposureLock (boolean state) {

    if (isAELocksupported) {
      Parameters params = mCamera.getParameters();
      params.setAutoExposureLock(state);
      mCamera.setParameters(params);
      Log.i("Camera1SurfaceView", "Exposure lock is now " + state);
    } else {
      Log.e("Camera1SurfaceView", "AE Lock not supported");
    }
  }
}