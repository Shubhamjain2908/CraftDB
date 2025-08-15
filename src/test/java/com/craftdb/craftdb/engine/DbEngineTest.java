package com.craftdb.craftdb.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbEngineTest {

  @TempDir
  Path tempDir; // JUnit will create and clean up a temporary directory for us

  @Test
  void testPutAndGet() {
    // We'll modify DbEngine to accept a directory
    DbEngine engine = new DbEngine(tempDir);
    // Test 1: Get a non-existent key
    Optional<LogEntry> notFound = engine.get("key1");
    assertThat(notFound).isEmpty();
    // Test 2: Put and then get the key
    engine.put("key1", "value1");
    Optional<LogEntry> found = engine.get("key1");
    assertThat(found).isPresent();
    assertThat(found.get().key()).isEqualTo("key1");
    assertThat(found.get().value()).isEqualTo("value1");
  }

  @Test
  void testDurabilityWithWalRecovery() {
    // Phase 1: Create an engine, write some data, and then "crash"
    DbEngine engine1 = new DbEngine(tempDir);
    engine1.put("key1", "value1");
    engine1.put("key2", "value2");
    // engine1 is now "gone", but the WAL file remains on disk in tempDir
    // Phase 2: Create a new engine instance, which should recover the data
    DbEngine engine2 = new DbEngine(tempDir);
    Optional<LogEntry> found1 = engine2.get("key1");
    Optional<LogEntry> found2 = engine2.get("key2");
    // Assert that the data was recovered successfully
    assertThat(found1).isPresent();
    assertThat(found1.get().value()).isEqualTo("value1");
    assertThat(found2).isPresent();
    assertThat(found2.get().value()).isEqualTo("value2");
  }

  @Test
  void testConcurrentPuts() throws InterruptedException, IOException {
    int numThreads = 10;
    int putsPerThread = 100;
    int totalPuts = numThreads * putsPerThread;
    DbEngine engine = new DbEngine(tempDir);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch latch = new CountDownLatch(totalPuts);
    // Submit all the put operations from multiple threads
    for (int i = 0; i < totalPuts; i++) {
      final int index = i;
      executor.submit(() -> {
        try {
          engine.put("key" + index, "value" + index);
        } finally {
          latch.countDown();
        }
      });
    }
    // Wait for all puts to complete
    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    // Now, verify the data. We can either read from the same engine
    // or create a new one to test recovery as well.
    // Let's create a new one to be thorough.
    DbEngine recoveryEngine = new DbEngine(tempDir);
    long totalEntries = 0;
    // A simple way to count entries for now is to read the WAL
    totalEntries = recoveryEngine.wal.readAll().stream().map(LogEntry::key).distinct().count();
    assertThat(totalEntries).isEqualTo(totalPuts);
  }
}
