package com.craftdb.craftdb.engine;

import com.craftdb.craftdb.storage.WriteAheadLog;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

public class DbEngine {
  private final Memtable memtable;
  private final WriteAheadLog wal;

  public DbEngine() {
    this.memtable = new Memtable();
    try {
      // Let's store our WAL file in a 'data' directory.
      this.wal = new WriteAheadLog(Paths.get("data/craftdb.wal"));
    } catch (IOException e) {
      // In a real app, handle this more gracefully.
      throw new RuntimeException("Failed to initialize WAL", e);
    }
  }

  public void put(String key, String value) {
    LogEntry entry = new LogEntry(key, value);
    try {
      // 1. Write to WAL first
      wal.log(entry);
      // 2. Then write to Memtable
      memtable.put(key, value);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write to WAL", e);
    }
  }

  public Optional<LogEntry> get(String key) {
    // For now, we only read from the Memtable.
    // We'll add disk lookups in the next module.
    return memtable.get(key);
  }
}
