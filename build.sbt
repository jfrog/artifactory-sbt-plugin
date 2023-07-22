ThisBuild / version := "0.3-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.18"



libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-ivy" % "2.39.7"



libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "2.39.7"


lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "build-info-extractor-sbt",
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    organization := "org.jfrog.buildinfo"
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyJarName in assembly := s"build-info-extractor-sbt-${version.value}-uber.jar"