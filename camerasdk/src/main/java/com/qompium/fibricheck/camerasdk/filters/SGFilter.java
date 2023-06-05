package com.qompium.fibricheck.camerasdk.filters;

public class SGFilter {

  private double[] sgData;

  public SGFilter (int bufferSize) {

    this.sgData = new double[bufferSize];
  }

  public void pushData (double x) {

    for (int i = sgData.length - 1; i > 0; i--) {
      sgData[i] = sgData[i - 1];
    }
    sgData[0] = x;
  }

  public void fillSgData () {

    for (int i = 0; i < sgData.length; i++) {
      sgData[i] = 0.0;
    }
  }

  public double applyFilter () {

    // Values calculated from http://www.statistics4u.com/fundstat_eng/cc_savgol_coeff.html
    switch (sgData.length) {
      case 5:
        return (((-3) * sgData[0]) + (12 * sgData[1]) + (17 * sgData[2]) + (12 * sgData[3]) - (3 * sgData[4])) / (double) 35;
      case 7:
        return (((-2) * sgData[0]) + (3 * sgData[1]) + (6 * sgData[2]) + (7 * sgData[3]) + (6 * sgData[4]) + (3 * sgData[5]) - (2 * sgData[6]))
            / (double) 21;
      case 9:
        return (((-21) * sgData[0])
            + (14 * sgData[1])
            + (39 * sgData[2])
            + (54 * sgData[3])
            + (59 * sgData[4])
            + (54 * sgData[5])
            + (39 * sgData[6])
            + (14 * sgData[7]) - (21 * sgData[8])) / (double) 231;
      default:
        return 0.0;
    }
  }
}
