package com.craftdb.craftdb.api;

import com.craftdb.craftdb.engine.DbEngine;
import com.craftdb.craftdb.engine.LogEntry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/craft")
public class CraftController {

  private final DbEngine dbEngine;

  public CraftController() {
    this.dbEngine = new DbEngine();
  }

  @PutMapping("/{key}")
  public ResponseEntity<String> put(@PathVariable String key, @RequestParam String value) {
    log.info("Putting entry for key {}, value: {}", key, value);
    try {
      dbEngine.put(key, value);
      return ResponseEntity.ok("Successfully stored key: " + key);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Failed to store key: " + e.getMessage());
    }
  }

  @GetMapping("/{key}")
  public ResponseEntity<String> get(@PathVariable String key) {
    log.info("Retrieving entry for key: {}", key);
    try {
      Optional<LogEntry> entry = dbEngine.get(key);
      return entry.map(logEntry -> ResponseEntity.ok(logEntry.value()))
          .orElseGet(() -> ResponseEntity.notFound().build());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Failed to retrieve key: " + e.getMessage());
    }
  }
}
