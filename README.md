# artifactory-sbt-plugin
The SBT Plugin for Artifactory resolve and pulish

What this tool (currently) doesn't do:
1.) This plug-in is not yet production ready, it does not yet actually interact with artifactory
2.) Get settings from the build environment to determine the settings needed to deploy artifactory

What this tool does do:
1.) Separate out the SBT artifacts and put them into the objects necessary to interact with the JFrog
BuildInfo libraries in artifactory, with a properly calculated path
2.) Lots and lots of debug statements printed out to the SBT shell so you can see what we're doing as we
build/parse out the build info for deployment to artifactory
