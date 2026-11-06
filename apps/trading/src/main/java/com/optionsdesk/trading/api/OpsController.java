package com.optionsdesk.trading.api;

import com.optionsdesk.trading.ops.DeployGuard;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations endpoints. {@code GET /ops/deployable}: 200 {@code {"deployable": true}} when a
 * production deploy is allowed now, 409 with the reason when it is not. The CD pipeline calls it
 * before deploying.
 */
@RestController
class OpsController {

  private final DeployGuard guard;

  OpsController(DeployGuard guard) {
    this.guard = guard;
  }

  @GetMapping("/ops/deployable")
  ResponseEntity<Map<String, Object>> deployable() {
    return guard
        .blockedReason()
        .<ResponseEntity<Map<String, Object>>>map(
            reason ->
                ResponseEntity.status(409).body(Map.of("deployable", false, "reason", reason)))
        .orElseGet(() -> ResponseEntity.ok(Map.of("deployable", true)));
  }
}
