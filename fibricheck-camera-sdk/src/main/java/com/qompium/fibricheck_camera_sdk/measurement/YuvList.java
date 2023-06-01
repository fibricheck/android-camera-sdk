package com.qompium.fibricheck_camera_sdk.measurement;

import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class YuvList implements Serializable {

  @SerializedName("y")
  public List<Double> y;

  @SerializedName("u")
  public List<Double> u;

  @SerializedName("v")
  public List<Double> v;

  public YuvList () {

    this.y = new ArrayList<>();
    this.u = new ArrayList<>();
    this.v = new ArrayList<>();
  }

  public void addYUV (Yuv yuv) {

    try {
      this.y.add(yuv.y);
      this.u.add(yuv.u);
      this.v.add(yuv.v);
    } catch (NullPointerException e) {
      Log.e("YUVList", "NPE while adding YUV");
      e.printStackTrace();
    }
  }

  public void addYUV (double y, double u, double v) {

    this.y.add(y > 0 ? y : 0);
    this.u.add(u > 0 ? u : 0);
    this.v.add(v > 0 ? v : 0);
  }

  public Yuv getAverageYuv () {

    int ySum = 0, uSum = 0, vSum = 0;
    double size = y.size();
    for(int i = 0; i < size; i++) {
      ySum += y.get(i);
      uSum += u.get(i);
      vSum += v.get(i);
    }

    return new Yuv(ySum/size, uSum/size, vSum/size);
  }
}
