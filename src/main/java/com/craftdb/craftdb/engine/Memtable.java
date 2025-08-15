package com.craftdb.craftdb.engine;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class Memtable {
  // We use ConcurrentSkipListMap as a thread-safe, sorted map.
  // It's a great choice for concurrent reads and writes.
  private final ConcurrentNavigableMap<String, LogEntry> entries;
  private final AtomicLong approximateSize = new AtomicLong(0);

  public Memtable() {
    this.entries = new ConcurrentSkipListMap<>();
  }

  public void put(String key, String value) {
    LogEntry entry = new LogEntry(key, value);
    this.entries.put(key, entry);
    // A rough estimation of size. A real implementation might be more precise.
    approximateSize.addAndGet(key.getBytes().length + (long) value.getBytes().length);
  }

  public Optional<LogEntry> get(String key) {
    return Optional.ofNullable(this.entries.get(key));
  }

  public long getApproximateSize() {
    return approximateSize.get();
  }

  public Collection<LogEntry> getAllEntries() {
    return entries.values();
  }
}
