package com.example.legaldoc.workflow;

import com.example.legaldoc.config.TemporalConfig;
import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.PageRef;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates document processing across two task queues:
 * <ul>
 *   <li>The main queue ({@link TemporalConfig#TASK_QUEUE}) for planning,
 *       manifest finalization, and Elasticsearch indexing.</li>
 *   <li>The page queue ({@link TemporalConfig#PAGE_TASK_QUEUE}) for CPU-heavy
 *       per-page extraction that fans out with {@code Async.function}.</li>
 * </ul>
 */
public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {

    private static final Logger log = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);

    private final DocumentActivities activities = Workflow.newActivityStub(
            DocumentActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TemporalConfig.TASK_QUEUE)
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setMaximumInterval(Duration.ofSeconds(30))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    /**
     * Page activities run on a separate task queue so a dedicated page worker
     * can scale independently of the orchestration worker. Per-page timeout is
     * shorter than the orchestration-level timeout because a single page is
     * cheap; a single hung page shouldn't block all retries.
     */
    private final PageActivities pageActivities = Workflow.newActivityStub(
            PageActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TemporalConfig.PAGE_TASK_QUEUE)
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(20))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public DocumentMetadata processDocument(SqsDocumentMessage message) {
        log.info("Starting document processing workflow for documentId={}", message.getDocumentId());

        // Step 1: Plan — normalize source, count pages, stage tmp prefix.
        PlanResult plan = activities.planDocument(message);
        log.info("Plan: documentId={} pages={} contentType={} tmpPrefix={}",
                plan.getDocumentId(), plan.getTotalPages(),
                plan.getSourceContentType(), plan.getTmpPrefix());

        // Step 2: Fan out page extraction. Each activity runs on the page worker,
        // with Temporal load-balancing them across whatever page workers are up.
        List<Promise<PageRef>> pagePromises = new ArrayList<>();
        for (int i = 1; i <= plan.getTotalPages(); i++) {
            final int pageNumber = i;
            pagePromises.add(Async.function(pageActivities::extractAndStorePage, plan, pageNumber));
        }
        Promise.allOf(pagePromises).get();

        List<PageRef> pageRefs = new ArrayList<>();
        for (Promise<PageRef> p : pagePromises) {
            pageRefs.add(p.get());
        }
        // Pages may complete out of order; sort for a deterministic manifest.
        pageRefs.sort(Comparator.comparingInt(PageRef::getPageNumber));

        // Step 3: Finalize the manifest in S3.
        DocumentPageManifest manifest = DocumentPageManifest.builder()
                .documentId(plan.getDocumentId())
                .fileName(plan.getFileName())
                .sourceContentType(plan.getSourceContentType())
                .tmpPrefix(plan.getTmpPrefix())
                .totalPages(pageRefs.size())
                .syntheticPagination(plan.isSyntheticPagination())
                .pages(pageRefs)
                .createdAt(Instant.ofEpochMilli(Workflow.currentTimeMillis()))
                .build();
        String manifestKey = activities.finalizeManifest(manifest);
        log.info("Manifest stored at {}", manifestKey);

        // Step 4: Reassemble text, compute TF-IDF, index to Elasticsearch.
        DocumentMetadata result = activities.indexDocument(manifest, message);
        log.info("Workflow complete: documentId={} status={}",
                result.getDocumentId(), result.getStatus());
        return result;
    }
}
