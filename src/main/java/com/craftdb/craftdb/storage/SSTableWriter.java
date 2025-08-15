package com.craftdb.craftdb.storage;

import com.craftdb.craftdb.engine.LogEntry;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class SSTableWriter {
  public static void write(Path path, Collection<LogEntry> entries) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(path.toFile());
        DataOutputStream out = new DataOutputStream(fos)) {
      for (LogEntry entry : entries) {
        byte[] keyBytes = entry.key().getBytes();
        byte[] valueBytes = entry.value().getBytes();
        // Write key length, then key
        out.writeInt(keyBytes.length);
        out.write(keyBytes);
        // Write value length, then value
        out.writeInt(valueBytes.length);
        out.write(valueBytes);
      }
    }
  }
}
