package com.ntros.graphics.rendering.panel.sim;

import static com.ntros.graphics.rendering.panel.sim.WorldColorsUtils.*;

import com.ntros.core.SimulationSpeed;
import com.ntros.core.control.IntentTranslator;
import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.data.Motive;
import com.ntros.core.world.snapshot.CreatureSnapshot;
import com.ntros.core.world.snapshot.WorldSnapshot;
import com.ntros.graphics.rendering.panel.AbstractScreenPanel;
import com.ntros.graphics.rendering.panel.ScreenController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * ESC - pause sim, display PAUSE_MENU [1 - 5] - Speed change commands. WASD - pan world by
 * direction
 */
public class WorldSimPanel extends AbstractScreenPanel {

  private static final Logger log = LoggerFactory.getLogger(WorldSimPanel.class);

  private static final double ZOOM_FACTOR = 1.15;
  private static final double MAX_RELATIVE_ZOOM = 74.0;
  private static final double KEYBOARD_PAN_STEP = 74.0;
  private static final int ALPHA_RGB_MAX_VALUE = 256;
  // below this pixels-per-tile scale, biomass+creatures render as one pre-baked overlay image
  // (two blits per paint); above it, the visible tile count is small enough for pretty AA ellipses
  private static final double DETAIL_ZOOM_THRESHOLD = 8.0;
  private static final int TICKS_PER_DAY = 1440; // 1 tick = 1 sim minute
  private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);
  private static final Color HUD_BACKGROUND = new Color(0, 0, 0, 150);
  // pixels per tile
  // latest snapshot received from the sim; only ever replaced, never mutated
  private WorldSnapshot worldSnapshot;
  private CreatureSnapshot creatureSnapshot;
  // previous snapshot's creatures + arrival timing: at detail zoom, positions are interpolated
  // across the interval between snapshots so creatures glide instead of teleporting 10x/sec
  private CreatureSnapshot previousCreatureSnapshot;
  private long lastSnapshotNanos;
  private long interpSpanNanos;
  // prebuilt rendering image of the snapshot
  private BufferedImage cachedImage;
  private final Ellipse2D cacheBiomassDot = new Ellipse2D.Double();
  private final Ellipse2D cacheCreatureDot = new Ellipse2D.Double();

  // cached biomass color-range
  private static final Color[] FOOD_ALPHA_RAMP = buildFoodAlphaRamp();

  // reused pixel buffer for image rebuilds
  private int[] pixelBuffer;
  // terrain content of the last rasterized snapshot; skip the 2M-pixel rebuild when unchanged
  private byte[] lastTerrain;
  // biomass + creatures baked per snapshot as one ARGB layer; painting it is a single blit
  // instead of up to hundreds of thousands of per-tile shape calls
  private BufferedImage overlayImage;
  private int[] overlayPixels; // direct raster access into overlayImage
  // HUD stats, recomputed once per new snapshot
  private int statRabbits;
  private int statFoxes;
  private double statBiomassTotal;
  // motive counts: what the population is doing right now — replaces per-creature hot-loop
  // logging with continuous aggregated observability
  // one counter per Motive ordinal, across all species — the full picture of what the
  // population is doing, not just flee/hunt
  private final int[] statMotiveCounts = new int[Motive.values().length];

  /// Population history: a ring buffer sampled once per sim-hour. Predator-prey dynamics are
  /// phase relationships (prey peak, predators peak later, prey crash) which a single-frame
  /// readout cannot show — you have to see the curves side by side over time.
  private static final int HISTORY_SAMPLES = 336; // 14 sim days at one sample per sim-hour
  private static final int TICKS_PER_SAMPLE = 60; // one sim hour
  private final int[] rabbitHistory = new int[HISTORY_SAMPLES];
  private final int[] foxHistory = new int[HISTORY_SAMPLES];
  private int historyCount;
  private int historyWriteIndex;
  private long lastSampledHour = Long.MIN_VALUE;
  // render health: paints per second and cost of the last paint
  private int fpsCounter;
  private int statFps;
  private long fpsWindowStartNanos;
  private double statPaintMillis;
  // sim health: ticks per second, measured from snapshot tick deltas
  private long tpsWindowStartNanos;
  private long tpsWindowStartTick;
  private long statTps;
  private double scale = 1.0;
  private double coverScale = 1.0;
  private double panX;
  private double panY;
  private boolean viewInitialized;

  public WorldSimPanel(ScreenController screenController, IntentTranslator intentTranslator) {
    super(screenController);

    WorldSimMouseHandler worldSimMouseHandler = new WorldSimMouseHandler(this);
    addMouseWheelListener(worldSimMouseHandler);
    addMouseListener(worldSimMouseHandler);
    addMouseMotionListener(worldSimMouseHandler);

    setFocusable(true);
    setBackground(Color.BLACK);

    // all keyboard input rides InputMap bindings (WHEN_IN_FOCUSED_WINDOW), so it works
    // regardless of which component holds focus; isShowing() guards the hidden card
    installPanKeyBindings();
    installSpeedKeyBindings(intentTranslator);

    // CardLayout fires componentShown when this card becomes the visible one
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (cachedImage != null) {
              fillPanelWithWorld();
            }
          }

          @Override
          public void componentShown(ComponentEvent e) {
            requestFocusInWindow();
            if (!viewInitialized) {
              fillPanelWithWorld();
            }
          }
        });
  }

  /** Accepts the latest published snapshot. Must be called on the EDT. */
  public void present(WorldSnapshot next) {
    if (next == null) {
      return;
    }
    if (next == worldSnapshot) {
      // no new sim state, but at detail zoom we keep repainting at timer rate so the
      // interpolated creature motion stays smooth between snapshots
      if (scale >= DETAIL_ZOOM_THRESHOLD && viewInitialized) {
        repaint();
      }
      return;
    }
    boolean dimensionsChanged =
        worldSnapshot == null || worldSnapshot.width() != next.width() || worldSnapshot.height() != next.height();

    // shift current -> previous for interpolation; measure the actual inter-arrival span
    previousCreatureSnapshot = dimensionsChanged ? null : creatureSnapshot;
    long arrival = System.nanoTime();
    interpSpanNanos = Math.min(500_000_000L, arrival - lastSnapshotNanos);
    lastSnapshotNanos = arrival;

    worldSnapshot = next;
    creatureSnapshot = worldSnapshot.creatureSnapshot();
    // sim-health accounting: tick delta per wall second = actual TPS, independent of paint rate
    long now = System.nanoTime();
    if (now - tpsWindowStartNanos >= 1_000_000_000L) {
      long elapsedNanos = now - tpsWindowStartNanos;
      if (tpsWindowStartNanos != 0) {
        statTps = (next.tick() - tpsWindowStartTick) * 1_000_000_000L / elapsedNanos;
      }
      tpsWindowStartNanos = now;
      tpsWindowStartTick = next.tick();
    }
    // terrain is static per world: only re-rasterize the 2M-pixel image when it actually changed
    if (dimensionsChanged || !Arrays.equals(lastTerrain, next.terrain())) {
      rebuildImage(next);
      lastTerrain = next.terrain();
    }
    computeStats(next);
    samplePopulationHistory(next);
    rebuildOverlay(next);
    // Reset the view only when loading a differently-sized world, not every tick
    if (dimensionsChanged) {
      viewInitialized = false;
      fillPanelWithWorld();
    }
    repaint();
  }

  /** Records one sample per sim-hour. Must run after computeStats. */
  private void samplePopulationHistory(WorldSnapshot snapshot) {
    long hour = snapshot.tick() / TICKS_PER_SAMPLE;
    if (hour == lastSampledHour) {
      return;
    }
    // a new run rewinds the clock: drop the previous world's history rather than splicing it.
    // The arrays are zeroed too, since the autoscale maximum scans all slots.
    if (hour < lastSampledHour) {
      historyCount = 0;
      historyWriteIndex = 0;
      Arrays.fill(rabbitHistory, 0);
      Arrays.fill(foxHistory, 0);
    }
    lastSampledHour = hour;

    rabbitHistory[historyWriteIndex] = statRabbits;
    foxHistory[historyWriteIndex] = statFoxes;
    historyWriteIndex = (historyWriteIndex + 1) % HISTORY_SAMPLES;
    if (historyCount < HISTORY_SAMPLES) {
      historyCount++;
    }
  }

  private void computeStats(WorldSnapshot snapshot) {
    int rabbits = 0;
    int foxes = 0;
    Arrays.fill(statMotiveCounts, 0);
    var creatures = snapshot.creatureSnapshot();
    if (creatures != null) {
      byte rabbitOrdinal = (byte) CreatureType.RABBIT.ordinal();
      for (int id : creatures.aliveIds()) {
        if (creatures.species()[id] == rabbitOrdinal) {
          rabbits++;
        } else {
          foxes++;
        }
        statMotiveCounts[creatures.motives()[id]]++;
      }
    }
    double biomassTotal = 0;
    for (float quantity : snapshot.biomass()) {
      biomassTotal += quantity;
    }
    statRabbits = rabbits;
    statFoxes = foxes;
    statBiomassTotal = biomassTotal;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (cachedImage == null) {
      return;
    }
    long paintStart = System.nanoTime();
    Graphics2D g2 = (Graphics2D) g.create();

    try {
      // transform from world/tile coordinates to screen coordinates
      g2.translate(panX, panY);
      g2.scale(scale, scale);

      // keep terrain tiles pixelated
      g2.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      g2.drawImage(cachedImage, 0, 0, null);
      if (scale >= DETAIL_ZOOM_THRESHOLD) {
        drawBiomassDots(g2);
        drawCreatures(g2);
      } else if (overlayImage != null) {
        g2.drawImage(overlayImage, 0, 0, null);
      }
    } finally {
      g2.dispose();
    }

    // day/night tint over the world, beneath the HUD
    drawDayNightTint(g);

    // render-health accounting: last paint cost + paints per wall second
    statPaintMillis = (System.nanoTime() - paintStart) / 1_000_000.0;
    fpsCounter++;
    if (paintStart - fpsWindowStartNanos >= 1_000_000_000L) {
      statFps = fpsCounter;
      fpsCounter = 0;
      fpsWindowStartNanos = paintStart;
    }

    // HUD draws in screen space, on top of everything
    Graphics2D hud = (Graphics2D) g.create();
    try {
      drawHud(hud);
    } finally {
      hud.dispose();
    }
  }

  /**
   * Darkens the scene by sim time of day: pitch-dark blue at midnight, clear at noon, smooth
   * cosine in between. One translucent fill over the viewport — effectively free.
   */
  private void drawDayNightTint(Graphics g) {
    if (worldSnapshot == null) {
      return;
    }
    double hourOfDay = (worldSnapshot.tick() % TICKS_PER_DAY) / 60.0;
    // 1.0 at midnight, 0.0 at noon
    double darkness = (1 + Math.cos(Math.PI * 2 * hourOfDay / 24.0)) / 2.0;
    int tintAlpha = (int) (darkness * 150);
    if (tintAlpha <= 0) {
      return;
    }
    Graphics2D tint = (Graphics2D) g.create();
    try {
      tint.setColor(new Color(10, 12, 48, tintAlpha));
      tint.fillRect(0, 0, getWidth(), getHeight());
    } finally {
      tint.dispose();
    }
  }

  private void drawHud(Graphics2D g2) {
    if (worldSnapshot == null) {
      return;
    }
    long tick = worldSnapshot.tick();
    long day = tick / TICKS_PER_DAY;
    long hour = (tick % TICKS_PER_DAY) / 60;
    long minute = tick % 60;

    String timeLine = String.format("day %d  %02d:%02d  tick %,d", day, hour, minute, tick);
    String popLine =
        String.format(
            "rabbits %d  foxes %d  biomass %,.0f", statRabbits, statFoxes, statBiomassTotal);
    // one counter per motive, built from the enum so new motives appear automatically
    StringBuilder motiveText = new StringBuilder("motives");
    for (Motive motive : Motive.values()) {
      motiveText
          .append("  ")
          .append(motive.name().toLowerCase())
          .append(' ')
          .append(statMotiveCounts[motive.ordinal()]);
    }
    String motiveLine = motiveText.toString();
    String perfLine =
        String.format("tps %d  fps %d  paint %.1fms", statTps, statFps, statPaintMillis);

    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setFont(HUD_FONT);
    FontMetrics metrics = g2.getFontMetrics();

    int pad = 8;
    int boxWidth =
        Math.max(
                Math.max(metrics.stringWidth(timeLine), metrics.stringWidth(popLine)),
                Math.max(metrics.stringWidth(motiveLine), metrics.stringWidth(perfLine)))
            + pad * 2;
    int lineHeight = metrics.getHeight();
    int boxHeight = lineHeight * 4 + pad * 2;

    g2.setColor(HUD_BACKGROUND);
    g2.fillRoundRect(10, 10, boxWidth, boxHeight, 12, 12);
    g2.setColor(Color.WHITE);
    g2.drawString(timeLine, 10 + pad, 10 + pad + metrics.getAscent());
    g2.drawString(popLine, 10 + pad, 10 + pad + lineHeight + metrics.getAscent());
    g2.drawString(motiveLine, 10 + pad, 10 + pad + lineHeight * 2 + metrics.getAscent());
    g2.drawString(perfLine, 10 + pad, 10 + pad + lineHeight * 3 + metrics.getAscent());

    drawPopulationChart(g2, 10, 10 + boxHeight + 6, Math.max(boxWidth, 260), metrics);
  }

  /**
   * Rolling population curves for both species. Each is scaled to its own maximum: rabbits
   * outnumber foxes ~5:1, so a shared axis would flatten the fox line into the baseline and hide
   * exactly the predator dynamics worth watching.
   */
  private void drawPopulationChart(Graphics2D g2, int x, int y, int width, FontMetrics metrics) {
    if (historyCount < 2) {
      return;
    }
    int chartHeight = 70;
    int pad = 6;
    int plotHeight = chartHeight - pad * 2 - metrics.getHeight();
    int plotTop = y + pad + metrics.getHeight();
    int plotWidth = width - pad * 2;

    g2.setColor(HUD_BACKGROUND);
    g2.fillRoundRect(x, y, width, chartHeight, 12, 12);

    int rabbitMax = Math.max(1, maxOf(rabbitHistory));
    int foxMax = Math.max(1, maxOf(foxHistory));

    g2.setColor(Color.WHITE);
    g2.drawString(
        String.format(
            "last %d sim days   rabbits peak %d   foxes peak %d",
            historyCount / 24, rabbitMax, foxMax),
        x + pad,
        y + pad + metrics.getAscent());

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    plotSeries(g2, rabbitHistory, rabbitMax, RABBIT_COLOR, x + pad, plotTop, plotWidth, plotHeight);
    plotSeries(g2, foxHistory, foxMax, FOX_COLOR, x + pad, plotTop, plotWidth, plotHeight);
  }

  private void plotSeries(
      Graphics2D g2, int[] history, int maxValue, Color color, int x, int y, int w, int h) {
    int[] xs = new int[historyCount];
    int[] ys = new int[historyCount];
    // oldest sample first: once the buffer wraps, the oldest entry sits at the write cursor
    int oldest = historyCount < HISTORY_SAMPLES ? 0 : historyWriteIndex;

    for (int i = 0; i < historyCount; i++) {
      int value = history[(oldest + i) % HISTORY_SAMPLES];
      xs[i] = x + (int) ((long) i * w / (historyCount - 1));
      ys[i] = y + h - (int) ((long) value * h / maxValue);
    }
    g2.setColor(color);
    g2.drawPolyline(xs, ys, historyCount);
  }

  private static int maxOf(int[] values) {
    int max = 0;
    for (int value : values) {
      max = Math.max(max, value);
    }
    return max;
  }

  private void drawBiomassDots(Graphics2D g2) {
    float[] biomass = worldSnapshot.biomass();
    int worldWidth = worldSnapshot.width();
    int worldHeight = worldSnapshot.height();

    // Only inspect tiles currently visible on screen.
    int firstX = Math.max(0, (int) Math.floor(-panX / scale));
    int firstY = Math.max(0, (int) Math.floor(-panY / scale));

    int lastX = Math.min(worldWidth - 1, (int) Math.ceil((getWidth() - panX) / scale));
    int lastY = Math.min(worldHeight - 1, (int) Math.ceil((getHeight() - panY) / scale));

    // only called above DETAIL_ZOOM_THRESHOLD, so the visible tile count is small
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Each tile is 1x1 in world coordinates.
    double diameter = 0.72;
    double inset = (1.0 - diameter) / 2.0;

    for (int tileY = firstY; tileY <= lastY; tileY++) {
      int rowOffset = tileY * worldWidth;

      for (int tileX = firstX; tileX <= lastX; tileX++) {
        float quantity = biomass[rowOffset + tileX];

        if (quantity == 0) {
          continue;
        }

        // modify inputs by the biomass quantity, output already cached
        int alpha = (int) Math.min(255f, 100 + quantity * 17);
        g2.setColor(FOOD_ALPHA_RAMP[alpha]);
        cacheBiomassDot.setFrame(tileX + inset, tileY + inset, diameter, diameter);
        g2.fill(cacheBiomassDot);
      }
    }
  }

  /**
   * Bakes biomass and creatures into one ARGB layer, once per snapshot. Writing pixels directly
   * into the raster costs a few ms at 10 snapshots/sec; painting becomes a single blit regardless
   * of how much life the world holds.
   */
  private void rebuildOverlay(WorldSnapshot snapshot) {
    int width = snapshot.width();
    int height = snapshot.height();

    if (overlayImage == null
        || overlayImage.getWidth() != width
        || overlayImage.getHeight() != height) {
      overlayImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      overlayPixels = ((DataBufferInt) overlayImage.getRaster().getDataBuffer()).getData();
    }
    Arrays.fill(overlayPixels, 0);

    float[] biomass = snapshot.biomass();
    for (int i = 0; i < biomass.length; i++) {
      float quantity = biomass[i];
      if (quantity > 0) {
        int alpha = (int) Math.min(255f, 100 + quantity * 17);
        overlayPixels[i] = FOOD_ALPHA_RAMP[alpha].getRGB();
      }
    }

    var creatures = snapshot.creatureSnapshot();
    if (creatures != null) {
      float[] xs = creatures.x();
      float[] ys = creatures.y();
      byte[] species = creatures.species();
      for (int id : creatures.aliveIds()) {
        overlayPixels[(int) ys[id] * width + (int) xs[id]] = CREATURE_COLORS[species[id]].getRGB();
      }
    }
  }

  private void drawCreatures(Graphics2D g2) {
    var creatures = worldSnapshot.creatureSnapshot();
    if (creatures == null) {
      return;
    }
    float[] xs = creatures.x();
    float[] ys = creatures.y();
    byte[] species = creatures.species();

    // visible world-region bounds, same convention as the biomass culling
    double firstX = -panX / scale;
    double firstY = -panY / scale;
    double lastX = (getWidth() - panX) / scale;
    double lastY = (getHeight() - panY) / scale;

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // creatures sit above biomass; a darker under-disc keeps them readable on any terrain
    double shadowDiameter = 0.95;
    double bodyDiameter = 0.72;
    double shadowInset = (1.0 - shadowDiameter) / 2.0;
    double bodyInset = (1.0 - bodyDiameter) / 2.0;

    // interpolation factor: how far we are between the previous and current snapshot
    float lerp = 1f;
    float[] prevXs = null;
    float[] prevYs = null;
    if (previousCreatureSnapshot != null && interpSpanNanos > 0) {
      lerp = Math.min(1f, (System.nanoTime() - lastSnapshotNanos) / (float) interpSpanNanos);
      prevXs = previousCreatureSnapshot.x();
      prevYs = previousCreatureSnapshot.y();
    }

    for (int id : creatures.aliveIds()) {
      float cx = xs[id];
      float cy = ys[id];
      if (prevXs != null) {
        float px = prevXs[id];
        float py = prevYs[id];
        // teleport guard: newborn or reused id — don't glide across the map
        if (Math.abs(cx - px) <= 3f && Math.abs(cy - py) <= 3f) {
          cx = px + (cx - px) * lerp;
          cy = py + (cy - py) * lerp;
        }
      }
      if (cx + 1 < firstX || cx > lastX || cy + 1 < firstY || cy > lastY) {
        continue; // off screen
      }

      g2.setColor(CREATURE_SHADOW_COLOR);
      cacheCreatureDot.setFrame(cx + shadowInset, cy + shadowInset, shadowDiameter, shadowDiameter);
      g2.fill(cacheCreatureDot);

      g2.setColor(CREATURE_COLORS[species[id]]);
      cacheCreatureDot.setFrame(cx + bodyInset, cy + bodyInset, bodyDiameter, bodyDiameter);
      g2.fill(cacheCreatureDot);
    }
  }

  void zoomAt(Point cursor, double wheelRotation) {
    if (cachedImage == null) {
      return;
    }

    if (!viewInitialized) {
      fillPanelWithWorld();
    }

    if (!viewInitialized) {
      return;
    }

    double oldScale = scale;
    double requestedScale = oldScale * Math.pow(ZOOM_FACTOR, -wheelRotation);

    // Zooming below coverScale would necessarily reveal black background.
    double newScale =
        Math.max(coverScale, Math.min(coverScale * MAX_RELATIVE_ZOOM, requestedScale));

    if (Double.compare(newScale, oldScale) == 0) {
      return;
    }

    double worldX = (cursor.x - panX) / oldScale;
    double worldY = (cursor.y - panY) / oldScale;

    scale = newScale;

    // Keep the same world position under the cursor.
    panX = cursor.x - worldX * newScale;
    panY = cursor.y - worldY * newScale;

    // Cursor anchoring can attempt to move an edge into the viewport.
    constrainPanToWorld();
    repaint();
  }

  void panBy(double dx, double dy) {
    if (!viewInitialized) {
      fillPanelWithWorld();
    }

    if (!viewInitialized) {
      return;
    }

    panX += dx;
    panY += dy;

    constrainPanToWorld();
    repaint();
  }

  public void resetView() {
    viewInitialized = false;
    fillPanelWithWorld();
  }

  private void fillPanelWithWorld() {
    if (cachedImage == null || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }

    double scaleX = (double) getWidth() / cachedImage.getWidth();
    double scaleY = (double) getHeight() / cachedImage.getHeight();

    // removes black bars but might crop real-world tiles
    coverScale = Math.max(scaleX, scaleY);
    scale = coverScale;

    // Center the image in any unused space.
    panX = (getWidth() - cachedImage.getWidth() * scale) / 2.0;
    panY = (getHeight() - cachedImage.getHeight() * scale) / 2.0;

    constrainPanToWorld();
    viewInitialized = true;
    repaint();
  }

  private void constrainPanToWorld() {
    if (cachedImage == null) {
      return;
    }

    double worldWidth = cachedImage.getWidth() * scale;
    double worldHeight = cachedImage.getHeight() * scale;

    panX = constrainAxis(panX, getWidth(), worldWidth);
    panY = constrainAxis(panY, getHeight(), worldHeight);
  }

  private static double constrainAxis(double position, double viewportSize, double worldSize) {

    // Normally impossible because scale >= coverScale, but this also
    // protects against floating-point and initialization edge cases.
    if (worldSize <= viewportSize) {
      return (viewportSize - worldSize) / 2.0;
    }

    // The image origin must remain between:
    //
    // viewportSize - worldSize: far edge aligned
    // 0:                         near edge aligned
    double minimumPosition = viewportSize - worldSize;
    double maximumPosition = 0.0;

    return Math.max(minimumPosition, Math.min(maximumPosition, position));
  }

  private void rebuildImage(WorldSnapshot snapshot) {
    int width = snapshot.width();
    int height = snapshot.height();

    if (cachedImage == null
        || cachedImage.getWidth() != width
        || cachedImage.getHeight() != height) {
      cachedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      pixelBuffer = new int[width * height];
    }

    byte[] terrain = snapshot.terrain();

    for (int i = 0; i < terrain.length; i++) {
      // color terrain with deterministic per-tile brightness variation
      pixelBuffer[i] = jitterRgb(TILE_RGB[terrain[i]], i);
    }
    cachedImage.setRGB(0, 0, width, height, pixelBuffer, 0, width);
  }

  /**
   * Deterministic ~±6% per-tile brightness variation so terrain reads as textured ground instead
   * of flat paint. Pure function of the tile index — the same world always looks the same.
   */
  private static int jitterRgb(int rgb, int tileIndex) {
    int h = tileIndex;
    h ^= h >>> 16;
    h *= 0x7feb352d;
    h ^= h >>> 15;
    h *= 0x846ca68b;
    h ^= h >>> 16;

    int scale = 256 + ((h & 31) - 16); // 240..271 => roughly ±6% brightness
    int r = Math.min(255, ((rgb >> 16 & 0xFF) * scale) >> 8);
    int g = Math.min(255, ((rgb >> 8 & 0xFF) * scale) >> 8);
    int b = Math.min(255, ((rgb & 0xFF) * scale) >> 8);
    return (r << 16) | (g << 8) | b;
  }

  private static Color[] buildFoodAlphaRamp() {
    Color[] ramp = new Color[256];
    int rgb = FOOD_COLOR_HEX & 0x00FF_FFFF;
    for (int alpha = 0; alpha < 256; alpha++) {
      ramp[alpha] = new Color((alpha << 24) | rgb, true);
    }
    return ramp;
  }

  /// KEY Bindings for panning
  private void installPanKeyBindings() {
    // Camera semantics:
    // W moves the camera north, so the rendered world moves downward.
    bindPanKey(KeyEvent.VK_W, "camera-up", 0, KEYBOARD_PAN_STEP);

    bindPanKey(KeyEvent.VK_S, "camera-down", 0, -KEYBOARD_PAN_STEP);

    bindPanKey(KeyEvent.VK_A, "camera-left", KEYBOARD_PAN_STEP, 0);

    bindPanKey(KeyEvent.VK_D, "camera-right", -KEYBOARD_PAN_STEP, 0);
  }

  private void bindPanKey(int keyCode, String actionName, double dx, double dy) {

    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(keyCode, 0), actionName);

    getActionMap()
        .put(
            actionName,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                // Prevent the hidden simulation card from reacting.
                if (isShowing()) {
                  panBy(dx, dy);
                }
              }
            });
  }

  /// KEY Bindings for simulation speed
  private void installSpeedKeyBindings(IntentTranslator intentTranslator) {
    bindSpeedKey(intentTranslator, KeyEvent.VK_1, "speed-x1", SimulationSpeed.X1);
    bindSpeedKey(intentTranslator, KeyEvent.VK_2, "speed-x5", SimulationSpeed.X5);
    bindSpeedKey(intentTranslator, KeyEvent.VK_3, "speed-x25", SimulationSpeed.X25);
    bindSpeedKey(intentTranslator, KeyEvent.VK_4, "speed-x250", SimulationSpeed.X250);
    bindSpeedKey(intentTranslator, KeyEvent.VK_5, "speed-max", SimulationSpeed.MAX);
    bindSpeedKey(intentTranslator, KeyEvent.VK_0, "speed-paused", SimulationSpeed.PAUSED);
  }

  private void bindSpeedKey(
      IntentTranslator intentTranslator, int keyCode, String actionName, SimulationSpeed speed) {

    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(keyCode, 0), actionName);

    getActionMap()
        .put(
            actionName,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                // Prevent the hidden simulation card from reacting.
                if (isShowing()) {
                  intentTranslator.changeSpeed(speed);
                }
              }
            });
  }
}
