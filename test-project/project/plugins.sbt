

lazy val sbtArtifactory = RootProject(file("..").toURI)

lazy val buildProject = (
  project.in(file(".")).
  dependsOn(sbtArtifactory)
)