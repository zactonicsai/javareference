package com.example.legaldoc.service;

import com.example.legaldoc.model.SqsDocumentMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queueName}")
    private String queueName;

    private String queueUrl;

    @PostConstruct
    public void ensureQueueExists() {
        try {
            GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build());
            this.queueUrl = response.queueUrl();
            log.info("SQS queue '{}' found at: {}", queueName, queueUrl);
        } catch (QueueDoesNotExistException e) {
            log.info("Creating SQS queue '{}'", queueName);
            CreateQueueResponse createResponse = sqsClient.createQueue(
                    CreateQueueRequest.builder().queueName(queueName).build());
            this.queueUrl = createResponse.queueUrl();
        } catch (Exception e) {
            log.warn("SQS queue check failed at startup: {}", e.getMessage());
        }
    }

    public String sendMessage(SqsDocumentMessage message) throws JsonProcessingException {
        String messageBody = objectMapper.writeValueAsString(message);
        log.info("Sending SQS message: documentId={}", message.getDocumentId());

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        SendMessageResponse response = sqsClient.sendMessage(request);
        log.info("SQS message sent: messageId={}", response.messageId());
        return response.messageId();
    }

    public boolean isAvailable() {
        try {
            sqsClient.listQueues(ListQueuesRequest.builder().maxResults(1).build());
            return true;
        } catch (Exception e) {
            log.debug("SQS unavailable: {}", e.getMessage());
            return false;
        }
    }

    public String getQueueUrl() {
        return queueUrl;
    }
}
