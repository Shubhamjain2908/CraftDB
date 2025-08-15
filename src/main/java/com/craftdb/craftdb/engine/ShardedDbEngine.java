package com.craftdb.craftdb.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A conceptual sharding router that manages multiple DbEngine instances (shards).
 */
public class ShardedDbEngine {
  private final List<DbEngine> shards;
  private final int shardCount;

  public ShardedDbEngine(int shardCount, Path baseDataDir) {
    this.shardCount = shardCount;
    this.shards = new ArrayList<>(shardCount);
    for (int i = 0; i < shardCount; i++) {
      // Each shard gets its own data directory, e.g., data/shard-0, data/shard-1
      Path shardDir = baseDataDir.resolve("shard-" + i);
      shards.add(new DbEngine(shardDir));
    }
  }

  /**
   * Finds the correct shard for a given key.
   *
   * @param key The key to find the shard for.
   * @return The DbEngine instance that acts as the target shard.
   */
  private DbEngine getShard(String key) {
    // Simple hash-based partitioning logic
    int shardIndex = Math.abs(key.hashCode()) % shardCount;
    return shards.get(shardIndex);
  }

  public void put(String key, String value) {
    // Route the put operation to the correct shard
    getShard(key).put(key, value);
  }

  public Optional<LogEntry> get(String key) {
    // Route the get operation to the correct shard
    return getShard(key).get(key);
  }
}
