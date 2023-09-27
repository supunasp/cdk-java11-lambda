package com.novattipayments.cdk.services.sqs;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.sqs.CfnQueue;
import software.constructs.Construct;

@Slf4j
public class SQSStack extends Stack {
    public SQSStack(@Nullable Construct scope, @Nullable String id, String env) {
        super(scope, id);
        initSqsStack(env);
    }

    private void initSqsStack(String env) {

        CfnQueue queue = CfnQueue.Builder.create(this, "MyQueue")
                .queueName("MyQueueName")
                .build();

    }
}
