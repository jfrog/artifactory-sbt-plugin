name := "sbt-artifactory"

organization := "org.jfrog.buildinfo"

version := "1.0-SNAPSHOT"

sbtPlugin := true

resolvers := 
 ("jcenter" at "http://jcenter.bintray.com/") :: Nil

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "latest.release"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-ivy" % "latest.release"