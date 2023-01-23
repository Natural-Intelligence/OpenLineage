@Library('ni-utils@support-openLineage') _

//service name is extrapolated from repository name check
def svcName = currentBuild.rawBuild.project.parent.displayName

// Define pod
def pod = libraryResource 'com/naturalint/kafka-agent-gradle.yaml'
print pod

// Define sharedLibrary
def sharedLibrary = new com.naturalint.kafkaConnectGradle()

// Custom maven build command
// def mavenBuildCommand=""

// Custom unit test command

// Set slack channel
def slackChannel = "kafka-connect-cicd"
//def mavenBuildCommand = "./gradlew build :spotlessApply"
def mavenBuildCommand = "./gradlew publishToMavenLocal"

// Args for pipeline
def initiateData = [projectPathVersion: "client/java", projectArtifact: "jar", projectBucketName: "openlineage", grepVersion: "version"]
def compileData = [run: true, maven_build_cmd: mavenBuildCommand, projectToCompile: ["client/java","integration/spark" ]]
def testData = [run: false] // Default is: python -m unittest
def artifactData = [run: true]
def intTestData = [run: false]
def deploymentData = [run: false]
def buildCommands = [
    initiateData: initiateData,
    compileData: compileData,
    testData: testData,
    artifactData: artifactData,
    intTestData: intTestData,
    deploymentData: deploymentData
]

timestamps {
    commonPipeline(sharedLibrary, svcName, buildCommands, pod, slackChannel)
}
