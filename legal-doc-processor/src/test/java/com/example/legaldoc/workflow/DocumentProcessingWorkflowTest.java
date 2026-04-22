package com.example.legaldoc.workflow;

import com.example.legaldoc.config.TemporalConfig;
import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.PageRef;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link DocumentProcessingWorkflowImpl} in Temporal's in-memory
 * test environment. Activities on both queues are mocked, so the test pins
 * down only the workflow's orchestration behavior (fan-out, ordering,
 * manifest finalization, index call).
 */
class DocumentProcessingWorkflowTest {

    private TestWorkflowEnvironment env;
    private Worker orchestrationWorker;
    private Worker pageWorker;
    private WorkflowClient client;

    private DocumentActivities mainActivities;
    private PageActivities pageActivities;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();

        orchestrationWorker = env.newWorker(TemporalConfig.TASK_QUEUE);
        orchestrationWorker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        mainActivities = mock(DocumentActivities.class);
        orchestrationWorker.registerActivitiesImplementations(mainActivities);

        pageWorker = env.newWorker(TemporalConfig.PAGE_TASK_QUEUE);
        pageActivities = mock(PageActivities.class);
        pageWorker.registerActivitiesImplementations(pageActivities);

        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void fansOutOnePageActivityPerPageAndBuildsManifest() {
        // Plan: 3 pages, text content type, synthetic pagination.
        PlanResult plan = PlanResult.builder()
                .documentId("doc-xyz")
                .fileName("case.txt")
                .sourceContentType("text/plain")
                .extractionSourceS3Key("doc-xyz/case.txt")
                .totalPages(3)
                .syntheticPagination(true)
                .tmpPrefix("tmp/doc-xyz/case.txt/")
                .build();
        when(mainActivities.planDocument(any(SqsDocumentMessage.class))).thenReturn(plan);

        // Page activity returns a ref for whichever page it's called with.
        when(pageActivities.extractAndStorePage(any(PlanResult.class), anyInt()))
                .thenAnswer(inv -> {
                    int n = inv.getArgument(1);
                    return PageRef.builder()
                            .pageNumber(n)
                            .textS3Key("tmp/doc-xyz/case.txt/pages/page-000" + n + ".txt")
                            .charCount(100)
                            .imageS3Keys(new ArrayList<>())
                            .build();
                });

        when(mainActivities.finalizeManifest(any(DocumentPageManifest.class)))
                .thenReturn("tmp/doc-xyz/case.txt/manifest.json");

        when(mainActivities.indexDocument(any(DocumentPageManifest.class), any(SqsDocumentMessage.class)))
                .thenAnswer(inv -> {
                    DocumentPageManifest m = inv.getArgument(0);
                    return DocumentMetadata.builder()
                            .documentId(m.getDocumentId())
                            .fileName(m.getFileName())
                            .pageCount(m.getTotalPages())
                            .status("INDEXED")
                            .build();
                });

        DocumentProcessingWorkflow workflow = client.newWorkflowStub(
                DocumentProcessingWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TemporalConfig.TASK_QUEUE).build());

        SqsDocumentMessage msg = SqsDocumentMessage.builder()
                .documentId("doc-xyz")
                .fileName("case.txt")
                .s3Key("doc-xyz/case.txt")
                .subject("Case XYZ")
                .build();

        DocumentMetadata result = workflow.processDocument(msg);

        assertThat(result.getDocumentId()).isEqualTo("doc-xyz");
        assertThat(result.getPageCount()).isEqualTo(3);
        assertThat(result.getStatus()).isEqualTo("INDEXED");

        // Page activity should have been called exactly once per page.
        verify(pageActivities).extractAndStorePage(any(PlanResult.class), org.mockito.ArgumentMatchers.eq(1));
        verify(pageActivities).extractAndStorePage(any(PlanResult.class), org.mockito.ArgumentMatchers.eq(2));
        verify(pageActivities).extractAndStorePage(any(PlanResult.class), org.mockito.ArgumentMatchers.eq(3));
        verify(mainActivities).finalizeManifest(any(DocumentPageManifest.class));
        verify(mainActivities).indexDocument(any(DocumentPageManifest.class), any(SqsDocumentMessage.class));
    }

    @Test
    void singlePagePlanCallsPageActivityExactlyOnce() {
        when(mainActivities.planDocument(any(SqsDocumentMessage.class)))
                .thenReturn(PlanResult.builder()
                        .documentId("doc-1")
                        .fileName("a.txt")
                        .sourceContentType("text/plain")
                        .extractionSourceS3Key("doc-1/a.txt")
                        .totalPages(1)
                        .syntheticPagination(true)
                        .tmpPrefix("tmp/doc-1/a.txt/")
                        .build());

        when(pageActivities.extractAndStorePage(any(PlanResult.class), anyInt()))
                .thenReturn(PageRef.builder()
                        .pageNumber(1)
                        .textS3Key("tmp/doc-1/a.txt/pages/page-0001.txt")
                        .charCount(10)
                        .imageS3Keys(List.of())
                        .build());

        when(mainActivities.finalizeManifest(any(DocumentPageManifest.class)))
                .thenReturn("tmp/doc-1/a.txt/manifest.json");

        when(mainActivities.indexDocument(any(DocumentPageManifest.class), any(SqsDocumentMessage.class)))
                .thenReturn(DocumentMetadata.builder()
                        .documentId("doc-1").status("INDEXED").build());

        DocumentProcessingWorkflow workflow = client.newWorkflowStub(
                DocumentProcessingWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TemporalConfig.TASK_QUEUE).build());

        DocumentMetadata result = workflow.processDocument(SqsDocumentMessage.builder()
                .documentId("doc-1").fileName("a.txt").s3Key("doc-1/a.txt").subject("x").build());

        assertThat(result.getStatus()).isEqualTo("INDEXED");
        verify(pageActivities).extractAndStorePage(any(PlanResult.class), org.mockito.ArgumentMatchers.eq(1));
    }
}
