package rw.smart.ecommerce.core.log.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.log.dto.AccessLogResponse;
import rw.smart.ecommerce.core.log.service.AccessLogService;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.util.List;

/**
 * Reads back what the monitoring aspect recorded. The companion to
 * {@code /actuator/metrics}: aggregate numbers there, individual slow
 * invocations here.
 */
@RestController
@RequestMapping("/api/v1/logs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Access logs", description = "Service-method execution monitoring (admin only)")
public class AccessLogController {

    private final AccessLogService accessLogService;

    public AccessLogController(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    @Operation(summary = "Get the most recent access logs (Admin only)",
            description = "Newest first. `limit` defaults to 50 and is capped at 500.")
    @GetMapping
    public ResponseEntity<StandardResponse<List<AccessLogResponse>>> findRecent(
            @RequestParam(defaultValue = "50") int limit) {

        List<AccessLogResponse> logs = accessLogService.findRecent(limit);
        return ResponseEntity.ok(StandardResponse.ok(logs.size() + " access log(s) retrieved", logs));
    }

    @Operation(summary = "Get the slowest service invocations (Admin only)",
            description = "Everything above `thresholdMs`, slowest first - the first place to look at a latency report.")
    @GetMapping("/slow")
    public ResponseEntity<StandardResponse<List<AccessLogResponse>>> findSlow(
            @RequestParam(defaultValue = "500") long thresholdMs,
            @RequestParam(defaultValue = "50") int limit) {

        List<AccessLogResponse> logs = accessLogService.findSlowerThan(thresholdMs, limit);
        return ResponseEntity.ok(StandardResponse.ok(
                logs.size() + " invocation(s) slower than " + thresholdMs + " ms", logs));
    }
}
