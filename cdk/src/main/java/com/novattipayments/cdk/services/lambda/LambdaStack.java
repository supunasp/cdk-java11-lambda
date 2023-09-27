package com.novattipayments.cdk.services.lambda;

import com.novattipayments.cdk.Constants;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Version;
import software.amazon.awscdk.services.lambda.VersionProps;
import software.constructs.Construct;

public class LambdaStack extends Stack {
    public LambdaStack(final Construct scope, final String id) {
        this(scope, id, null, Constants.DEFAULT_ENV);
    }

    public LambdaStack(final Construct scope, final String id, final StackProps props, String env) {
        super(scope, id, props);

        initStack(env);
    }

    private void initStack(String env) {

        String repoName = "cdk-java11-lambda";

        String functionName = "cdk-pipeline-lambda";
        String pathToResource = "../lambda/target/lambda-0.1.jar";
        if (Constants.DEVELOPMENT_ENV.equals(env)) {
            functionName = env + "-" + repoName;
        }

        IRole lambdaRole = Role.fromRoleArn(
                this,
                "lambda-role",
                "arn:aws:iam::306171637169:role/lambda-role"
        );
        IFunction lambdaFunction = Function.Builder.create(this, functionName)
                .functionName(functionName)
                .runtime(Runtime.JAVA_11)
                .code(Code.fromAsset(pathToResource))
                .handler("com.novattipayments.cdk.MyLambdaHandler")
                .role(lambdaRole)
                .build();


        // Create a Lambda version using the Maven version
        Version lambdaVersion = new Version(this,
                repoName + "version",
                VersionProps.builder()
                        .lambda(lambdaFunction)
                        .description("1.0.0-SNAPSHOT")
                        .build());

    }
}

