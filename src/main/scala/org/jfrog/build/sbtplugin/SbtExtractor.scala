/*
 * Copyright (C) 2015 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jfrog.build.sbtplugin


import org.apache.ivy.Ivy
import org.jfrog.build.client.{ArtifactoryBuildInfoClient, ArtifactoryClientConfiguration, DeployDetails}
import org.jfrog.build.extractor.BuildInfoExtractorUtils

//Provides details for deployment
import org.jfrog.build.api.util.FileChecksumCalculator
import sbt._
import org.jfrog.build.api.Module  //Contains build module information
//import org.jfrog.build.api.util.DeployableFile  //markg: This would be a good way to do it, but build-info doesn't yet implement
import java.io.File
import org.jfrog.build.api.builder.ModuleBuilder
import org.jfrog.build.extractor.BuildInfoExtractorUtils.getModuleIdString
import java.util.Properties
//import org.jfrog.build.util.IvyResolverHandler
import org.apache.ivy.core.IvyPatternHelper
import scala.collection.JavaConversions

/**
 * @author freds
 * @author markg
 */
object SbtExtractor {

  def defineResolvers(resolverConf: ArtifactoryClientConfiguration#ResolverHandler): Seq[Resolver] = {
    val url = resolverConf.getUrl
    import org.apache.commons.lang.StringUtils
    if (StringUtils.isNotBlank(url)) {
      def betterUrl = resolverConf.urlWithMatrixParams(url)
      def mavenRepo =
        if (resolverConf.isMaven) Seq("artifactory-maven-resolver" at betterUrl)
        else Nil
      def ivyRepo =
        if (resolverConf.isIvyRepositoryDefined) {
          Seq(
            Resolver.url("artifactory-ivy-resolver", sbt.url(betterUrl))(Patterns(resolverConf.getIvyArtifactPattern))
          )
        } else Nil
      mavenRepo ++ ivyRepo
    }
    else Seq.empty
  }

  def extractModule(log: sbt.Logger, artifacts: Map[Artifact, File], report: UpdateReport, moduleId: ModuleID,
                    configuration: ArtifactoryClientConfiguration): ArtifactoryModule = {
    log.info(s"BuildInfo: extracting info for module $moduleId")
  //TODO - UpdateReport contains the full graph, much more detailed than the POM, need from it.
  //  log.info(s"ArtifactoryPluginInfo report: ${report}")
    //report.configurations //configuration.details has the new model
    log.info(s"Org: ${moduleId.organization} name: ${moduleId.name} rev: ${moduleId.revision}")
    val module: Module = new ModuleBuilder().id(getModuleIdString(moduleId.organization, moduleId.name, moduleId.revision)).build()
    val ddIterate: Iterable[DeployDetails] = createDeployDetailsSeq(log, artifacts, configuration, moduleId)
    val aModule: ArtifactoryModule = new ArtifactoryModule(module, ddIterate)
    if(aModule.deployableFiles.isEmpty)
      log.info(s"DeployableFiles is Empty")
    else printDD(ddIterate, log)
    aModule
  }

  def printDD(ddIterate: Iterable[DeployDetails], log: sbt.Logger): Unit =
  {
    for(tempDD <- ddIterate) {
    log.info(s"ArtifactoryPlugInfo DeployDetails: $tempDD file: ${tempDD.getFile} TargetRepo: ${tempDD.getTargetRepository}" +
      s" ArtfPath: ${tempDD.getArtifactPath} md5: ${tempDD.getMd5} sha1: ${tempDD.getSha1}")
    }
  }

  def createDeployDetailsSeq(log: sbt.Logger, artifacts: Map[Artifact, File],
                             configuration: ArtifactoryClientConfiguration, moduleId: ModuleID): Iterable[DeployDetails] = {
    // TODO - Figure out what to do with extra file metadata.  Properties?
    // TODO - need to add build info fields, as per buildDeployDetails
    log.info(s"ArtifactoryPluginInfo Artifacts: $artifacts")
    if(artifacts.nonEmpty) {
      def tempSeqDD: Iterable[DeployDetails] = for {
        artf <- artifacts.keys
        f: File = artifacts.get(artf).get
        //      log.info(s"ArtifactoryPlugInfo File: $f")
        checksums: java.util.Map[String, String] = FileChecksumCalculator.calculateChecksums(f, "md5", "sha1")
        myPath = calculateArtifactPath(configuration.publisher, moduleId, artf)
      //val myPath = s"$f"
      } yield new DeployDetails.Builder().file(f).targetRepository(configuration.publisher.getRepoKey).artifactPath(myPath).
          md5(checksums.get("md5")).sha1(checksums.get("sha1")).build()
      tempSeqDD
    }
    else null //TODO: I am supposed to be using option instead of returning null here?
  }


