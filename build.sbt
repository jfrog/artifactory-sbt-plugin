name := "sbt-artifactory"

organization := "org.jfrog.buildinfo"

version := "1.0-SNAPSHOT"

sbtPlugin := true

resolvers := 
 ("jcenter" at "http://artifactory/artifactory") :: Nil

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "2.3.3"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-ivy" % "latest.release"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-gradle" % "latest.release"