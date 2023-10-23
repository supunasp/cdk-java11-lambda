#!/bin/sh

function getProperty {
   PROP_KEY=$1
   PROP_VALUE=`cat $PROPERTY_FILE | grep "$PROP_KEY" | cut -d'=' -f2`
   echo $PROP_VALUE
}

PROPERTY_FILE=release.properties
# export values
ARTIFACT_ID=$(mvn -f lambda/pom.xml org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.artifactId -q -DforceStdout)
GROUP_ID=$(mvn -f lambda/pom.xml org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.groupId -q -DforceStdout)
SNAPSHOT_VERSION=$(mvn -f lambda/pom.xml org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout)

echo "test build before release prepare"
mvn clean test

echo "release prepare"
mvn release:prepare -Passembly-zip -Dmaven.test.skip=true

VERSION_ID=$(getProperty "project.rel.${GROUP_ID}\\\\:${ARTIFACT_ID}")

echo "uploading release to lib-release-local : ${VERSION_ID}"
mvn deploy:deploy-file \
  -DgroupId=$GROUP_ID \
  -DartifactId=$ARTIFACT_ID \
  -Dversion="${VERSION_ID}" \
  -Dpackaging=zip \
  -Dfile="lambda/target/${ARTIFACT_ID}-${VERSION_ID}.zip" \
  -DrepositoryId=release-local \
  -Durl=https://XXXXXXX.d.codeartifact.ap-southeast-2.amazonaws.com/maven/release-local/

echo "uploading snapshot to lib-snapshot-local : ${SNAPSHOT_VERSION}"
mvn deploy:deploy-file \
  -DgroupId=$GROUP_ID \
  -DartifactId=$ARTIFACT_ID \
  -Dversion="${SNAPSHOT_VERSION}" \
  -Dpackaging=zip \
  -Dfile="lambda/target/${ARTIFACT_ID}-${VERSION_ID}.zip" \
  -DrepositoryId=snapshot-local \
  -Durl=https://XXXXXXX.d.codeartifact.ap-southeast-2.amazonaws.com/maven/snapshot-local/

echo "release clean"
mvn release:clean

echo "cdk diff and synth"
cd ${CODEBUILD_SRC_DIR}/cdk

cdk diff -c ARTIFACT_ID=${ARTIFACT_ID} -c SNAPSHOT_VERSION=${SNAPSHOT_VERSION} -c RELEASE_VERSION=${VERSION_ID}

cdk synth -c ARTIFACT_ID=${ARTIFACT_ID} -c SNAPSHOT_VERSION=${SNAPSHOT_VERSION} -c RELEASE_VERSION=${VERSION_ID}
