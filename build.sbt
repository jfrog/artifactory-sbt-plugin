name := "sbt-artifactory"

organization := "org.jfrog.buildinfo"

version := "0.2"

sbtPlugin := true

resolvers := 
 ("jcenter" at "http://localhost:8081/artifactory/jcenter") :: Nil

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor" % "2.5.1"

libraryDependencies +=
  "org.jfrog.buildinfo" % "build-info-extractor-ivy" % "2.5.0"