package com.craftdb.craftdb.engine;

import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class Memtable {
  // We use ConcurrentSkipListMap as a thread-safe, sorted map.
  // It's a great choice for concurrent reads and writes.
  private final ConcurrentNavigableMap<String, LogEntry> entries;

  public Memtable() {
    this.entries = new ConcurrentSkipListMap<>();
  }

  public void put(String key, String value) {
    // A null value can signify a "tombstone" or deletion marker.
    LogEntry entry = new LogEntry(key, value);
    this.entries.put(key, entry);
  }

  public Optional<LogEntry> get(String key) {
    return Optional.ofNullable(this.entries.get(key));
  }
}
