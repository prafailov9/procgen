package com.ntros.generator;

import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;

import java.util.Arrays;

/**
 * Carves rivers into a generated heightfield using flow accumulation.
 *
 * <p>Rivers cannot come out of the noise settings, however they are tuned. The classifier decides
 * each tile from ONE scalar — its own elevation — and a river is not a property of a point. It is
 * connected along its length, runs monotonically downhill, merges with tributaries and never
 * splits, and terminates at a sea or lake. Those are properties of the GRADIENT field, of how each
 * tile relates to its neighbours. Thresholding a thin band of elevation gives contour lines:
 * closed loops at constant height, running across slopes instead of down them. The opposite of a
 * river.
 *
 * <p>So rivers are a second algorithm that runs after the heightfield exists, in three passes:
 *
 * <ol>
 *   <li><b>Fill depressions</b> (Priority-Flood) so every land tile has a downhill path to the sea
 *   <li><b>Flow direction</b> (D8): each tile points at its steepest downhill neighbour
 *   <li><b>Flow accumulation</b>: how much upstream land drains through each tile. Anything above
 *       a threshold is a river.
 * </ol>
 *
 * <p>Cost is O(n log n), dominated by the two sorts, and it runs once at generation.
 */
public final class HydrologyGenerator {

  /**
   * Added each time a depression is raised. Without it a filled basin is perfectly flat, D8 finds
   * no downhill neighbour anywhere inside it, and flow stalls — the classic "flat resolution"
   * problem. A tiny upward tilt guarantees a strictly descending path out of every filled pit, at
   * a distortion far below what the classifier's thresholds can notice.
   */
  private static final float FILL_EPSILON = 1e-6f;

