package com.example.legaldoc.health;

import com.example.legaldoc.model.ServiceHealthStatus;
import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckServiceTest {

    // Note: Temporal's WorkflowServiceStubs.blockingStub() returns a generated gRPC stub
    // that is non-trivial to mock cleanly. HealthCheckService catches any exception from
    // the stub call and returns false, so these tests just drive S3/SQS/ES flags and
    // treat Temporal as "down" (since the mock throws).

    @Test
    void reportsAllDependenciesDown() {
        S3Service s3 = mock(S3Service.class);
        SqsService sqs = mock(SqsService.class);
        ElasticsearchService es = mock(ElasticsearchService.class);
        WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);

        when(s3.isAvailable()).thenReturn(false);
        when(sqs.isAvailable()).thenReturn(false);
        when(es.isAvailable()).thenReturn(false);
        when(stubs.blockingStub()).thenThrow(new RuntimeException("boom"));

        HealthCheckService svc = new HealthCheckService(s3, sqs, es, stubs);
        ServiceHealthStatus status = svc.checkAll();

        assertThat(status.isS3Available()).isFalse();
        assertThat(status.isSqsAvailable()).isFalse();
        assertThat(status.isElasticsearchAvailable()).isFalse();
        assertThat(status.isTemporalAvailable()).isFalse();
        assertThat(status.isAllHealthy()).isFalse();
        assertThat(status.getTimestamp()).isNotNull();
    }

    @Test
    void reportsS3AndSqsAndEsUp_TemporalStillFalseBecauseStubMockThrows() {
        S3Service s3 = mock(S3Service.class);
        SqsService sqs = mock(SqsService.class);
        ElasticsearchService es = mock(ElasticsearchService.class);
        WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);

        when(s3.isAvailable()).thenReturn(true);
        when(sqs.isAvailable()).thenReturn(true);
        when(es.isAvailable()).thenReturn(true);
        when(stubs.blockingStub()).thenThrow(new RuntimeException("no real server"));

        HealthCheckService svc = new HealthCheckService(s3, sqs, es, stubs);
        ServiceHealthStatus status = svc.checkAll();

        assertThat(status.isS3Available()).isTrue();
        assertThat(status.isSqsAvailable()).isTrue();
        assertThat(status.isElasticsearchAvailable()).isTrue();
        assertThat(status.isTemporalAvailable()).isFalse();
        assertThat(status.isAllHealthy()).isFalse();
    }

    @Test
    void isAllHealthyRequiresAllFourFlags() {
        ServiceHealthStatus allUp = ServiceHealthStatus.builder()
                .s3Available(true)
                .sqsAvailable(true)
                .elasticsearchAvailable(true)
                .temporalAvailable(true)
                .build();
        assertThat(allUp.isAllHealthy()).isTrue();

        ServiceHealthStatus oneDown = ServiceHealthStatus.builder()
                .s3Available(true)
                .sqsAvailable(true)
                .elasticsearchAvailable(true)
                .temporalAvailable(false)
                .build();
        assertThat(oneDown.isAllHealthy()).isFalse();
    }
}
