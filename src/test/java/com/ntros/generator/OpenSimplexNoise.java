package com.ntros.generator;

public class OpenSimplexNoise {
  private static final double STRETCH_CONSTANT_2D = -0.211324865405187;
  private static final double SQUISH_CONSTANT_2D = 0.366025403784439;
  private static final double NORM_CONSTANT_2D = (double) 47.0F;
  private static final long DEFAULT_SEED = 0L;
  private short[] perm;
  private static byte[] gradients2D =
      new byte[] {5, 2, 2, 5, -5, 2, -2, 5, 5, -2, 2, -5, -5, -2, -2, -5};

  public OpenSimplexNoise() {
    this(0L);
  }

  public OpenSimplexNoise(short[] perm) {
    this.perm = perm;
  }

  public OpenSimplexNoise(long seed) {
    this.perm = new short[256];
    short[] source = new short[256];

    for (short i = 0; i < 256; source[i] = i++) {}

    seed = seed * 6364136223846793005L + 1442695040888963407L;
    seed = seed * 6364136223846793005L + 1442695040888963407L;
    seed = seed * 6364136223846793005L + 1442695040888963407L;

    for (int i = 255; i >= 0; --i) {
      seed = seed * 6364136223846793005L + 1442695040888963407L;
      int r = (int) ((seed + 31L) % (long) (i + 1));
      if (r < 0) {
        r += i + 1;
      }

      this.perm[i] = source[r];
      source[r] = source[i];
    }
  }

  public double eval(double x, double y) {
    double stretchOffset = (x + y) * -0.211324865405187;
    double xs = x + stretchOffset;
    double ys = y + stretchOffset;
    int xsb = fastFloor(xs);
    int ysb = fastFloor(ys);
    double squishOffset = (double) (xsb + ysb) * 0.366025403784439;
    double xb = (double) xsb + squishOffset;
    double yb = (double) ysb + squishOffset;
    double xins = xs - (double) xsb;
    double yins = ys - (double) ysb;
    double inSum = xins + yins;
    double dx0 = x - xb;
    double dy0 = y - yb;
    double value = (double) 0.0F;
    double dx1 = dx0 - (double) 1.0F - 0.366025403784439;
    double dy1 = dy0 - (double) 0.0F - 0.366025403784439;
    double attn1 = (double) 2.0F - dx1 * dx1 - dy1 * dy1;
    if (attn1 > (double) 0.0F) {
      attn1 *= attn1;
      value += attn1 * attn1 * this.extrapolate(xsb + 1, ysb + 0, dx1, dy1);
    }

    double dx2 = dx0 - (double) 0.0F - 0.366025403784439;
    double dy2 = dy0 - (double) 1.0F - 0.366025403784439;
    double attn2 = (double) 2.0F - dx2 * dx2 - dy2 * dy2;
    if (attn2 > (double) 0.0F) {
      attn2 *= attn2;
      value += attn2 * attn2 * this.extrapolate(xsb + 0, ysb + 1, dx2, dy2);
    }

    double dx_ext;
    double dy_ext;
    int xsv_ext;
    int ysv_ext;
    if (inSum <= (double) 1.0F) {
      double zins = (double) 1.0F - inSum;
      if (!(zins > xins) && !(zins > yins)) {
        xsv_ext = xsb + 1;
        ysv_ext = ysb + 1;
        dx_ext = dx0 - (double) 1.0F - 0.732050807568878;
        dy_ext = dy0 - (double) 1.0F - 0.732050807568878;
      } else if (xins > yins) {
        xsv_ext = xsb + 1;
        ysv_ext = ysb - 1;
        dx_ext = dx0 - (double) 1.0F;
        dy_ext = dy0 + (double) 1.0F;
      } else {
        xsv_ext = xsb - 1;
        ysv_ext = ysb + 1;
        dx_ext = dx0 + (double) 1.0F;
        dy_ext = dy0 - (double) 1.0F;
      }
    } else {
      double zins = (double) 2.0F - inSum;
      if (!(zins < xins) && !(zins < yins)) {
        dx_ext = dx0;
        dy_ext = dy0;
        xsv_ext = xsb;
        ysv_ext = ysb;
      } else if (xins > yins) {
        xsv_ext = xsb + 2;
        ysv_ext = ysb + 0;
        dx_ext = dx0 - (double) 2.0F - 0.732050807568878;
        dy_ext = dy0 + (double) 0.0F - 0.732050807568878;
      } else {
        xsv_ext = xsb + 0;
        ysv_ext = ysb + 2;
        dx_ext = dx0 + (double) 0.0F - 0.732050807568878;
        dy_ext = dy0 - (double) 2.0F - 0.732050807568878;
      }

      ++xsb;
      ++ysb;
      dx0 = dx0 - (double) 1.0F - 0.732050807568878;
      dy0 = dy0 - (double) 1.0F - 0.732050807568878;
    }

    double attn0 = (double) 2.0F - dx0 * dx0 - dy0 * dy0;
    if (attn0 > (double) 0.0F) {
      attn0 *= attn0;
      value += attn0 * attn0 * this.extrapolate(xsb, ysb, dx0, dy0);
    }

    double attn_ext = (double) 2.0F - dx_ext * dx_ext - dy_ext * dy_ext;
    if (attn_ext > (double) 0.0F) {
      attn_ext *= attn_ext;
      value += attn_ext * attn_ext * this.extrapolate(xsv_ext, ysv_ext, dx_ext, dy_ext);
    }

    return value / (double) 47.0F;
  }

  private double extrapolate(int xsb, int ysb, double dx, double dy) {
    int index = this.perm[this.perm[xsb & 255] + ysb & 255] & 14;
    return (double) gradients2D[index] * dx + (double) gradients2D[index + 1] * dy;
  }

  private static int fastFloor(double x) {
    int xi = (int) x;
    return x < (double) xi ? xi - 1 : xi;
  }
}
