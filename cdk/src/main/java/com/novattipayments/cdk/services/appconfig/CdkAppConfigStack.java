package com.novattipayments.cdk.services.appconfig;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.appconfig.CfnApplication;
import software.amazon.awscdk.services.appconfig.CfnConfigurationProfile;
import software.amazon.awscdk.services.appconfig.CfnDeployment;
import software.amazon.awscdk.services.appconfig.CfnDeploymentStrategy;
import software.amazon.awscdk.services.appconfig.CfnEnvironment;
import software.amazon.awscdk.services.appconfig.CfnHostedConfigurationVersion;
import software.constructs.Construct;

@Slf4j
public class CdkAppConfigStack extends Stack {
    public CdkAppConfigStack(final Construct parent, final String id, final String env) {
        super(parent, id, null);
        initStack(env);
    }

    public CdkAppConfigStack() {
    }

    private void initStack(final String env) {


        String applicationName = "test-lambda";
        CfnDeploymentStrategy deploymentStrategy = CfnDeploymentStrategy.Builder.create(this, "just-deploy-no-baking")
                .name("CDK-Just Deploy-No Baking")
                .deploymentDurationInMinutes(0)
                .growthFactor(100)
                .replicateTo("NONE")
                .finalBakeTimeInMinutes(0)
                .growthType("LINEAR")
                .build();

        String contentToBeDeployed = "---\n#This config was automatically deployed via AWS CDK. \n" +
                "#Environment : " + env + "\n";

        CfnApplication application = CfnApplication.Builder
                .create(this, "cdk-application-" + applicationName)
                .name("cdk-" + applicationName).build();

        CfnConfigurationProfile cfnConfigurationProfile = CfnConfigurationProfile.Builder
                .create(this, "cdk-app-configuration-" + applicationName)
                .name("app-configuration")
                .locationUri("hosted")
                .type("AWS.Freeform")
                .applicationId(application.getRef())
                .build();
        CfnHostedConfigurationVersion hostedConfigurationVersion = CfnHostedConfigurationVersion.Builder
                .create(this, "config-version-" + env + "-" + applicationName)
                .contentType("application/x-yaml")
                .applicationId(application.getRef())
                .configurationProfileId(cfnConfigurationProfile.getRef())
                .content(contentToBeDeployed)
                .build();

        CfnEnvironment environment = CfnEnvironment.Builder.create(this, "cdk-" + env + "-environment-" + applicationName)
                .name(env)
                .applicationId(application.getRef())
                .build();

        CfnDeployment.Builder.create(this, "cdk-" + env + "-app-config-deployment-" + applicationName)
                .applicationId(application.getRef())
                .configurationProfileId(cfnConfigurationProfile.getRef())
                .configurationVersion(hostedConfigurationVersion.getRef())
                .deploymentStrategyId(deploymentStrategy.getRef())
                .environmentId(environment.getRef())
                .build();


    }
}
