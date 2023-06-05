package com.qompium.fibricheck.camerasdk.filters;

/**
 * Digital filter
 *
 * @author Luc
 */
public class FirFilter {

  private double[] a;

  private double[] b;

  private double[] xRegister; //Contains previous input values that are used for computing the filtered value.

  private double[] yRegister; //Contains previous output values.

  private FirFilter nextFilter; //Used to chain filters together.

  /**
   * Makes a new filter object with the desired coefficients.
   *
   * @param a Output-coefficients.
   * @param b Input-coefficients.
   */
  public FirFilter (double[] a, double[] b) {

    this.a = a;
    this.b = b;

    xRegister = new double[b.length];
    for (int i = 0; i < b.length; i++) {
      xRegister[i] = 0;
    }

    yRegister = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      yRegister[i] = 0;
    }

    nextFilter = null;
  }

  /**
   * Makes a new filter object with the desired coefficients, the output is immediately connected to the input of nextFilter.
   *
   * @param a          Output-coefficients.
   * @param b          Input-coefficients.
   * @param nextFilter FirFilter object, output of this filter is connected to the input of nextFilter.
   */
  public FirFilter (double[] a, double[] b, FirFilter nextFilter) {

    this.a = a;
    this.b = b;

    xRegister = new double[b.length];
    for (int i = 0; i < b.length; i++) {
      xRegister[i] = 0;
    }

    yRegister = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      yRegister[i] = 0;
    }

    connectOutput(nextFilter);
  }

  /**
   * Calculates the filtered value based upon the new input value.
   *
   * @param inputValue The current input value.
   *
   * @return The filtered value.
   */
  public double calculateOutput (double inputValue) {

    double result = 0f; //y(n) in Matlab.

    pushX(inputValue);

    //Calculate result:
    for (int i = 1; i < a.length; i++) {
      result = result - a[i] * yRegister[i - 1];
    }
    for (int i = 0; i < b.length; i++) {
      result = result + b[i] * xRegister[i];
    }
    result = result / a[0];

    pushY(result);

    if (nextFilter != null) {
      return nextFilter.calculateOutput(result);
    } else {
      return result;
    }
  }

  /**
   * Saves the value into the input register.
   *
   * @param x The value to be saved.
   */
  private void pushX (double x) {

    for (int i = xRegister.length - 1; i > 0; i--) {
      xRegister[i] = xRegister[i - 1];
    }
    xRegister[0] = x;
  }

  /**
   * Saves the value into the output register.
   *
   * @param y The value to be saved.
   */
  private void pushY (double y) {

    for (int i = yRegister.length - 1; i > 0; i--) {
      yRegister[i] = yRegister[i - 1];
    }
    yRegister[0] = y;
  }

  // Getters and setters:

  public void connectOutput (FirFilter nextFilter) {

    this.nextFilter = nextFilter;
  }

  public double[] getA () {

    return a;
  }

  public void setA (double[] a) {

    this.a = a;
  }

  public double[] getB () {

    return b;
  }

  public void setB (double[] b) {

    this.b = b;
  }
}
