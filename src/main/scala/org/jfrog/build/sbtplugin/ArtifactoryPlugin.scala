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
	val autoImport = ArtifactoryKeys


	override def projectSettings: Seq[Setting[_]] = 
	  Seq(
	  	publishTo := {
	  		val config = artifactoryClientConfiguration.value
	  		Some("foo" at "bar")
	  	}
	  )

	override def globalSettings: Seq[Setting[_]] =
	  Seq(
	  	artifactoryClientConfiguration := {
	  		val config = new ArtifactoryClientConfiguration(new NullLog)
	  		//config.
	  		config
	  	}
	  )
}