# artifactory-sbt-plugin
The SBT Plugin for Artifactory resolve and publish

##This project is currently INACTIVE.  If you are interested in it please fork and improve!  If someone were to contribute back, JFrog could commit to testing it against every new release of artifactory.  Also if you have questions feel free to ask.

To install as a global plugin:
```
got to ~/.sbt/plugins/plugins.sbt

lazy val sbtArtifactory = RootProject(file("/JFrogProds/scala-work/sbt-plugin/artifactory-sbt-plugin").toURI)

lazy val buildProject = (
  project.in(file(".")).
  dependsOn(sbtArtifactory)
)
```

What this tool (currently) doesn't do:

1.) This plug-in is in an alpha state.  It does now interact with artifactory but has a lot of features to go.  See Roadmap.md for details

What this tool does do:

1.) Separate out the SBT artifacts and put them into the objects necessary to interact with the JFrog
BuildInfo libraries in artifactory, with a properly calculated path, and deploy the artifacts.

2.) Lots and lots of debug statements printed out to the SBT shell so you can see what we're doing as we
build/parse out the build info for deployment to artifactory

3.) one can define the artifactory client configuration via the global setting 'artifactory'   for example you can put the below in your build.sbt, and then see the settings reflected when you use the task 'artifactoryPublish' inside sbt which currently outputs the artifactory client configuration settings to the log (or the shell).


artifactory := 
{

artifactory.value.resolver.setContextUrl("http://localhost:8081/artifactory")

artifactory.value.publisher.setContextUrl("http://localhost:8081/artifactory")

artifactory.value.publisher.setUsername("admin")

artifactory.value.publisher.setPassword("password")

artifactory.value

}