  private static final int[] NEIGHBOR_DX = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] NEIGHBOR_DY = {1, 1, 0, -1, -1, -1, 0, 1};
  // diagonal steps cover more ground, so slope must be measured per unit distance or rivers
  // develop a diagonal bias
  private static final float[] NEIGHBOR_DISTANCE = {
    1f, 1.41421356f, 1f, 1.41421356f, 1f, 1.41421356f, 1f, 1.41421356f
  };

  private final int width;
  private final int height;
  private final int size;
  private final TerrainCodec codec = new TerrainCodec();

  public HydrologyGenerator(int width, int height) {
    this.width = width;
    this.height = height;
    this.size = width * height;
  }

  /**
   * Marks river tiles in place.
   *
   * @param tiles encoded terrain, modified where rivers are carved
   * @param surface the heightfield the tiles were classified from — must be the SAME field, or
   *     rivers will flow through what looks like a hillside
   * @param moisture used as rainfall weight, so wetter regions grow bigger rivers
   * @return flow accumulation per tile, useful later for mills, fords and settlement siting
   */
  public float[] carveRivers(byte[] tiles, float[] surface, float[] moisture) {
    boolean[] isSea = markSea(tiles);
    float[] filled = fillDepressions(surface, isSea);
    int[] downstream = computeFlowDirections(filled, isSea);
    float[] accumulation = accumulateFlow(filled, downstream, moisture, isSea);
    paintRivers(tiles, accumulation, isSea);
    return accumulation;
  }

  private boolean[] markSea(byte[] tiles) {
    boolean[] isSea = new boolean[size];
    for (int i = 0; i < size; i++) {
      Tile tile = codec.decode(tiles[i]);
      isSea[i] = tile == Tile.DEEP_WATER || tile == Tile.SHALLOW_WATER;
    }
    return isSea;
  }

  /**
   * Priority-Flood (Barnes et al.): flood inward from every outlet, always expanding from the
   * lowest frontier tile. A tile reached from a higher frontier must be at least that high — that
   * is exactly what "this pit fills up to the level where it spills" means.
   *
   * <p>Outlets are sea tiles and the map border. Without the border, a coastless map would have
   * nowhere to drain and the whole thing would fill to one level.
   */
  private float[] fillDepressions(float[] surface, boolean[] isSea) {
    float[] filled = surface.clone();
    boolean[] visited = new boolean[size];
    LongHeap frontier = new LongHeap(Math.max(1024, size / 8));

    for (int i = 0; i < size; i++) {
      int x = i % width;
      int y = i / width;
      boolean onBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1;
      if (isSea[i] || onBorder) {
        visited[i] = true;
        frontier.push(key(filled[i], i));
      }
    }

    while (!frontier.isEmpty()) {
      long entry = frontier.pop();
      int cell = cellOf(entry);
      float spillLevel = filled[cell];

      int x = cell % width;
      int y = cell / width;
      for (int n = 0; n < NEIGHBOR_DX.length; n++) {
        int nx = x + NEIGHBOR_DX[n];
        int ny = y + NEIGHBOR_DY[n];
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
          continue;
        }
        int neighbor = ny * width + nx;
        if (visited[neighbor]) {
          continue;
        }
        visited[neighbor] = true;
        if (filled[neighbor] <= spillLevel) {
          filled[neighbor] = spillLevel + FILL_EPSILON;
        }
        frontier.push(key(filled[neighbor], neighbor));
      }
    }
    return filled;
  }

  /** For each land tile, the neighbour with the steepest drop per unit distance. */
  private int[] computeFlowDirections(float[] filled, boolean[] isSea) {
    int[] downstream = new int[size];
    Arrays.fill(downstream, -1);

    for (int cell = 0; cell < size; cell++) {
      if (isSea[cell]) {
        continue; // water that reaches the sea has arrived
      }
      int x = cell % width;
      int y = cell / width;
      float steepest = 0f;
      int best = -1;

      for (int n = 0; n < NEIGHBOR_DX.length; n++) {
        int nx = x + NEIGHBOR_DX[n];
        int ny = y + NEIGHBOR_DY[n];
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
          continue;
        }
        int neighbor = ny * width + nx;
        float slope = (filled[cell] - filled[neighbor]) / NEIGHBOR_DISTANCE[n];
        if (slope > steepest) {
          steepest = slope;
          best = neighbor;
        }
      }
      downstream[cell] = best;
    }
    return downstream;
  }

  /**
   * Every tile starts holding its own rainfall, then hands its total to the tile below it.
   *
   * <p>Processing strictly from high ground down means a tile's own total is final before it is
   * passed on — one linear sweep does what would otherwise need repeated relaxation. This is the
   * same trick as evaluating a DAG in topological order; elevation IS the topological order,
   * because water only ever flows downhill.
   */
  private float[] accumulateFlow(
      float[] filled, int[] downstream, float[] moisture, boolean[] isSea) {
    float[] accumulation = new float[size];
    for (int cell = 0; cell < size; cell++) {
      // wetter ground contributes more runoff, so rivers are bigger in wet regions
      accumulation[cell] = isSea[cell] ? 0f : 0.5f + moisture[cell];
    }

    long[] order = new long[size];
    for (int cell = 0; cell < size; cell++) {
      order[cell] = key(filled[cell], cell);
    }
    Arrays.sort(order); // ascending by elevation

    for (int i = size - 1; i >= 0; i--) { // walk downhill: highest first
      int cell = cellOf(order[i]);
      int next = downstream[cell];
      if (next >= 0) {
        accumulation[next] += accumulation[cell];
      }
    }
    return accumulation;
  }

  /**
   * A river appears where enough land drains through one tile. The threshold scales with the map
   * so the same setting means the same river density at any world size — the lesson learned from
   * biomass growth being an absolute rate.
   */
  private void paintRivers(byte[] tiles, float[] accumulation, boolean[] isSea) {
    int landCount = 0;
    for (int cell = 0; cell < size; cell++) {
      if (!isSea[cell]) {
        landCount++;
      }
    }
    if (landCount == 0) {
      return;
    }

    float minDrainage = Math.max(64f, landCount * 0.0004f);
    // a river carrying this much water is wide enough to spill onto its neighbours
    float wideDrainage = minDrainage * 12f;
    byte freshWater = codec.encodeTile(Tile.FRESH_WATER);

    for (int cell = 0; cell < size; cell++) {
      if (isSea[cell] || accumulation[cell] < minDrainage) {
        continue;
      }
      tiles[cell] = freshWater;

      if (accumulation[cell] < wideDrainage) {
        continue;
      }
      int x = cell % width;
      int y = cell / width;
      for (int n = 0; n < NEIGHBOR_DX.length; n += 2) { // 4-neighbours only: keeps banks tidy
        int nx = x + NEIGHBOR_DX[n];
        int ny = y + NEIGHBOR_DY[n];
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
          continue;
        }
        int neighbor = ny * width + nx;
        if (!isSea[neighbor]) {
          tiles[neighbor] = freshWater;
        }
      }
    }
  }

  /**
   * Packs (elevation, cell) into one long so the heap and the sort need no objects and no boxing.
   *
   * <p>Float bit patterns of non-negative floats compare in the same order as the floats
   * themselves, so the raw bits can sit in the high word and ordinary long ordering sorts by
   * height. Elevation here is always in [0,1], so that holds.
   */
  private static long key(float elevation, int cell) {
    return ((long) Float.floatToIntBits(elevation) << 32) | (cell & 0xFFFFFFFFL);
  }

  private static int cellOf(long key) {
    return (int) key;
  }

  /** Minimal primitive min-heap. Java's PriorityQueue would box two million Longs. */
  private static final class LongHeap {
    private long[] items;
    private int count;

    LongHeap(int capacity) {
      items = new long[Math.max(16, capacity)];
    }

    boolean isEmpty() {
      return count == 0;
    }

    void push(long value) {
      if (count == items.length) {
        items = Arrays.copyOf(items, items.length * 2);
      }
      int i = count++;
      items[i] = value;
      while (i > 0) { // sift up
        int parent = (i - 1) >>> 1;
        if (items[parent] <= items[i]) {
          break;
        }
        swap(parent, i);
        i = parent;
      }
    }

    long pop() {
      long top = items[0];
      items[0] = items[--count];
      int i = 0;
      while (true) { // sift down
        int left = 2 * i + 1;
        int right = left + 1;
        int smallest = i;
        if (left < count && items[left] < items[smallest]) {
          smallest = left;
        }
        if (right < count && items[right] < items[smallest]) {
          smallest = right;
        }
        if (smallest == i) {
          break;
        }
        swap(smallest, i);
        i = smallest;
      }
      return top;
    }

    private void swap(int a, int b) {
      long tmp = items[a];
      items[a] = items[b];
      items[b] = tmp;
    }
  }
}
