name := "sbt-artifactory"

organization := "org.jfrog.buildinfo"

version := "0.2"

sbtPlugin := true

resolvers := 
 ("jcenter" at "http://localhost:8081/artifactory/jcenter") :: Nil

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "latest.release"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-ivy" % "latest.release"