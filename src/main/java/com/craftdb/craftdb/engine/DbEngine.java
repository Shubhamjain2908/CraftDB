package com.craftdb.craftdb.engine;

import com.craftdb.craftdb.storage.SSTableWriter;
import com.craftdb.craftdb.storage.WriteAheadLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DbEngine {
  private static final long MEMTABLE_THRESHOLD_BYTES = 1024 * 1024L; // 1MB threshold
  private final Path dataDir;
  private final AtomicLong sstableCounter = new AtomicLong(0);
  private final WriteAheadLog wal;
  // A single-threaded executor to handle flushing in the background
  private final ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
  private Memtable activeMemtable;
  private Memtable frozenMemtable;
  // Use a thread-safe list to store paths to our SSTables
  private final List<Path> sstablePaths = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService compactionExecutor =
      Executors.newSingleThreadScheduledExecutor();

  public DbEngine(Path dataDir) {
    this.dataDir = Paths.get("data");
    try {
      Files.createDirectories(dataDir); // Ensure the data directory exists
      this.wal = new WriteAheadLog(dataDir.resolve("craftdb.wal"));
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize engine", e);
    }
    this.activeMemtable = new Memtable();
    try {
      recover();
    } catch (IOException e) {
      throw new RuntimeException("Failed to recover from WAL", e);
    }
    this.frozenMemtable = null; // No frozen memtable initially
    // Schedule the compaction task to run periodically
    compactionExecutor.scheduleAtFixedRate(this::runCompaction, 1, 1, TimeUnit.MINUTES);
  }

  public DbEngine() { // Default constructor for the main app
    this(Paths.get("data"));
  }

  public void put(String key, String value) {
    LogEntry entry = new LogEntry(key, value);
    try {
      wal.log(entry);
      activeMemtable.put(key, value);
      // Check if memtable has reached its threshold
      if (activeMemtable.getApproximateSize() > MEMTABLE_THRESHOLD_BYTES) {
        triggerFlush();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to write to WAL", e);
    }
  }

  private synchronized void triggerFlush() {
    // If there's already a flush in progress, do nothing
    if (frozenMemtable != null) {
      return;
    }
    log.info("Memtable threshold reached, triggering flush...");
    frozenMemtable = activeMemtable;
    activeMemtable = new Memtable();
    // Submit the flush task to the background thread
    flushExecutor.submit(this::flushToSSTable);
  }

  private void flushToSSTable() {
    try {
      long newSSTableId = sstableCounter.incrementAndGet();
      Path sstablePath = dataDir.resolve("sstable-" + newSSTableId + ".db");
      log.info("Flushing memtable to {}", sstablePath);
      SSTableWriter.write(sstablePath, frozenMemtable.getAllEntries());
      sstablePaths.add(sstablePath); // Add the new SSTable to our list
      log.info("Flush completed successfully for {}", sstablePath);
      // Once flushed, we can clear the frozen memtable
      synchronized (this) {
        frozenMemtable = null;
      }
      // In a real DB, we would now truncate the WAL. We'll skip this for now.
    } catch (IOException e) {
      log.error("Failed to flush memtable to SSTable", e);
      // Handle failure: retry, log, etc.
    }
  }

  public Optional<LogEntry> get(String key) {
    // 1. Check active memtable
    Optional<LogEntry> entry = activeMemtable.get(key);
    if (entry.isPresent()) {
      return entry;
    }
    // 2. Check frozen memtable (if one exists)
    Memtable frozen = this.frozenMemtable; // local reference for thread safety
    if (frozen != null) {
      entry = frozen.get(key);
      if (entry.isPresent()) {
        return entry;
      }
    }
    // 3. Check SSTables on disk (we will implement this reader next)
    // For now, return empty if not in memory
    return Optional.empty();
  }

  private void recover() throws IOException {
    log.info("Starting recovery from WAL...");
    List<LogEntry> entries = this.wal.readAll();
    for (LogEntry entry : entries) {
      this.activeMemtable.put(entry.key(), entry.value());
    }
    log.info("Recovery complete. {} entries loaded into Memtable.", entries.size());
  }

  // In the DbEngine class
  private void runCompaction() {
    // A simple compaction trigger: run if we have more than 3 SSTables.
    if (sstablePaths.size() <= 3) {
      return;
    }
    try {
      log.info("Compaction triggered for {} SSTables.", sstablePaths.size());
      List<Path> sourcesToCompact = new ArrayList<>(sstablePaths);
      long newSSTableId = sstableCounter.incrementAndGet();
      Path compactedSSTablePath = dataDir.resolve("sstable-" + newSSTableId + ".db");
      // Run the compaction
      Compactor.compact(sourcesToCompact, compactedSSTablePath);
      // CRITICAL: Atomically update the state.
      // In a real system, this step is much more complex to ensure crash safety.
      sstablePaths.removeAll(sourcesToCompact);
      sstablePaths.add(compactedSSTablePath);
      // Delete the old, now-obsolete files
      for (Path oldPath : sourcesToCompact) {
        Files.delete(oldPath);
      }
      log.info("Old SSTables deleted.");

    } catch (IOException e) {
      log.error("Compaction failed", e);
    }
  }
}
