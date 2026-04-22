package com.example.legaldoc.workflow;

import com.example.legaldoc.model.PageRef;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * CPU-heavy per-page activities. Runs on a dedicated worker bound to
 * {@code legal-doc-page-queue} so page extraction scales independently of
 * orchestration throughput. The main workflow fans these out in parallel
 * using {@code Async.function} + {@code Promise.allOf}.
 */
@ActivityInterface
public interface PageActivities {

    /**
     * Extract a single page from the already-normalized source (stored at
     * {@code plan.extractionSourceS3Key}), upload its text and images under
     * {@code plan.tmpPrefix}, and return the resulting {@link PageRef}.
     */
    @ActivityMethod
    PageRef extractAndStorePage(PlanResult plan, int pageNumber);
}
