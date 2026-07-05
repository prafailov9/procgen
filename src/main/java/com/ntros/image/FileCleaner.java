package com.ntros.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileCleaner {

  private static final String DELIMITER = "\\";
  private static final String IN_DIR = "C:\\dev\\SOS\\SongsOfSyx.jar.src";
  private static final String IN_DIR_DEL = IN_DIR + DELIMITER;
  private static final String OUT_DIR = "C:\\dev\\SOS\\SOS_FIXED";
  private static final String OUT_DIR_DEL = OUT_DIR + DELIMITER;

  private final StringBuilder pathBuffer = new StringBuilder();

  public static void main(String[] args) throws IOException {
    FileCleaner fc = new FileCleaner();
    Path root = Paths.get(IN_DIR);

    fc.printJavaFiles(root);
    System.out.println(fc.pathBuffer);
  }

  void printJavaFiles(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {

      paths.forEach(
          path -> {
            String outPathName = convertOutPathName(path);
            if (Files.isDirectory(path) && !path.toString().contains(".idea")) {

              pathBuffer.append(outPathName).append(DELIMITER);

            } else if (Files.isRegularFile(path) && path.toString().endsWith(".java")) {
              pathBuffer.append(outPathName).append("\n");
              processFile(path, outPathName);
            }
          });
    }
  }

  // prepare file for output buffer
  String convertOutPathName(Path path) {
    String pathName = String.valueOf(path.toAbsolutePath());
    return pathName.contains(IN_DIR_DEL) ? pathName.replace(IN_DIR_DEL, OUT_DIR_DEL) : pathName;
  }

  void processFile(Path path, String outPathName) {
    try {
      String content = Files.readString(path);

      String updated =
          content.replaceAll("(?m)^/\\*\\s*\\d*\\s*\\*/\\s?", "").replaceAll("(?m)^\\s*$\\R", "");

      Path outputPath = Paths.get(outPathName);
      Path parent = outputPath.getParent();

      if (parent != null) {
        Files.createDirectories(parent);
      }

      Files.writeString(outputPath, updated);
      System.out.printf("Wrote: %s\n", outPathName);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
