package  org.jfrog.build.sbtplugin

import sbt._
import sbt.Keys._
import org.jfrog.build.client.ArtifactoryClientConfiguration
import org.jfrog.build.api.util.NullLog

object ArtifactoryKeys {
	val artifactory = settingKey[ArtifactoryClientConfiguration]("An api to collect/store published artifact information.")
	val artifactoryRecordInfo = taskKey[String]("")
	val artifactoryPublish = taskKey[Unit]("publishing all files to artifactory.")
}

import ArtifactoryKeys._
object ArtifactoryPlugin extends AutoPlugin {
	override def trigger = allRequirements
	override def requires = sbt.plugins.IvyPlugin
	val autoImport = ArtifactoryKeys


	override def projectSettings: Seq[Setting[_]] = 
	  Seq(
	  	// TODO - override publishTo with the artifactory config to push aritfacts into
	  	// TODO - override resolvers with a resolver which can pull from artifactory
        // TODO - attach hooks to publish so we can generate JSON
        resolvers := {
        	defineResolvers(artifactory.value.resolver) match {
        		case Nil => resolvers.value
        		case stuff => stuff
        	} 
        },
        artifactoryRecordInfo :=  {
        	val arts = packagedArtifacts.value
        	val deps = update.value
        	val info = projectID.value
        	// TODO - Fill out stuff on artifactory
        	println(s"RECORDING INFO FOR ${info}")
        	info.toString
        },
        artifactoryPublish := (artifactoryPublish in Global).value,
        aggregate in artifactoryPublish := false

	  )

	def defineResolvers(resolverConf: ArtifactoryClientConfiguration#ResolverHandler): Seq[Resolver] = {
		val url = resolverConf.getUrl
		import org.apache.commons.lang.StringUtils
		if(StringUtils.isNotBlank(url)) {
			def betterUrl = resolverConf.urlWithMatrixParams(url)
			def mavenRepo = 
			  if(resolverConf.isMaven) Seq("artifactory-maven-resolver" at betterUrl)
			  else Nil
			def ivyRepo =
			  if(resolverConf.isIvyRepositoryDefined) {
			  	Seq(
			  	  Resolver.url("artifactory-ivy-resolver", sbt.url(betterUrl))(Patterns(resolverConf.getIvyArtifactPattern))
			  	)
			  } else Nil
			mavenRepo ++ ivyRepo
		}
		else Seq.empty
	}

	override def globalSettings: Seq[Setting[_]] =
	  Seq(
	  	artifactory := {
	  		val config = new ArtifactoryClientConfiguration(new NullLog)
	  		//config.
	  		config
	  	},
	  	artifactoryPublish := {
	  		// Ignore this result, just declare a dependency on all the 
	  		// record tasks.
	  		val allInfoIsRecorded = recordAllTasksEverywhere.value 
	  		// Publish
	  		println(s"PUBLISHING ${allInfoIsRecorded mkString "\n"}!")
	  	}
	  )

   //  A dynamic task which looks up the artifactoryRecordInfo task on ALL
   //  possible projects and joins the results together.
   lazy val recordAllTasksEverywhere = Def.taskDyn {
   	  val refs = buildStructure.value.allProjectRefs
   	  joinAllExistingTasks(refs, artifactoryRecordInfo)
   }
   // Joins all tasks in a project, returns the results in a sequence.
   // If a project does not have a task, that project's task result does not show up in
   // the seuqence.
   def joinAllExistingTasks[T](refs: Seq[ProjectRef], key: TaskKey[T]): Def.Initialize[Task[Seq[T]]] = {
   	 val taskInitializers: Seq[Def.Initialize[Task[Seq[T]]]] =
   	   for {
   	   	 ref <- refs
   	   	 init = (key in ref).?
   	   } yield init map (_.toSeq)
   	 taskInitializers reduce { (lhs, rhs) =>
   	 	lhs.zipWith(rhs) { (task, task2) =>
   	 		for {
   	 			result <- task
   	 			result2 <- task2
   	 		} yield result ++ result2
   	 	}
   	 }
   }


}