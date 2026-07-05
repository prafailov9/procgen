package com.ntros.image;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;

public class ImageSplitter {

  public void split(String imagePath) throws IOException {
    BufferedImage bufferedImage =
        ImageIO.read(Objects.requireNonNull(ImageSplitter.class.getResource(imagePath)));
    int width = bufferedImage.getWidth();
    int height = bufferedImage.getHeight();

    int splits = gcd(width, height);

    if (width > height) {
      // vertical split if W > H

    } else if (width < height) {
      // horizontal split if H > W

    }

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        //        bufferedImage.getRGB()
      }
    }
    Raster raster = bufferedImage.getData();
  }

  private int gcd(int w, int h) {
    w = Math.abs(w);
    h = Math.abs(h);

    if (w == 0) {
      return h;
    }
    if (h == 0) {
      return w;
    }

    int high = Math.max(w, h);
    int low = Math.min(w, h);

    int rem = high % low;
    while (rem != 0) {
      high = low;
      low = rem;
      rem = high % low;
    }
    return low;
  }
}
