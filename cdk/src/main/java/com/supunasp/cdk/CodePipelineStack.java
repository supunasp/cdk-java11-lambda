package com.supunasp.cdk;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.CfnResource;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.StageProps;
import software.amazon.awscdk.pipelines.CodeBuildOptions;
import software.amazon.awscdk.pipelines.CodeBuildStep;
import software.amazon.awscdk.pipelines.CodeBuildStepProps;
import software.amazon.awscdk.pipelines.CodeCommitSourceOptions;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineProps;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.services.codebuild.BucketCacheOptions;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariableType;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.Cache;
import software.amazon.awscdk.services.codebuild.ComputeType;
import software.amazon.awscdk.services.codebuild.IProject;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codecommit.IRepository;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitTrigger;
import software.amazon.awscdk.services.iam.FromRoleArnOptions;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAttributes;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;


@Slf4j
public class CodePipelineStack extends Stack {

    public CodePipelineStack(final Construct scope,
                             final String id,
                             final StackProps props,
                             final String repoName) {
        super(scope, id, props);

        initStack(repoName, props.getEnv());
    }

    private void initStack(final String repoName, final Environment env) {

        IRepository codeCommitRepository = getRepository(this, repoName);
        BuildEnvironment buildEnvironment = getBuildEnvironment();
        IRole codeBuildRole = getCodeBuildDeveloperRole(this);
        IRole codeBuildActionRole = getCodeBuildActionRole(this);
        IRole codePipelineRole = getCodePipelineDeveloperRole(this);
        IRole codePipelineEventRole = getCodePipelineEventsRole(this);
        Cache codebuildCache = getCacheBucket(this, repoName);

        PolicyStatement policyStatement = getPolicyStatement();

        //synth action
        String codeBuildName = "cdk-" + repoName + "-codebuild";
        CodeBuildStep synthStep = new CodeBuildStep(
                codeBuildName,
                CodeBuildStepProps
                        .builder()
                        .projectName(codeBuildName)
                        .cache(codebuildCache)
                        .input(
                                CodePipelineSource.codeCommit(
                                        codeCommitRepository,
                                        "master",
                                        CodeCommitSourceOptions
                                                .builder()
                                                .eventRole(codePipelineEventRole)
                                                .actionName("commit")
                                                .codeBuildCloneOutput(Boolean.TRUE)
                                                .trigger(CodeCommitTrigger.NONE)
                                                .build()
                                )
                        )
                        .partialBuildSpec(getCacheBuildSpec())
                        .installCommands(getInstallCommands())
                        .commands(getBuildCommands())
                        .primaryOutputDirectory("${CODEBUILD_SRC_DIR}/cdk/cdk.out")
                        .buildEnvironment(buildEnvironment)
                        .actionRole(codeBuildActionRole)
                        .role(codeBuildRole)
                        .rolePolicyStatements(List.of(policyStatement))
                        .build()
        );
        // Create the pipeline
        String codePipelineId = "cdk-codepipeline-" + repoName;
        CodePipeline codePipeline = new CodePipeline(
                this,
                codePipelineId,
                CodePipelineProps.builder()
                        .pipelineName(codePipelineId)
                        .selfMutation(Boolean.TRUE)
                        .role(codePipelineRole)
                        .synth(synthStep)
                        .crossAccountKeys(Boolean.TRUE)
                        .artifactBucket(getArtifactBucket(this))
                        .synthCodeBuildDefaults(
                                getCodeBuildDefaults(codebuildCache, policyStatement,
                                        buildEnvironment)
                        )
                        .codeBuildDefaults(
                                getCodeBuildDefaults(codebuildCache, policyStatement,
                                        buildEnvironment)
                        )
                        .selfMutationCodeBuildDefaults(
                                getCodeBuildDefaults(codebuildCache, policyStatement,
                                        buildEnvironment)
                        )
                        .assetPublishingCodeBuildDefaults(
                                getCodeBuildDefaults(codebuildCache, policyStatement,
                                        buildEnvironment)
                        )
                        .build()
        );

        // DEV deploy
        String devFunctionName = "dev-" + repoName;

        codePipeline.addStage(
                new LambdaPipelineStage(
                        this,
                        repoName + "-dev-deploy",
                        StageProps
                                .builder()
                                .stageName(repoName + "-dev-deploy")
                                .env(env)
                                .build(),
                        devFunctionName,
                        Constants.DEVELOPMENT_ENV

                )
        );

        // Test deploy
        codePipeline.addStage(
                new LambdaPipelineStage(
                        this,
                        repoName + "-test-deploy",
                        StageProps
                                .builder()
                                .stageName(repoName + "-test-deploy")
                                .env(env)
                                .build(),
                        repoName,
                        Constants.TEST_ENV

                )
        );

        codePipeline.buildPipeline();

        clearAutoGeneratedRoles(codePipeline);


    }

