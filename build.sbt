name := "sbt-artifactory"

organization := "org.jfrog.buildinfo"

version := "1.0-SNAPSHOT"

sbtPlugin := true

resolvers += "jcenter" at "http://jcenter.bintray.com/"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "2.2.7"