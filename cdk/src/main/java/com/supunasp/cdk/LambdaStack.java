package com.supunasp.cdk;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.FromRoleArnOptions;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Version;
import software.amazon.awscdk.services.lambda.VersionProps;
import software.constructs.Construct;

@Slf4j
public class LambdaStack extends Stack {

    public LambdaStack(final Construct scope,
                       final String id,
                       final StackProps props,
                       final String functionName,
                       final String environment) {
        super(scope, id, props);

        initStack(functionName, environment);
    }

    private void initStack(final String functionName,
                           final String environment) {

        String artifactId = getValueFromContext(this, Constants.ARTIFACT_ID);
        String snapShotVersion = getValueFromContext(this, Constants.SNAPSHOT_VERSION);
        String releaseVersion = getValueFromContext(this, Constants.RELEASE_VERSION);

        String handler = "com.XXXXXXX.cdk.CdkLambdaHandler::handleRequest";

        Code code = Code.fromAsset(
                "../lambda/target/" + artifactId + "-" + releaseVersion + ".zip"
        );

        IRole lambdaRole = getLambdaRole();
        IFunction lambdaFunction = Function.Builder.create(this, functionName)
                .functionName(functionName)
                .runtime(Runtime.JAVA_11)
                .code(code)
                .handler(handler)
                .role(lambdaRole)
                .build();

        log.info("Lambda function for {} is {}", environment, lambdaFunction.getFunctionName());
        // Create a Lambda version using the Maven version
        String version;
        if (Constants.DEVELOPMENT_ENV.equals(environment)) {
            version = snapShotVersion;
        } else {
            version = releaseVersion;
        }
        Version lambdaVersion = new Version(
                this,
                functionName + "-" + version,
                VersionProps.builder()
                        .lambda(lambdaFunction)
                        .description(version)
                        .removalPolicy(RemovalPolicy.RETAIN)
                        .build()
        );

        log.info("Lambda function version for {} in {} is {}",
                lambdaFunction.getFunctionName(), environment, lambdaVersion.getVersion());

    }

    @NotNull
    private IRole getLambdaRole() {
        return Role.fromRoleArn(
                this,
                "lambda-role",
                "arn:aws:iam::XXXXXXX:role/lambda-role",
                FromRoleArnOptions
                        .builder()
                        .mutable(Boolean.FALSE)
                        .build()
        );
    }

    @NotNull
    public static String getValueFromContext(final Construct scope, final String variableKey) {
        return (String) scope.getNode().tryGetContext(variableKey);
    }
}
