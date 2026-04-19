# AGENTS Guide for CraftDB

## Big Picture

- `CraftdbApplication` only boots Spring (`src/main/java/com/craftdb/craftdb/CraftdbApplication.java`); storage behavior
  lives outside Spring beans.
- HTTP boundary is `CraftController` (`src/main/java/com/craftdb/craftdb/api/CraftController.java`), which directly
  `new DbEngine()` in its constructor.
- Write path is `PUT -> DbEngine.put -> WAL append -> active Memtable` (`DbEngine.put`, `WriteAheadLog.log`,
  `Memtable.put`).
- Flush path is asynchronous: when `MEMTABLE_THRESHOLD_BYTES` (1 MB) is crossed, `activeMemtable` becomes
  `frozenMemtable` and background `flushToSSTable` writes `sstable-<id>.db`.
- Compaction runs every minute (`scheduleAtFixedRate`) and only triggers when `sstablePaths.size() > 3`, merging files
  via `Compactor.compact`.
- Current read path limitation is intentional for now: `DbEngine.get` checks active/frozen memtables only and returns
  empty if key exists only in SSTables.

## Data + Storage Contracts

- Runtime files are under `data/`: WAL at `data/craftdb.wal`, SSTables at `data/sstable-*.db`.
- WAL serialization is plain UTF-8 `key,value\n` (`WriteAheadLog.log`), parsed with `split(",", 2)` in `readAll`;
  keys/values with raw commas are not safe in this prototype.
- SSTables are length-prefixed binary records (`SSTableWriter.write` / `SSTableReader.readAll`):
  `int keyLen, keyBytes, int valueLen, valueBytes`.
- `LogEntry` is the shared value object (`src/main/java/com/craftdb/craftdb/engine/LogEntry.java`) used in WAL,
  memtable, and SSTable operations.
- `ShardedDbEngine` exists as a conceptual router by `Math.abs(key.hashCode()) % shardCount`; it is not wired into the
  REST API.

## Developer Workflows

- Use JDK 21 toolchain (declared in `build.gradle`).
- Run app: `./gradlew bootRun`.
- Run tests: `./gradlew test`.
- Focused engine test: `./gradlew test --tests com.craftdb.craftdb.engine.DbEngineTest`.
- Quick API smoke test after boot:
    - `curl -X PUT "http://localhost:8080/api/craft/user:1?value=alice"`
    - `curl "http://localhost:8080/api/craft/user:1"`

## Code Patterns to Follow

- Concurrency is explicit in `DbEngine`: `ReentrantReadWriteLock` (`readLock` for `get`, `writeLock` for
  mutation/compaction).
- Flush and compaction are single-thread executors; new background work should preserve ordering/atomicity assumptions
  around `frozenMemtable` and `sstablePaths`.
- Tests use JUnit 5 + AssertJ + `@TempDir` (`DbEngineTest`) and assert behavior by API (`get`) or WAL contents (
  `engine.wal.readAll()`).
- If you add read-from-disk behavior, update both `DbEngine.get` and tests that currently assume memory-only reads.
- Be careful with constructor expectations: `DbEngine(Path dataDir)` currently creates WAL using the parameter but sets
  internal SSTable base directory to `Paths.get("data")`; do not assume custom path fully scopes all files.

## Integration Points + Change Hotspots

- Controller/engine coupling is direct (no Spring DI abstraction), so API changes often require editing
  `CraftController` and `DbEngine` together.
- Storage format changes must be coordinated across `WriteAheadLog`, `SSTableWriter`, `SSTableReader`, WAL recovery (
  `DbEngine.recover`), and compaction.
- Compaction correctness depends on stream order in `Compactor.compact` (`replacement` wins for duplicate keys);
  preserve deterministic ordering if refactoring.
- `README.md` documents architectural limitations; keep it aligned when behavior changes (especially GET semantics and
  WAL lifecycle).
