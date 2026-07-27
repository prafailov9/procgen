package com.ntros.generator;

import com.ntros.core.world.Tile;
import com.ntros.core.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.ntros.core.world.Tile.EMPTY;

public class SequentialWorldGenerator {

    private static final int TILE_COUNT = Tile.values().length - 1; // excluding EMPTY
    private static final int NEIGHBORS = 8;

    // all 8 neighbor paired positions
    private static final int[] neighX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] neighY = {1, 1, 0, -1, -1, -1, 0, 1};

    private final Random rng = new Random();
    // for each tile type + neighbor dir -> stores the set of tile types that are allowed there
    // rules[tile][dir] = allowed neighbor tiles
    private final boolean[][][] rules;


    private final int width;
    private final int height;
    private final World world;
    private final GenStats genStats;
    private boolean done = false;

    public SequentialWorldGenerator(World world) {
        this.width = world.getWidth();
        this.height = world.getHeight();
        this.world = world;
        this.genStats = new GenStats(0, 0);

        rules = new boolean[TILE_COUNT][NEIGHBORS][TILE_COUNT];

        buildRules();
    }

    public GenStats getGenStats() {
        return genStats;
    }

    public void step() {
        if (done) {
            return;
        }

        int conflicts = minConflicts(width, height);
        if (conflicts == 0) {
            done = true;
        }

        genStats.setConflicts(conflicts);
        genStats.incrementIterations();
    }

    public boolean isDone() {
        return done;
    }

    private int minConflicts(int width, int height) {
        int totalConflicts = 0;
        List<Tile> tileReplacements = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile current = world.getTile(x, y);
                // count how many neighbor constraints the current tile violates at (x, y)
                int currentConflicts = countConflicts(x, y, width, height, current);

                // if there are conflicts, check against all possible tile types
                if (currentConflicts > 0) {
                    totalConflicts++;
                    tileReplacements.clear();

                    for (Tile tile : Tile.values()) {
                        // exclude empty tiles
                        if (tile == EMPTY) {
                            continue;
                        }
                        // count conflicts if this tile were placed at (x, y)
                        int conflictsForTile = countConflicts(x, y, width, height, tile);
                        // prefer tiles that do not increase conflicts;
                        // occasionally allow worse choices to escape local deadlocks
                        if (conflictsForTile <= currentConflicts || rng.nextFloat() <= 0.02f) {
                            tileReplacements.add(tile);
                        }
                    }
                    // replace the current tile with a random one from all valid replacements
                    if (!tileReplacements.isEmpty()) {
                        Tile replacement = tileReplacements.get(rng.nextInt(tileReplacements.size()));
                        world.getTerrain()[y * width + x] = (byte) replacement.ordinal();
                    }
                }
            }
        }

        return totalConflicts;
    }

    // counts how many of the 8 neighbors violate the adjacency rules
    // if candidate were placed at (x, y)
    private int countConflicts(
            int x, int y, int width, int height, Tile candidate) {
        int con = 0;
        for (int dir = 0; dir < NEIGHBORS; dir++) {
            int nx = (x + neighX[dir] + width) % width;
            int ny = (y + neighY[dir] + height) % height;

            int neighbor = world.getEncodedTile(nx, ny);
            if (!rules[candidate.ordinal()][dir][neighbor]) {
                con++;
            }
        }
        return con;
    }

    // learn adjacency rules from the reference pattern:
    // for each tile type and direction, mark which neighbor tile types are allowed
    private void buildRules() {
        int refRows = 12;
        int refCols = 14;
        int[] reference = generateRef(refRows, refCols);

        for (int y = 0; y < refRows; y++) {
            for (int x = 0; x < refCols; x++) {
                int tileIdx = reference[y * refCols + x];

                for (int direction = 0; direction < NEIGHBORS; direction++) {
                    int nx = x + neighX[direction];
                    int ny = y + neighY[direction];

                    if (nx >= 0 && nx < refCols && ny >= 0 && ny < refRows) {
                        int validNeighborTile = reference[ny * refCols + nx];
                        rules[tileIdx][direction][validNeighborTile] = true;
                    }
                }
            }
        }
    }

    /**
     * Generates a reference image from which the rules are established. - Deep water(0) can only be
     * next to itself and shallow water(1) - Shallow water(1) can be next to deep water(0), itself and
     * grass(2) - Grass(2) can be next to shallow water(1), itself and forrest(3), etc.
     *
     * <pre>
     * |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0|
     * |0| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |0|
     * |0| |1| |2| |2| |2| |2| |2| |2| |2| |2| |2| |2| |1| |0|
     * |0| |1| |2| |3| |3| |3| |3| |3| |3| |3| |3| |2| |1| |0|
     * |0| |1| |2| |3| |4| |4| |4| |4| |4| |4| |3| |2| |1| |0|
     * |0| |1| |2| |3| |4| |5| |5| |5| |5| |4| |3| |2| |1| |0|
     * |0| |1| |2| |3| |4| |5| |5| |5| |5| |4| |3| |2| |1| |0|
     * |0| |1| |2| |3| |4| |4| |4| |4| |4| |4| |3| |2| |1| |0|
     * |0| |1| |2| |3| |3| |3| |3| |3| |3| |3| |3| |2| |1| |0|
     * |0| |1| |2| |2| |2| |2| |2| |2| |2| |2| |2| |2| |1| |0|
     * |0| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |1| |0|
     * |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0| |0|
     * </pre>
     */
    public static int[] generateRef(int rows, int cols) {
        int[] matrix = new int[rows * cols];
        // the matrix is init with zeros, we get the outer 0-ring for free.
        // so total steps is TILE_COUNT(excluding EMPTY) - 1
        // and indices i, j start at 1.
        int steps = TILE_COUNT - 1;
        int k = 1;

        while (steps >= 1) {
            int i = k, j = k;

            // right
            while (j < (cols - 1) - k) {
                matrix[i * cols + j] = k;
                j++;
            }

            // down
            while (i < (rows - 1) - k) {
                matrix[i * cols + j] = k;
                i++;
            }

            // left
            while (j > k) {
                matrix[i * cols + j] = k;
                j--;
            }

            // up
            while (i > k) {
                matrix[i * cols + j] = k;
                i--;
            }

            k++;
            steps--;
        }

        return matrix;
    }

}
