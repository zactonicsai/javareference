package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.SqsDocumentMessage;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentProcessingWorkflow {

    @WorkflowMethod
    DocumentMetadata processDocument(SqsDocumentMessage message);
}
