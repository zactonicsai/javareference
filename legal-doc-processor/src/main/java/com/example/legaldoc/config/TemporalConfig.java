package com.example.legaldoc.config;

import com.example.legaldoc.workflow.DocumentActivitiesImpl;
import com.example.legaldoc.workflow.DocumentProcessingWorkflowImpl;
import com.example.legaldoc.workflow.PageActivitiesImpl;
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

/**
 * Wires two Temporal workers:
 *
 * <ul>
 *   <li>{@link #TASK_QUEUE} ({@code legal-doc-task-queue}) — runs the workflow
 *       itself and the orchestration activities (plan / finalize manifest /
 *       index to Elasticsearch). One worker, not CPU-heavy.</li>
 *   <li>{@link #PAGE_TASK_QUEUE} ({@code legal-doc-page-queue}) — runs the
 *       per-page extraction activity. Deployed as a second worker so page
 *       processing can scale independently of orchestration throughput.</li>
 * </ul>
 *
 * Both workers share the same {@link WorkerFactory} in-process for simplicity;
 * in production each queue would typically run in its own JVM/pod.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class TemporalConfig {

    public static final String TASK_QUEUE = "legal-doc-task-queue";
    public static final String PAGE_TASK_QUEUE = "legal-doc-page-queue";

    @Value("${temporal.host}")
    private String temporalHost;

    @Value("${temporal.namespace}")
    private String namespace;

    private final DocumentActivitiesImpl documentActivities;
    private final PageActivitiesImpl pageActivities;

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

    /**
     * Orchestration worker: hosts the workflow implementation and the main
     * activities. Only this worker polls {@link #TASK_QUEUE}.
     */
    @Bean
    public Worker orchestrationWorker(WorkerFactory workerFactory) {
        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        worker.registerActivitiesImplementations(documentActivities);
        log.info("Registered orchestration worker for task queue: {}", TASK_QUEUE);
        return worker;
    }

    /**
     * Page worker: hosts ONLY the page-extraction activity. Starting
     * {@link WorkerFactory} is done after both workers are registered so they
     * both come up together.
     */
    @Bean
    public Worker pageWorker(WorkerFactory workerFactory) {
        Worker worker = workerFactory.newWorker(PAGE_TASK_QUEUE);
        worker.registerActivitiesImplementations(pageActivities);
        // Starting here ensures both workers have been registered by the time
        // factory.start() runs; Spring constructs @Bean methods in declaration order
        // within a single @Configuration, so orchestrationWorker is guaranteed first.
        workerFactory.start();
        log.info("Registered page worker for task queue: {} (both workers started)", PAGE_TASK_QUEUE);
        return worker;
    }
}
