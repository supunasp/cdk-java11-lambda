package com.novattipayments.cdk;

import com.novattipayments.cdk.services.appconfig.CdkAppConfigStack;
import com.novattipayments.cdk.services.lambda.LambdaStack;
import com.novattipayments.cdk.services.pipeline.PipelineStack;
import com.novattipayments.cdk.services.sns.SnsStack;
import com.novattipayments.cdk.services.sqs.SQSStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        String env = Constants.DEFAULT_ENV;

        LambdaStack stack = new LambdaStack(
                app,
                "cdk-pipeline-lambda",
                StackProps
                        .builder()
                        .stackName("cdk-pipeline-lambda")
                        .build(),
                env
        );
        new CdkAppConfigStack(app, "Base-AppConfig", env);
        new SnsStack(app, "sns-stack", env);
        new SQSStack(app, "sqs-stack", env);
        new PipelineStack(
                app,
                "poc-cdk-stack-pipeline",
                StackProps
                        .builder()
                        .stackName("poc-cdk-stack-pipeline")
                        .build(),
                env
        );

        app.synth();
    }
}

