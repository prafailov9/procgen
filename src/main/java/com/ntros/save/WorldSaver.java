package com.ntros.save;

import static com.ntros.core.ecs.data.CreatureType.FOX;
import static com.ntros.core.ecs.data.CreatureType.RABBIT;

import com.ntros.core.Lifecycle;
import com.ntros.core.channel.Channel;
import com.ntros.core.ecs.data.DeathCause;
import com.ntros.core.ecs.data.Motive;
import com.ntros.core.world.snapshot.StatsSnapshot;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains the analytics channel to a CSV time series, one row per published sample.
 *
 * <p>Deliberately a queue consumer rather than a reader of the latest-snapshot reference: the
 * renderer only ever wants the newest frame, but a log that skips rows leaves gaps in the time
 * base, and cross-correlating a series with irregular gaps gives a meaningless phase lag.
 *
 * <p>Rows arrive on sim-tick boundaries, so a file's row count measures SIM progress, not wall
 * time: a big world running at ~65 tps produces roughly one row a second, while a small world at
 * ~17K tps produces hundreds.
 */
public class WorldSaver implements Lifecycle, Runnable {

  private static final String DIR = "run_logs";
  private static final String FILENAME_PREFIX = "run";
  private static final int MAX_LINES = 12_000;
  private static final Pattern RUN_FILE = Pattern.compile(FILENAME_PREFIX + "(\\d+)");
  private static final Logger log = LoggerFactory.getLogger(WorldSaver.class);

  private final Thread saverThread;
  private final Path dir;
  private final Channel<StatsSnapshot> statsChannel;

  private long fileCounter;
  private int rowCount;
  private BufferedWriter writer;

  public WorldSaver(Channel<StatsSnapshot> statsChannel) {
    this.statsChannel = statsChannel;

    dir = createWorkingDir();
    // Next index past the highest existing one. Counting files instead breaks as soon as a run is
    // deleted from the middle: with run0 and run2 present the count is 2, and creating "run2"
    // throws FileAlreadyExistsException on the saver thread.
    fileCounter = highestExistingRunIndex(dir) + 1;
    openNextFile();
    saverThread = new Thread(this, "saver-1");
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        // blocking take, not poll: samples arrive as rarely as once a wall-second on a big world,
        // and spinning on poll() in between burned a whole core - stolen from the sim thread that
        // is already the bottleneck
        StatsSnapshot stats = statsChannel.take();
        if (stats == null) {
          continue;
        }
        if (rowCount >= MAX_LINES) {
          openNextFile();
        }
        writeRow(stats);
        rowCount++;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // normal shutdown path, not an error
    } catch (RuntimeException e) {
      // without this the thread dies silently and the run log just stops mid-file
      log.error("Saver failed, run log incomplete", e);
    } finally {
      closeWriter();
    }
    log.info("Saver stopped after {} rows", rowCount);
  }

  @Override
  public void start() {
    saverThread.start();
  }

  @Override
  public void stop() throws InterruptedException {
    saverThread.interrupt();
    saverThread.join();
  }

  private void writeRow(StatsSnapshot stats) {
    StringBuilder row = new StringBuilder(160);
    row.append(stats.tick())
        .append(',')
        .append(stats.rabbits())
        .append(',')
        .append(stats.foxes())
        .append(',')
        .append(String.format("%.2f", stats.biomassTotal()))
        // Flows, split by cause. Births alone hid the churn: one run showed ~100K rabbit births a
        // day against a standing population of 21K, which is only visible with deaths alongside.
        .append(',')
        .append(stats.births(RABBIT))
        .append(',')
        .append(stats.deaths(RABBIT, DeathCause.STARVED))
        .append(',')
        .append(stats.deaths(RABBIT, DeathCause.EATEN))
        .append(',')
        .append(stats.births(FOX))
        .append(',')
        .append(stats.deaths(FOX, DeathCause.STARVED));
    for (Motive motive : Motive.values()) {
      row.append(',').append(stats.motiveCounts()[motive.ordinal()]);
    }
    writeLine(row.toString());
  }

  /** Built from the enums so a new motive cannot silently desync the header from the rows. */
  private static String csvHeader() {
    StringBuilder header =
        new StringBuilder(
            "tick,rabbits,foxes,biomass,"
                + "rabbit_births,rabbit_starved,rabbit_eaten,"
                + "fox_births,fox_starved");
    for (Motive motive : Motive.values()) {
      header.append(",motive_").append(motive.name().toLowerCase());
    }
    return header.toString();
  }

  private void openNextFile() {
    closeWriter();
    Path file = dir.resolve(FILENAME_PREFIX + fileCounter++ + ".csv");
    try {
      writer = Files.newBufferedWriter(file);
    } catch (IOException e) {
      throw new RuntimeException("Could not open run log " + file, e);
    }
    rowCount = 0;
    writeLine(csvHeader());
    log.info("Writing run log to {}", file.toAbsolutePath());
  }

  private void writeLine(String line) {
    try {
      // buffered: the previous version reopened and closed the file for every single row, which
      // on a fast small-world run meant hundreds of open/close cycles per second
      writer.write(line);
      writer.newLine();
    } catch (IOException e) {
      throw new RuntimeException("Could not write to run log", e);
    }
  }

  private void closeWriter() {
    if (writer == null) {
      return;
    }
    try {
      writer.close(); // flushes; without it the buffered tail of a run is lost on stop
    } catch (IOException e) {
      log.warn("Could not close run log", e);
    }
    writer = null;
  }

  private Path createWorkingDir() {
    try {
      return Files.createDirectories(Path.of(DIR));
    } catch (IOException ex) {
      throw new RuntimeException("Could not create " + DIR, ex);
    }
  }

  private long highestExistingRunIndex(Path dir) {
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(Files::isRegularFile)
          .map(path -> RUN_FILE.matcher(path.getFileName().toString()))
          .filter(Matcher::find)
          .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
          .max()
          .orElse(-1L);
    } catch (IOException ex) {
      throw new RuntimeException("Could not list " + dir, ex);
    }
  }
}
