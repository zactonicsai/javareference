package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Orchestration-level activities that touch SQS-style metadata, write the
 * final manifest, compute keywords across all pages, and index into ES.
 *
 * These run on the main {@code legal-doc-task-queue} worker. Per-page
 * extraction (which is CPU-heavy) runs on a separate worker — see
 * {@link PageActivities}.
 */
@ActivityInterface
public interface DocumentActivities {

    /**
     * Download the source from S3, detect content type, compute the total page
     * count, and build an empty-but-sized manifest. Does NOT extract any pages;
     * that fans out through {@link PageActivities#extractAndStorePage}.
     */
    @ActivityMethod
    PlanResult planDocument(SqsDocumentMessage message);

    /**
     * After every page has been processed, finalize the manifest in S3 and
     * return the manifest S3 key.
     */
    @ActivityMethod
    String finalizeManifest(DocumentPageManifest manifest);

    /**
     * Build the {@link DocumentMetadata} by reading the per-page text files back
     * from S3 (via the manifest) and concatenating them. Then run TF-IDF and
     * index into Elasticsearch.
     */
    @ActivityMethod
    DocumentMetadata indexDocument(DocumentPageManifest manifest, SqsDocumentMessage message);
}
