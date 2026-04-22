package com.example.legaldoc.config;

import com.example.legaldoc.workflow.DocumentActivitiesImpl;
import com.example.legaldoc.workflow.DocumentProcessingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TemporalConfig {

    public static final String TASK_QUEUE = "legal-doc-task-queue";

    @Value("${temporal.host}")
    private String temporalHost;

    @Value("${temporal.namespace}")
    private String namespace;

    private final DocumentActivitiesImpl documentActivities;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalHost)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        return WorkerFactory.newInstance(workflowClient);
    }

    @Bean
    public Worker documentWorker(WorkerFactory workerFactory) {
        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        worker.registerActivitiesImplementations(documentActivities);
        workerFactory.start();
        log.info("Temporal worker started for task queue: {}", TASK_QUEUE);
        return worker;
    }
}
