package com.example.legaldoc.health;

import com.example.legaldoc.model.ServiceHealthStatus;
import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private final S3Service s3Service;
    private final SqsService sqsService;
    private final ElasticsearchService elasticsearchService;
    private final WorkflowServiceStubs workflowServiceStubs;

    public ServiceHealthStatus checkAll() {
        return ServiceHealthStatus.builder()
                .s3Available(s3Service.isAvailable())
                .sqsAvailable(sqsService.isAvailable())
                .elasticsearchAvailable(elasticsearchService.isAvailable())
                .temporalAvailable(isTemporalAvailable())
                .timestamp(Instant.now())
                .build();
    }

    private boolean isTemporalAvailable() {
        try {
            workflowServiceStubs.blockingStub()
                    .getSystemInfo(io.temporal.api.workflowservice.v1.GetSystemInfoRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            log.debug("Temporal unavailable: {}", e.getMessage());
            return false;
        }
    }
}
