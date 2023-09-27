package com.novattipayments.cdk.services.pipeline;

import com.novattipayments.cdk.Constants;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.Cache;
import software.amazon.awscdk.services.codebuild.CodeCommitSourceProps;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.codecommit.IRepository;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildActionProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;

public class PipelineStack extends Stack {
    public PipelineStack(final Construct scope, final String id) {
        this(scope, id, null, Constants.DEFAULT_ENV);
    }

    public PipelineStack(final Construct scope, final String id, final StackProps props, String env) {
        super(scope, id, props);

        initStack(env);
    }

    private void initStack(String env) {

        String repoName = "cdk-java11-lambda";

        IRole codePipelineRole = getCodePipelineDeveloperRole();

        // Create a CodeCommit repository
        IRepository codeCommitRepository = getRepository(repoName);
        // Create a CodePipeline artifact
        Artifact sourceArtifact = new Artifact("SourceArtifact");
        CodeCommitSourceAction codeCommitSourceAction = CodeCommitSourceAction.Builder.create()
                .actionName("CodeCommit_Source")
                .repository(codeCommitRepository)
                .output(sourceArtifact)
                .branch("master")
                .role(codePipelineRole)
                .build();


        // Build Action (CodeBuild)
        IRole codeBuildRole = getCodeBuildDeveloperRole();
        Cache codebuildCache = getCacheBucket();
        Artifact buildOutput = new Artifact("BuildArtifact");
        CodeBuildAction codeBuildAction = new CodeBuildAction(CodeBuildActionProps.builder()
                .actionName("CodeBuild")
                .project(getBuildProject(
                        repoName,
                        codeCommitRepository,
                        codeBuildRole,
                        codebuildCache))
                .input(sourceArtifact)
                .outputs(Collections.singletonList(buildOutput))
                .role(codePipelineRole)
                .build());

        // Deploy Action (AWS Lambda)
        Artifact deployOutput = new Artifact("DeployArtifact");
        CodeBuildAction codeDeployAction = new CodeBuildAction(CodeBuildActionProps.builder()
                .actionName("CodeDeploy")
                .project(getDeployProject(
                        codeCommitRepository,
                        codeBuildRole,
                        codebuildCache
                ))
                .input(buildOutput)
                .outputs(Collections.singletonList(deployOutput))
                .role(codePipelineRole)
                .build());


        // Create the pipeline
        Pipeline pipeline = Pipeline.Builder.create(this, repoName + "-pipeline")
                .pipelineName(repoName + "-pipeline")
                .stages(Arrays.asList(
                                StageProps.builder()
                                        .stageName("Source")
                                        .actions(Collections.singletonList(codeCommitSourceAction))
                                        .build()
                                , StageProps.builder()
                                        .stageName("Build")
                                        .actions(Collections.singletonList(codeBuildAction))
                                        .build()
                                , StageProps.builder()
                                        .stageName("Deploy")
                                        .actions(Collections.singletonList(codeDeployAction))
                                        .build()
                        )
                )
                .role(codePipelineRole)
                .build();
    }

    @NotNull
    private Project getBuildProject(String repoName,
                                    IRepository codeCommitRepository,
                                    IRole role,
                                    Cache cache) {
        // Create a CodeBuild project for building Lambda code
        return Project.Builder.create(this, repoName + "-build")
                .projectName(repoName + "-build")
                .source(Source.codeCommit(CodeCommitSourceProps
                        .builder()
                        .repository(codeCommitRepository)
                        .branchOrRef("master")
                        .build()))
                .buildSpec(
                        BuildSpec.fromSourceFilename("buildspec.yml")
                )
                .environment(
                        BuildEnvironment.builder()
                                .buildImage(LinuxBuildImage.STANDARD_5_0)
                                .build()
                )
                .role(role)
                .cache(cache)
                .build();
    }

    @NotNull
    private Project getDeployProject(IRepository codeCommitRepository,
                                     IRole role,
                                     Cache cache
    ) {
        // Create a CodeBuild project for building Lambda code
        return Project.Builder.create(this, "lambda-deploy")
                .projectName("lambda-deploy")
                .source(Source.codeCommit(CodeCommitSourceProps
                        .builder()
                        .repository(codeCommitRepository)
                        .branchOrRef("master")
                        .build()))
                .buildSpec(
                        BuildSpec.fromSourceFilename("deployChanges.yml")
                )
                .environment(
                        BuildEnvironment.builder()
                                .buildImage(LinuxBuildImage.STANDARD_5_0)
                                .build()
                )
                .role(role)
                .cache(cache)
                .build();
    }

    @NotNull
    private IRepository getRepository(String repoName) {
        IRepository codeCommitRepository = Repository.fromRepositoryName(
                this, repoName, repoName);
        if (codeCommitRepository == null) {
            codeCommitRepository = Repository.Builder.create(this, repoName)
                    .repositoryName(repoName)
                    .build();
        }
        return codeCommitRepository;
    }

    @NotNull
    private Cache getCacheBucket() {
        return Cache.bucket(
                Bucket.fromBucketName(
                        this,
                        "build-cache",
                        "code-build-cache.novattipayments.com"
                )
        );
    }

    @NotNull
    private IRole getCodeBuildDeveloperRole() {
        return Role.fromRoleArn(
                this,
                "CodeBuildDeveloperRole",
                "arn:aws:iam::306171637169:role/CodeBuildDeveloperRole"
        );
    }

    @NotNull
    private IRole getCodePipelineDeveloperRole() {
        return Role.fromRoleArn(
                this,
                "CodePipelineDeveloperRole",
                "arn:aws:iam::306171637169:role/CodePipelineDeveloperRole"
        );
    }
}

