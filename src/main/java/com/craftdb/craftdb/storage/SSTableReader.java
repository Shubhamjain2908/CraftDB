package com.craftdb.craftdb.storage;

import com.craftdb.craftdb.engine.LogEntry;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SSTableReader {
  public static List<LogEntry> readAll(Path path) throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    try (FileInputStream fis = new FileInputStream(path.toFile());
        DataInputStream in = new DataInputStream(fis)) {
      while (true) {
        try {
          int keyLen = in.readInt();
          byte[] keyBytes = new byte[keyLen];
          in.readFully(keyBytes);
          int valLen = in.readInt();
          byte[] valBytes = new byte[valLen];
          in.readFully(valBytes);
          entries.add(new LogEntry(new String(keyBytes), new String(valBytes)));
        } catch (EOFException e) {
          // End of file, we're done.
          break;
        }
      }
    }
    return entries;
  }
}