  def publish(log: sbt.Logger, configuration: ArtifactoryClientConfiguration, modules: Seq[ArtifactoryModule]): Unit = {
    // Publish
    //TODO: Compare with build-info-extractor-ivy ArtifactoryBuildListener.doDeploy() some configs are still not present (such as blackduck integration)
    val myACC = makeACC(log, configuration)
    printACC(log, myACC)
    val contextUrl = myACC.publisher.getContextUrl();
    val username = myACC.publisher.getUsername();
    val password = myACC.publisher.getPassword();
    log.info(s"BuildInfo: Publishing ${modules.map (_.module.getId) mkString ", "}")
    val myABIC: ArtifactoryBuildInfoClient = new ArtifactoryBuildInfoClient(contextUrl, username, password, myACC.getLog)
    //TODO: skipping checks on isPublishArtifacts and isPublishBuildInfo we will assume they are true for now
    //TODO: Haven't really created a buildinfo yet, need to do that.
    //TODO: for now, skipping include/exclude patterns
    log.info(s"BuildInfo: Publishing based on ABIC ${myABIC.toString}")
    for(module <- modules) {
      log.info(s"BuildInfo: Publishing Module ${module.module.getId}")
      if(module.deployableFiles.isEmpty)
        log.info(s"DeployableFiles is Empty")
      else log.info(s"DeployableFiles is not Empty")
      for(detail <- module.deployableFiles) {
        myABIC.deployArtifact(detail)
        log.info(s"BuildInfo: Publishing Detail ${detail.getArtifactPath}")
      }
    }
  }

  def makeACC(log: sbt.Logger, configuration: ArtifactoryClientConfiguration): ArtifactoryClientConfiguration = {
    val props: Properties = new Properties
    props.putAll(System.getenv)
    val myProps = BuildInfoExtractorUtils.mergePropertiesWithSystemAndPropertyFile(props, configuration.getLog)
    configuration.fillFromProperties(myProps)
    configuration
  }

