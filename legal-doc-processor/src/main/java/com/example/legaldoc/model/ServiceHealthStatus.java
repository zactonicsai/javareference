package com.example.legaldoc.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reachability snapshot of the pipeline's downstream services.")
public class ServiceHealthStatus {
    @Schema(description = "True if S3 responded to a ListBuckets call.")
    private boolean s3Available;
    @Schema(description = "True if SQS responded to a ListQueues call.")
    private boolean sqsAvailable;
    @Schema(description = "True if Temporal's frontend responded to a GetClusterInfo call.")
    private boolean temporalAvailable;
    @Schema(description = "True if Elasticsearch's cluster health endpoint responded.")
    private boolean elasticsearchAvailable;
    @Schema(description = "When this snapshot was taken (UTC).")
    private Instant timestamp;

    @Schema(description = "True iff every downstream dependency reported available.")
    public boolean isAllHealthy() {
        return s3Available && sqsAvailable && temporalAvailable && elasticsearchAvailable;
    }
}
