package  org.jfrog.build.sbtplugin

import sbt._
import sbt.Keys._
import org.jfrog.build.client.ArtifactoryClientConfiguration
import org.jfrog.build.api.util.NullLog

object ArtifactoryKeys {
	val artifactoryClientConfiguration = settingKey[ArtifactoryClientConfiguration]("An api to collect/store published artifact information.")
}

import ArtifactoryKeys._
object ArtifactoryPlugin extends AutoPlugin {
	override def trigger = allRequirements
	override def requires = sbt.plugins.IvyPlugin


	override def projectSettings: Seq[Setting[_]] = 
	  Seq(
	  )

	override def globalSettings: Seq[Setting[_]] =
	  Seq(
	  	artifactoryClientConfiguration := new ArtifactoryClientConfiguration(new NullLog)
	  )
}