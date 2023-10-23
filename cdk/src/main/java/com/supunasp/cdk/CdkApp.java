package com.supunasp.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        String repoName = "cdk-java11-lambda";
        Environment environment = makeEnv();
        new CodePipelineStack(
                app,
                repoName + "-codepipeline-stack",
                StackProps
                        .builder()
                        .stackName(repoName + "-codepipeline-stack")
                        .env(environment)
                        .build(),
                repoName
        );

        app.synth();
    }

    public static Environment makeEnv() {
        return Environment.builder()
                .account("XXXX")
                .region("ap-southeast-2")
                .build();
    }

}

