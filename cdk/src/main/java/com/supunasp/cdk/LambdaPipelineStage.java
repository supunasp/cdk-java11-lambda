package com.supunasp.cdk;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

@Slf4j
public class LambdaPipelineStage extends Stage {
    public LambdaPipelineStage(final Construct scope,
                               final String id,
                               final StageProps props,
                               final String functionName,
                               final String environment
    ) {
        super(scope, id, props);

        LambdaStack stack = new LambdaStack(
                this,
                functionName,
                StackProps
                        .builder()
                        .stackName(functionName)
                        .env(props.getEnv())
                        .build(),
                functionName,
                environment
        );
        log.info("Stack for {} is {}", environment, stack.getStackName());

    }
}
