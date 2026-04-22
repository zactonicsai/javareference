package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DocumentActivities {

    @ActivityMethod
    DocumentMetadata extractTextAndMetadata(SqsDocumentMessage message);

    @ActivityMethod
    DocumentMetadata computeKeywords(DocumentMetadata document);

    @ActivityMethod
    String indexToElasticsearch(DocumentMetadata document);
}
