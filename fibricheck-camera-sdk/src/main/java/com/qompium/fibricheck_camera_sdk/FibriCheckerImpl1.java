package com.qompium.fibricheck_camera_sdk;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.qompium.fibricheck_camera_sdk.measurement.Quadrant;

public class FibriCheckerImpl1 extends FibriChecker {

  private static final String TAG = "FibriChecker1";

  private Camera1SurfaceView camera1SurfaceView;

  public FibriCheckerImpl1 (ViewGroup viewGroup, Context context, FibriBuilder builder) {

    super(viewGroup, context, builder);
    hardwareLevel = -1;

    ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

      @Override public void onActivityCreated (Activity activity, Bundle bundle) {

      }

      @Override public void onActivityStarted (Activity activity) {

      }

      @Override public void onActivityResumed (Activity activity) {

      }

      @Override public void onActivityPaused (Activity activity) {

        closeCamera();
      }

      @Override public void onActivityStopped (Activity activity) {

      }

      @Override public void onActivitySaveInstanceState (Activity activity, Bundle bundle) {

      }

      @Override public void onActivityDestroyed (Activity activity) {

        clearResources();
      }
    });
  }

  @Override public void start () {

    try {
      activateCamera();
      state = State.DETECTING_FINGER;
    } catch (RuntimeException e) {
      Log.e(TAG, e.toString());
    }
  }

  public void activateCamera () {

    camera1SurfaceView = new Camera1SurfaceView(context, quadrantRows, quadrantCols, this);
    camera1SurfaceView.setFlash(flashEnabled);
    try {
      viewGroup.addView(camera1SurfaceView);
      viewGroup.setVisibility(View.INVISIBLE);
    } catch (NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override public void onFrameReceived (final Quadrant quadrant, final double[] yuvData, long timeStamp) {

    handleStates(quadrant, yuvData, getMotionData(), timeStamp);
  }

  @Override public void lockExposure () {

    camera1SurfaceView.setExposureLock(true);
  }

  @Override public void unlockExposure () {

    camera1SurfaceView.setExposureLock(false);
  }

  @Override public void closeCamera () {

    try {
      camera1SurfaceView.destroyCamera();
    } catch (NullPointerException e) {
      Log.e(TAG, e.toString());
    }
  }
}