    @NotNull
    private static List<String> getInstallCommands() {
        return List.of(
                "export CODEARTIFACT_AUTH_TOKEN=`aws codeartifact get-authorization-token "
                        + "--domain XXXX --query authorizationToken --output text`",
                "npm install -g aws-cdk"
        );
    }

    @NotNull
    private static List<String> getBuildCommands() {
        return List.of(
                "echo Build started on `date`",
                "git checkout master",
                //release prepare
                "sh ./release.sh"
        );
    }

    private void clearAutoGeneratedRoles(final CodePipeline codePipeline) {
        CfnResource pipelineCfn = getDefaultChild(codePipeline.getPipeline());

        overrideStageRoles(pipelineCfn);

        CfnResource selfMutationProjectCfn = getDefaultChild(codePipeline.getSelfMutationProject());

        if (selfMutationProjectCfn != null) {
            // replace service role for AWS:${cdk-codepipeline-XX/UpdatePipeline/SelfMutation/Role}
            selfMutationProjectCfn.addPropertyOverride(
                    "ServiceRole",
                    "arn:aws:iam::XXXXXXX:role/CodePipelineSelfMutationRole"
            );
        }
    }

    private static void overrideStageRoles(final CfnResource pipelineCfn) {
        if (pipelineCfn != null) {
            // AWS:${cdk-codepipeline-XX/Pipeline/Source/source-change/CodePipelineActionRole}
            pipelineCfn.addPropertyOverride(
                    "Stages.0.Actions.0.RoleArn",
                    "arn:aws:iam::XXXXXXX:role/CodePipelineActionRole"
            );
            // AWS:${cdk-codepipeline-XX/UpdatePipeline/SelfMutation/Role}
            pipelineCfn.addPropertyOverride(
                    "Stages.2.Actions.0.RoleArn",
                    "arn:aws:iam::XXXXXXX:role/CodePipelineSelfMutationRole"
            );
            //AWS:${cdk-codepipeline-XX/Assets/FileRole}
            pipelineCfn.addPropertyOverride(
                    "Stages.3.Actions.0.RoleArn",
                    "arn:aws:iam::XXXXXXX:role/CodePipelineAssetsFileRole"
            );
        }
    }

    public static BuildEnvironmentVariable getPlainTextEnvVariable(final String value) {
        return BuildEnvironmentVariable
                .builder()
                .value(value)
                .type(BuildEnvironmentVariableType.PLAINTEXT)
                .build();
    }

    public static BuildEnvironmentVariable getSecretEnvVariable(final String value) {
        return BuildEnvironmentVariable
                .builder()
                .value(value)
                .type(BuildEnvironmentVariableType.SECRETS_MANAGER)
                .build();
    }

    public static BuildEnvironment getBuildEnvironment() {

        return BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                .computeType(ComputeType.MEDIUM)
                .privileged(Boolean.TRUE)
                .environmentVariables(getCodeBuildUserDetails())
                .build();
    }

    /**
     * tree map is required to maintain order. otherwise pipeline will go on loop
     *
     * @return Map of env variables
     */
    @NotNull
    private static Map<String, BuildEnvironmentVariable> getCodeBuildUserDetails() {

        Map<String, BuildEnvironmentVariable> variableMap = new TreeMap<>();
        variableMap.put(
                "AWS_ACCESS_KEY_ID",
                getSecretEnvVariable(
                        "arn:aws:secretsmanager:ap-southeast-2:XXXXXXX:secret:XXXXXXX::")
        );
        variableMap.put(
                "AWS_SECRET_ACCESS_KEY",
                getSecretEnvVariable(
                        "arn:aws:secretsmanager:ap-southeast-2:XXXXXXX:secret:XXXXXXX::")
        );
        variableMap.put(
                "AWS_DEFAULT_REGION",
                getPlainTextEnvVariable("ap-southeast-2")
        );
        return variableMap;
    }

