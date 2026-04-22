package com.example.legaldoc.controller;

import com.example.legaldoc.health.HealthCheckService;
import com.example.legaldoc.model.ServiceHealthStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Reachability of downstream services (S3, SQS, Temporal, Elasticsearch).")
public class HealthController {

    private final HealthCheckService healthCheckService;

    @Operation(
            summary = "Check the status of each downstream service",
            description = "Returns per-service booleans plus a timestamp. "
                    + "A service is considered 'up' if a single lightweight call to it succeeds."
    )
    @ApiResponse(responseCode = "200",
            description = "Current health snapshot.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ServiceHealthStatus.class)))
    @GetMapping("/services")
    public ServiceHealthStatus services() {
        return healthCheckService.checkAll();
    }
}