  def printACC(log: sbt.Logger, configuration: ArtifactoryClientConfiguration): Unit = {
    log.info(s"ArtifactoryClientConfig: $configuration")
    log.info(s"ArtifactoryClientConfig Base Values:")
    log.info(s"ACC AllProperties: ${configuration.getAllProperties}")
    log.info(s"ACC AllRootConfig: ${configuration.getAllRootConfig}")
    log.info(s"ACC EnvVarsExcludePatterns: ${configuration.getEnvVarsExcludePatterns}")
    log.info(s"ACC EnvVarsIncludePatterns: ${configuration.getEnvVarsIncludePatterns}")
    log.info(s"ACC ExportFile: ${configuration.getExportFile}")
    log.info(s"ACC Log: ${configuration.getLog}")
    log.info(s"ACC PropertiesFile: ${configuration.getPropertiesFile}")
    log.info(s"ACC Timeout: ${configuration.getTimeout}")
    log.info(s"ArtifactoryClientConfig Resolver Values:")
    log.info(s"ArtifactoryClientConfig resolver: ${configuration.resolver}")
    log.info(s"ACC Resolver booleans:")
    log.info(s"ACC Resolver isIvyRepositoryDefined: ${configuration.resolver.isIvyRepositoryDefined}")
    log.info(s"ACC Resolver isIvy: ${configuration.resolver.isIvy}")
    log.info(s"ACC Resolver isM2Compatible: ${configuration.resolver.isM2Compatible}")
    log.info(s"ACC Resolver isMaven: ${configuration.resolver.isMaven}")
    log.info(s"ACC Resolver values:")
    log.info(s"ACC Resolver BuildRoot: ${configuration.resolver.getBuildRoot}")
    log.info(s"ACC Resolver ContextURL: ${configuration.resolver.getContextUrl}") //TODO: this needs to be defined in build.sbt as per readme, should we do something with initialization?
    log.info(s"ACC Resolver DownloadSnapshotRepoKey: ${configuration.resolver.getDownloadSnapshotRepoKey}")
    log.info(s"ACC Resolver DownloadURL: ${configuration.resolver.getDownloadUrl}")
    log.info(s"ACC Resolver MatrixParamPrefix: ${configuration.resolver.getMatrixParamPrefix}")
    log.info(s"ACC Resolver IvyArtifactPattern: ${configuration.resolver.getIvyArtifactPattern}")
    log.info(s"ACC Resolver IvyPattern: ${configuration.resolver.getIvyPattern}")
    log.info(s"ACC Resolver Log: ${configuration.resolver.getLog}")
    log.info(s"ACC Resolver MatrixParams: ${configuration.resolver.getMatrixParams}")
    log.info(s"ACC Resolver Name: ${configuration.resolver.getName}")
    log.info(s"ACC Resolver Password: ${configuration.resolver.getPassword}")
    log.info(s"ACC Resolver Prefix: ${configuration.resolver.getPrefix}")
    log.info(s"ACC Resolver RepoKey: ${configuration.resolver.getRepoKey}")
    log.info(s"ACC Resolver URL: ${configuration.resolver.getUrl}")
    log.info(s"ACC Resolver URLwithMatrixParams: ${configuration.resolver.getUrlWithMatrixParams}")
    log.info(s"ACC Resolver Username: ${configuration.resolver.getUsername}")
    log.info(s"ArtifactoryClientConfiguration Publisher publisher Values:")
    log.info(s"ACC Publisher publisher: ${configuration.publisher}")
    log.info(s"ACC publisher booleans:")
    log.info(s"ACC Publisher isCopyAggregatedArtifacts: ${configuration.publisher.isCopyAggregatedArtifacts}")
    log.info(s"ACC Publisher isEvenUnstable: ${configuration.publisher.isEvenUnstable}")
    log.info(s"ACC Publisher isFilterExcludedArtifactsFromBuild: ${configuration.publisher.isFilterExcludedArtifactsFromBuild}")
    log.info(s"ACC Publisher isPublishAggregatedArtifacts: ${configuration.publisher.isPublishAggregatedArtifacts}")
    log.info(s"ACC Publisher isPublishArtifacts: ${configuration.publisher.isPublishArtifacts}")
    log.info(s"ACC Publisher isPublishBuildInfo: ${configuration.publisher.isPublishBuildInfo}")
    log.info(s"ACC Publisher isRecordAllDependencies: ${configuration.publisher.isRecordAllDependencies}")
    log.info(s"ACC Publisher isIvy: ${configuration.publisher.isIvy}")
    log.info(s"ACC Publisher isM2Compatible: ${configuration.publisher.isM2Compatible}")
    log.info(s"ACC Publisher isMaven: ${configuration.publisher.isMaven}")
    log.info(s"ACC publisher values:")
    log.info(s"ACC Publisher AggregateArtifacts: ${configuration.publisher.getAggregateArtifacts}")
    log.info(s"ACC Publisher ArtifactSpecs: ${configuration.publisher.getArtifactSpecs}")
    log.info(s"ACC Publisher BuildRoot: ${configuration.publisher.getBuildRoot}")
    log.info(s"ACC Publisher ContextURL: ${configuration.publisher.getContextUrl}") //TODO: this needs to be defined in build.sbt as per readme, should we do something with initialization?
    log.info(s"ACC Publisher getExcludePatterns: ${configuration.publisher.getExcludePatterns}")
    log.info(s"ACC Publisher getIncludePatterns: ${configuration.publisher.getIncludePatterns}")
    log.info(s"ACC Publisher MatrixParamPrefix: ${configuration.publisher.getMatrixParamPrefix}")
    log.info(s"ACC Publisher SnapshotRepoKey: ${configuration.publisher.getSnapshotRepoKey}")
    log.info(s"ACC Publisher IvyArtifactPattern: ${configuration.publisher.getIvyArtifactPattern}")
    log.info(s"ACC Publisher IvyPattern: ${configuration.publisher.getIvyPattern}")
    log.info(s"ACC Publisher Log: ${configuration.publisher.getLog}")
    log.info(s"ACC Publisher MatrixParams: ${configuration.publisher.getMatrixParams}")
    log.info(s"ACC Publisher Name: ${configuration.publisher.getName}")
    log.info(s"ACC Publisher Password: ${configuration.publisher.getPassword}")
    log.info(s"ACC Publisher Prefix: ${configuration.publisher.getPrefix}")
    log.info(s"ACC Publisher RepoKey: ${configuration.publisher.getRepoKey}")
    log.info(s"ACC Publisher URL: ${configuration.publisher.getUrl}")
    log.info(s"ACC Publisher URLwithMatrixParams: ${configuration.publisher.getUrlWithMatrixParams}")
    log.info(s"ACC Publisher Username: ${configuration.publisher.getUsername}")
    log.info(s"ArtifactoryClientConfiguration BuildInfo Stuff:")
    log.info(s"ArtifactoryClientConfiguration BuildInfo: ${configuration.info}")
    log.info(s"ACC Info booleans: ")
    log.info(s"ACC Info isDeleteBuildArtifacts: ${configuration.info.isDeleteBuildArtifacts}")
    log.info(s"ACC Info isReleaseEnabled: ${configuration.info.isReleaseEnabled}")
    log.info(s"ACC Info values: ")
    log.info(s"ACC Info AgentName: ${configuration.info.getAgentName}")
    log.info(s"ACC Info AgentVersion: ${configuration.info.getAgentVersion}")
    log.info(s"ACC Info BuildAgentName: ${configuration.info.getBuildAgentName}")
    log.info(s"ACC Info BuildAgentVersion: ${configuration.info.getBuildAgentVersion}")
    log.info(s"ACC Info BuildName: ${configuration.info.getBuildName}")
    log.info(s"ACC Info BuildNumber: ${configuration.info.getBuildNumber}")
    log.info(s"ACC Info BuildNumbersNotToDelete: ${configuration.info.getBuildNumbersNotToDelete}")
    log.info(s"ACC Info BuildRetentionCount: ${configuration.info.getBuildRetentionCount}")
    log.info(s"ACC Info BuildRetentionDays: ${configuration.info.getBuildRetentionDays}")
    log.info(s"ACC Info BuildRetentionMinimumDate: ${configuration.info.getBuildRetentionMinimumDate}")
    log.info(s"ACC Info BuildRoot: ${configuration.info.getBuildRoot}")
    log.info(s"ACC Info BuildStarted: ${configuration.info.getBuildStarted}")
    log.info(s"ACC Info BuildTimestamp: ${configuration.info.getBuildTimestamp}")
    log.info(s"ACC Info BuildURL: ${configuration.info.getBuildUrl}")
    log.info(s"ACC Info ParentBuildName: ${configuration.info.getParentBuildName}")
    log.info(s"ACC Info ParentBuildNumber: ${configuration.info.getParentBuildNumber}")
    log.info(s"ACC Info Principal: ${configuration.info.getPrincipal}")
    log.info(s"ACC Info ReleaseComment: ${configuration.info.getReleaseComment}")
    log.info(s"ACC Info RunParameters: ${configuration.info.getRunParameters}")
    log.info(s"ACC Info VcsRevision: ${configuration.info.getVcsRevision}") //TODO: get this from the gitPlugin, will need a second plugin to write just this info when gitPlugin is present
    log.info(s"ACC Info VcsUrl: ${configuration.info.getVcsUrl}")
    log.info(s"ACC Info Log: ${configuration.info.getLog}")
    log.info(s"ACC Info Prefix: ${configuration.info.getPrefix}")
  }

  def calculateArtifactPath (publisher: ArtifactoryClientConfiguration#PublisherHandler, moduleId: ModuleID, artf: Artifact): String = {
    //attributes: Map[String, String], extraAttributes: Map[String, String]
    var organization: String = moduleId.organization
    val revision: String = moduleId.revision
    val moduleName: String = moduleId.name
    val ext: String = artf.extension
    val artfType: String = artf.`type`
    val artifactPattern: String = getPattern(publisher, artfType)
    if (publisher.isM2Compatible) {
      organization = organization.replace(".", "/")
    }
    IvyPatternHelper.substitute(artifactPattern, organization, moduleName, revision, artf.name, artfType, ext)
  }

  def getPattern(pub: ArtifactoryClientConfiguration#PublisherHandler, typestring: String): String = {
    if (isIvy(typestring) )
    {
      pub.getIvyPattern
    } else
    {
      pub.getIvyArtifactPattern
    }
  }

  def isIvy(typestring: String): Boolean = {
    if (typestring.isEmpty)
    {
      false
    }
    else {
      typestring.equals(s"Ivy")
    }
  }
}

case class ArtifactoryModule(
                              module: Module,
                              deployableFiles: Iterable[DeployDetails]
                              )

