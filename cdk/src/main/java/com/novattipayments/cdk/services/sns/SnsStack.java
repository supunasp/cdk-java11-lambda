package com.novattipayments.cdk.services.sns;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.sns.CfnSubscription;
import software.amazon.awscdk.services.sns.CfnSubscriptionProps;
import software.amazon.awscdk.services.sns.CfnTopic;
import software.amazon.awscdk.services.sns.Topic;
import software.constructs.Construct;

import java.util.List;

public class SnsStack extends Stack {
    public SnsStack(@Nullable Construct scope, @Nullable String id, String env) {
        super(scope, id);
        initSnsStack(env);
    }

    private void initSnsStack(String env) {

        // Create an SNS topic
        Topic snsTopic = Topic.Builder.create(this, "MySNSTopic")
                .displayName("My SNS Topic")
                .build();

        // Create an email subscription
        CfnSubscription emailSubscription = new CfnSubscription(this, "EmailSubscription", CfnSubscriptionProps.builder()
                .protocol("email")
                .topicArn(snsTopic.getTopicArn())
                .endpoint("youremail@example.com") // Replace with your email address
                .build());

        // Add permissions to the topic to allow email deliveries
        PolicyStatement emailPermissions = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(List.of(snsTopic.getTopicArn()))
                .actions(List.of("SNS:Publish"))
                .principals(List.of(new ServicePrincipal("ses.amazonaws.com"))) // AWS SES service principal
                .build());

    }

}
