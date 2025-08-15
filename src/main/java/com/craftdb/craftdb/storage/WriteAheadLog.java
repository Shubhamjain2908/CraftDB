package com.craftdb.craftdb.storage;

import com.craftdb.craftdb.engine.LogEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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

  @Override
  public void close() throws IOException {
    if (outputStream != null) {
      outputStream.close();
    }
  }
}
