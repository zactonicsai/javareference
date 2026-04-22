package com.example.legaldoc.service;

import com.example.legaldoc.config.TemporalConfig;
import com.example.legaldoc.model.SqsDocumentMessage;
import com.example.legaldoc.workflow.DocumentProcessingWorkflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsPollerService {

    private final SqsClient sqsClient;
    private final SqsService sqsService;
    private final ObjectMapper objectMapper;
    private final WorkflowClient workflowClient;

    @Value("${aws.sqs.queueName}")
    private String queueName;

    @Scheduled(fixedDelay = 5000)
    public void pollQueue() {
        String queueUrl = sqsService.getQueueUrl();
        if (queueUrl == null) return;

        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(1)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
            if (messages.isEmpty()) return;

            log.info("Received {} SQS messages", messages.size());
            for (Message msg : messages) {
                try {
                    SqsDocumentMessage docMsg = objectMapper.readValue(msg.body(), SqsDocumentMessage.class);
                    triggerWorkflow(docMsg);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(msg.receiptHandle())
                            .build());
                } catch (Exception e) {
                    log.error("Error processing SQS message: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.debug("SQS poll failed (expected when service is down): {}", e.getMessage());
        }
    }

    private void triggerWorkflow(SqsDocumentMessage msg) {
        String workflowId = "doc-workflow-" + msg.getDocumentId();
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .build();

        DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, options);

        log.info("Triggering workflow {} for documentId={}", workflowId, msg.getDocumentId());
        WorkflowClient.start(workflow::processDocument, msg);
    }
}
