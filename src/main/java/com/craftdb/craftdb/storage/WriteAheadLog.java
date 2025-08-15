package com.craftdb.craftdb.storage;

import com.craftdb.craftdb.engine.LogEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog implements AutoCloseable {

  private final Path logFile;
  private final OutputStream outputStream;

  public WriteAheadLog(Path logFile) throws IOException {
    this.logFile = logFile;
    // Open the file in append mode. Create it if it doesn't exist.
    this.outputStream =
        Files.newOutputStream(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  public synchronized void log(LogEntry entry) throws IOException {
    // A very simple serialization format: "key,value\n"
    // In a real DB, we'd use a more robust binary format.
    String line = entry.key() + "," + entry.value() + "\n";
    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
    // flush() is crucial to ask the OS to write the data from its cache
    // to the disk, ensuring durability.
    outputStream.flush();
  }

  public List<LogEntry> readAll() throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    // A simple implementation. A real DB would use checksums to detect corruption.
    try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",", 2);
        if (parts.length == 2) {
          entries.add(new LogEntry(parts[0], parts[1]));
        }
      }
    }
    return entries;
  }

  @Override
  public void close() throws IOException {
    if (outputStream != null) {
      outputStream.close();
    }
  }
}
