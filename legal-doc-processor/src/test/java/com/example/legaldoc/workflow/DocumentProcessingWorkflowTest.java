package com.example.legaldoc.workflow;

import com.example.legaldoc.config.TemporalConfig;
import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.KeywordScore;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentProcessingWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension =
            TestWorkflowExtension.newBuilder()
                    .setWorkflowTypes(DocumentProcessingWorkflowImpl.class)
                    .setDoNotStart(true)
                    .build();

    @Test
    void workflowCompletesThroughAllActivities(
            TestWorkflowExtension.TestEnvironmentArgs args,
            Worker worker,
            WorkflowClient client) {

        DocumentActivities mockActivities = mock(DocumentActivities.class);
        DocumentMetadata extracted = DocumentMetadata.builder()
                .documentId("doc-1")
                .fileName("evidence.txt")
                .extractedText("homicide fraud")
                .status("EXTRACTED")
                .build();

        DocumentMetadata withKeywords = DocumentMetadata.builder()
                .documentId("doc-1")
                .fileName("evidence.txt")
                .extractedText("homicide fraud")
                .topKeywords(List.of(new KeywordScore("homicide", 0.5, 1)))
                .status("KEYWORDS_COMPUTED")
                .build();

        when(mockActivities.extractTextAndMetadata(any())).thenReturn(extracted);
        when(mockActivities.computeKeywords(any())).thenReturn(withKeywords);
        when(mockActivities.indexToElasticsearch(any())).thenReturn("doc-1");

        worker.registerActivitiesImplementations(mockActivities);
        args.getTestEnvironment().start();

        DocumentProcessingWorkflow workflow = client.newWorkflowStub(
                DocumentProcessingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .build());

        SqsDocumentMessage msg = SqsDocumentMessage.builder()
                .documentId("doc-1")
                .s3Key("doc-1/evidence.txt")
                .fileName("evidence.txt")
                .subject("Test case")
                .uploadDateTime(Instant.now().toString())
                .build();

        DocumentMetadata result = workflow.processDocument(msg);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("INDEXED");
        verify(mockActivities).extractTextAndMetadata(any());
        verify(mockActivities).computeKeywords(any());
        verify(mockActivities).indexToElasticsearch(any());
    }
}
