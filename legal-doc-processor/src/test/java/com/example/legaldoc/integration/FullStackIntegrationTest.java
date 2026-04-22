package com.example.legaldoc.integration;

import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Full-stack integration test that spins up LocalStack and Elasticsearch via Testcontainers.
 *
 * Disabled by default because (a) it needs a running Docker daemon, and (b) Temporal is not
 * started here -- wiring a full Temporal server inside Testcontainers is heavier than a unit
 * test should be. To run locally: remove the @Disabled and ensure Docker is available.
 *
 * Temporal-side behavior is covered by {@link com.example.legaldoc.workflow.DocumentProcessingWorkflowTest}
 * using the Temporal test environment.
 */
@Disabled("Enable locally with Docker available; see class javadoc.")
@SpringBootTest
@Testcontainers
class FullStackIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(S3, SQS);

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.accessKey", localstack::getAccessKey);
        registry.add("aws.secretKey", localstack::getSecretKey);
        registry.add("elasticsearch.host", elasticsearch::getHost);
        registry.add("elasticsearch.port",
                () -> elasticsearch.getMappedPort(9200).toString());
        // Point Temporal at a non-existent host; TemporalConfig beans will still construct,
        // but health checks will report Temporal as down for this suite.
        registry.add("temporal.host", () -> "localhost:1");
    }

    @Autowired private S3Service s3Service;
    @Autowired private SqsService sqsService;
    @Autowired private ElasticsearchService elasticsearchService;

    @Test
    void s3AndSqsAndEsAreReachableThroughServiceBeans() throws Exception {
        assertThat(s3Service.isAvailable()).isTrue();
        assertThat(sqsService.isAvailable()).isTrue();
        assertThat(elasticsearchService.isAvailable()).isTrue();

        // Upload something through the real S3 bean
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                "homicide evidence scene".getBytes());
        String key = s3Service.uploadFile("integration/test.txt", file);
        assertThat(key).isEqualTo("integration/test.txt");

        byte[] back = s3Service.downloadFileAsBytes(key);
        assertThat(new String(back)).contains("homicide");
    }
}
