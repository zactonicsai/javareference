package com.example.legaldoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthStatus {
    private boolean s3Available;
    private boolean sqsAvailable;
    private boolean temporalAvailable;
    private boolean elasticsearchAvailable;
    private Instant timestamp;

    public boolean isAllHealthy() {
        return s3Available && sqsAvailable && temporalAvailable && elasticsearchAvailable;
    }
}
