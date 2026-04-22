#!/bin/bash
echo "Initializing LocalStack S3 bucket and SQS queue..."

awslocal s3api create-bucket --bucket legal-documents --region us-east-1
awslocal sqs create-queue --queue-name legal-doc-processing-queue --region us-east-1

echo "LocalStack initialization complete."