    @NotNull
    public static FromRoleArnOptions getDefaultRoleArnOptions() {
        return FromRoleArnOptions
                .builder()
                .mutable(Boolean.FALSE)
                .build();
    }

    @NotNull
    public static IRepository getRepository(final Construct scope, final String repoName) {
        return Repository.fromRepositoryName(
                scope, repoName, repoName);
    }

    @NotNull
    public static Cache getCacheBucket(final Construct scope, final String repoName) {
        return Cache.bucket(
                Bucket.fromBucketName(
                        scope,
                        "build-cache",
                        "code-build-cache"
                ),
                BucketCacheOptions
                        .builder()
                        .prefix(repoName)
                        .build()
        );
    }

    public static IBucket getArtifactBucket(final Construct scope) {
        return Bucket.fromBucketAttributes(
                scope,
                "artifact-bucket",
                BucketAttributes
                        .builder()
                        .bucketName("cdk-pipeline-artifacts")
                        .encryptionKey(getBucketEncryptionKey(scope))
                        .build()
        );
    }

    public static IKey getBucketEncryptionKey(final Construct scope) {

        return Key.fromKeyArn(
                scope,
                "artifact-bucket-key",
                "arn:aws:kms:ap-southeast-2:XXXXXXX:key/XXXX"
        );
    }

    @NotNull
    public static BuildSpec getCacheBuildSpec() {
        return BuildSpec.fromObject(
                Map.of(
                        "cache",
                        Map.of(
                                "paths", List.of("/root/.m2/**/*")
                        )
                )
        );
    }

    public static PolicyStatement getPolicyStatement() {
        return PolicyStatement.Builder
                .create()
                .actions(List.of("sts:AssumeRole"))
                .resources(List.of("arn:aws:iam::XXXXXXX:role/cdk-*"))
                .build();
    }

    @NotNull
    public static IRole getCodeBuildDeveloperRole(final Construct scope) {
        return Role.fromRoleArn(
                scope,
                "CodeBuildDeveloperRole",
                "arn:aws:iam::XXXXXXX:role/CodeBuildDeveloperRole",
                getDefaultRoleArnOptions()
        );
    }

    @NotNull
    public static IRole getCodeBuildActionRole(final Construct scope) {
        return Role.fromRoleArn(
                scope,
                "CodeBuildActionRole",
                "arn:aws:iam::XXXXXXX:role/CodeBuildActionRole",
                getDefaultRoleArnOptions()
        );
    }

    @NotNull
    public static IRole getCodePipelineDeveloperRole(final Construct scope) {
        return Role.fromRoleArn(
                scope,
                "CodePipelineDeveloperRole",
                "arn:aws:iam::XXXXXXX:role/CodePipelineDeveloperRole",
                getDefaultRoleArnOptions()
        );
    }

    @NotNull
    public static IRole getCodePipelineEventsRole(final Construct scope) {
        return Role.fromRoleArn(
                scope,
                "CodePipelineEventsRole",
                "arn:aws:iam::XXXXXXX:role/CodePipelineEventsRole",
                getDefaultRoleArnOptions()
        );
    }

    @NotNull
    public static CodeBuildOptions getCodeBuildDefaults(final Cache codebuildCache,
                                                        final PolicyStatement policyStatement,
                                                        final BuildEnvironment buildEnvironment) {
        return CodeBuildOptions
                .builder()
                .cache(codebuildCache)
                .rolePolicy(List.of(policyStatement))
                .buildEnvironment(buildEnvironment)
                .build();
    }

    @Nullable
    public static CfnResource getDefaultChild(final Construct construct) {
        return (CfnResource) construct.getNode()
                .getDefaultChild();
    }

    @Nullable
    public static CfnResource getDefaultChild(final IProject project) {
        return (CfnResource) project.getNode()
                .getDefaultChild();
    }
}
