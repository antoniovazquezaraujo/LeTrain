package letrain.ground.impl;

import letrain.ground.NoiseGenerator;

public class Terrain2D {
  int tileSize = 16;
  double scl = 0.07F;

  double xRO, yRO, xTO, yTO, x, y;
  double speed = 5;
  int buffer = 10;

  int w, h;

  int[] tiles;

  NoiseGenerator noiseGenerator = new NoiseGenerator();
 
  void setup() {
    w = 100;
    h = 100;
    tiles = new int[(w) * (h)];
    drawTerrain();
  }

  void draw() {
    drawTerrain();
  }

  // void keyPressed() {
  //   if (key == ' ') {
  //     noiseSeed(millis());
  //     drawTerrain();
  //   }
  //   if (key == 'w')
  //     y -= speed;
  //   if (key == 's')
  //     y += speed;
  //   if (key == 'a')
  //     x -= speed;
  //   if (key == 'd')
  //     x += speed;
  // }

    public static void main(String[] args) {
        Terrain2D t = new Terrain2D();
        t.setup();
        t.draw();
    }
  void drawTerrain() {
    xRO = x % tileSize;
    yRO = y % tileSize;
    xTO = (int) x / tileSize;
    yTO = (int) y / tileSize;
    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        tiles[i + j * w] = getTile(i, j);
      }
    }
    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        System.out.println(":"
        + tiles[i + j * w]+ ","+ ((i - buffer / 2) * tileSize - xRO) 
        + ", " + ((j - buffer / 2) * tileSize - yRO )
        + ", "
        + tileSize
        + ","+ tileSize);
      }
    }
  }

  int getTile(int x, int y) {
    double v = noiseGenerator.noise((xTO + x) * scl, (yTO + y) * scl);
    if (v < 0.35F) {
      return 0;
    } else if (v < 0.5F) {
      return 1;
    } else if (v < 0.7F) {
      return 2;
    } else {
      return 3;
    }
  }
}