package com.craftdb.craftdb.engine;

import com.craftdb.craftdb.storage.SSTableReader;
import com.craftdb.craftdb.storage.SSTableWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Compactor {
  public static void compact(List<Path> sourcePaths, Path outputPath) throws IOException {
    log.info("Starting compaction for {} files into {}", sourcePaths.size(), outputPath);
    // This is a simple, memory-intensive approach. A real DB would use
    // a more sophisticated k-way merge that doesn't load everything into memory.
    Map<String, LogEntry> mergedEntries = sourcePaths.stream()
        // Open each SSTable and read all its entries
        .flatMap(path -> {
          try {
            return SSTableReader.readAll(path).stream();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        // Collect into a map, ensuring we only keep the last-written value for each key.
        // Since stream processing is sequential here, the last entry for a duplicate key wins.
        .collect(Collectors.toMap(
            LogEntry::key,
            entry -> entry,
            (existing, replacement) -> replacement
            // Merge function: keep the replacement
        ));
    // Write the final, merged entries to a new SSTable
    SSTableWriter.write(outputPath, mergedEntries.values());
    log.info("Compaction completed successfully.");
  }
}
