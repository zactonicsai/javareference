package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {

    private static final Logger log = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);

    private final DocumentActivities activities = Workflow.newActivityStub(
            DocumentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setMaximumInterval(Duration.ofSeconds(30))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public DocumentMetadata processDocument(SqsDocumentMessage message) {
        log.info("Starting document processing workflow for documentId={}", message.getDocumentId());

        // Step 1: Pull file from S3 and extract text + metadata
        DocumentMetadata metadata = activities.extractTextAndMetadata(message);

        // Step 2: Compute TF-IDF keyword scores
        metadata = activities.computeKeywords(metadata);

        // Step 3: Index into Elasticsearch
        String indexedId = activities.indexToElasticsearch(metadata);
        log.info("Workflow complete. Indexed documentId={}", indexedId);

        metadata.setStatus("INDEXED");
        return metadata;
    }
}
