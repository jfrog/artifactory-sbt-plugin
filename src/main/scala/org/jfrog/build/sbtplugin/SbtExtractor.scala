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

import org.apache.commons.lang3.StringUtils
import org.apache.ivy.Ivy
import org.jfrog.build.extractor.BuildInfoExtractorUtils.getTypeString
import org.jfrog.build.extractor.clientConfiguration.{ArtifactoryClientConfiguration, IncludeExcludePatterns, PatternMatcher}
import org.jfrog.build.extractor.BuildInfoExtractorUtils
import org.jfrog.build.extractor.ci.{Agent, BuildAgent, BuildInfo, BuildInfoFields, BuildInfoProperties, Dependency, Module}
import org.jfrog.build.extractor.clientConfiguration.client.artifactory.ArtifactoryManager
import org.jfrog.build.extractor.clientConfiguration.deploy.DeployDetails
import org.jfrog.build.extractor.clientConfiguration.util.JsonUtils.toJsonString

import java.text.{ParseException, SimpleDateFormat}
import java.util
import org.jfrog.build.api.util.{CommonUtils, FileChecksumCalculator}
import sbt.*

import java.io.File
import org.jfrog.build.extractor.BuildInfoExtractorUtils.getModuleIdString

import java.util.{Date, Properties}
import scala.jdk.CollectionConverters.*
import org.apache.ivy.core.IvyPatternHelper
import org.jfrog.build.api.builder.ModuleType
import org.jfrog.build.extractor.builder.{ArtifactBuilder, BuildInfoBuilder, DependencyBuilder, ModuleBuilder}
import org.jfrog.build.extractor.retention.Utils

/**
 * @author freds
 * @author markg
 */
object SbtExtractor {

  def defineResolvers(resolverConf: ArtifactoryClientConfiguration#ResolverHandler): Seq[Resolver] = {
    Option(resolverConf.getUrl).filter(StringUtils.isNotBlank).fold[Seq[Resolver]](Nil) { url =>
      val betterUrl = resolverConf.urlWithMatrixParams(url)
      val mavenRepo =
        if (resolverConf.isMaven) {
          println("IS MAVEN")
          Seq("artifactory-maven-resolver" at betterUrl)
        } else Nil
      val ivyRepo =
        if (resolverConf.isIvyRepositoryDefined) {
          println("IS IVY")
          Seq(Resolver.url("artifactory-ivy-resolver", sbt.url(betterUrl))(Patterns(resolverConf.getIvyArtifactPattern)))
        } else Nil
      mavenRepo ++ ivyRepo
    }
  }

  def extractModule(log: sbt.Logger, artifacts: Map[Artifact, File], report: UpdateReport, moduleId: ModuleID,
                    configuration: ArtifactoryClientConfiguration): ArtifactoryModule = {
    log.info(s"BuildInfo: extracting info for module $moduleId")
    //TODO - UpdateReport contains the full graph, much more detailed than the POM, need from it.
    //  log.info(s"ArtifactoryPluginInfo report: ${report}")
    //report.configurations //configuration.details has the new model
    log.info(s"Org: ${moduleId.organization} name: ${moduleId.name} rev: ${moduleId.revision}")
    log.info("---------------------")
    //log.info(s"report: ${report.toSeq}")

    val ddIterate = createDeployDetails(log, artifacts, configuration, moduleId)
    val publisher = configuration.publisher // ConventionUtils.getPublisherHandler(project);

    // TODO: Change from GENERIC to SBT once supported.
    // TODO: add repository field
    val moduleBuilder = new ModuleBuilder().`type`(ModuleType.GENERIC).id(getModuleIdString(moduleId.organization, moduleId.name, moduleId.revision))
    moduleBuilder.dependencies(calculateDependencies(publisher, report).asJava)
    // Extract the module's artifacts

    // moduleBuilder.excludedArtifacts(calculateArtifacts(ProjectUtils.filterIncludeExcludeDetails(project, publisher, ddIterate, false)));
    moduleBuilder.artifacts(calculateArtifacts(filterIncludeExcludeDetails(publisher, ddIterate, isInclude = true)).asJava)

    ArtifactoryModule(moduleBuilder.build(), ddIterate)
  }

  private def createDeployDetails(log: sbt.Logger, artifacts: Map[Artifact, File],
                                  configuration: ArtifactoryClientConfiguration, moduleId: ModuleID): List[SbtDeployDetails] = {
    // TODO - Figure out what to do with extra file metadata.  Properties?
    // TODO - need to add build info fields, as per buildDeployDetails
    log.info(s"ArtifactoryPluginInfo Artifacts: $artifacts")
    for {
      (artifact, f) <- artifacts
      checksums = FileChecksumCalculator.calculateChecksums(f, "md5", "sha1")
      myPath = calculateArtifactPath(configuration.publisher, moduleId, artifact)
    } yield SbtDeployDetails(new DeployDetails.Builder().file(f).targetRepository(configuration.publisher.getRepoKey).artifactPath(myPath).
      md5(checksums.get("md5")).sha1(checksums.get("sha1")).build(), artifact)
  }.toList

  def publish(log: sbt.Logger, configuration: ArtifactoryClientConfiguration, defaultProjectName: String, modules: Seq[ArtifactoryModule], defBIFile: File): Unit = {

    //TODO: Compare with build-info-extractor-ivy ArtifactoryBuildListener.doDeploy() some configs are still not present (such as blackduck integration)
    val myACC = makeACC(log, configuration)
    //    val contextUrl = myACC.publisher.getContextUrl
    //    val username = myACC.publisher.getUsername
    //    val password = myACC.publisher.getPassword
    log.info(s"BuildInfo: Publishing ${modules.map(_.module.getId) mkString ", "}")
    val myABIC: ArtifactoryManager = createArtifactoryManager(myACC.publisher)
    //      new ArtifactoryManager(contextUrl, username, password, myACC.getLog)
    //TODO: skipping checks on isPublishArtifacts and isPublishBuildInfo we will assume they are true for now
    //TODO: Haven't really created a buildinfo yet, need to do that.
    //TODO: for now, skipping include/exclude patterns
    log.info(s"BuildInfo: Publishing based on ABIC ${myABIC.toString}")
    for (module <- modules) {
      log.info(s"BuildInfo: Publishing Module ${module.module.getId}")
      if (module.deployableFiles.isEmpty)
        log.info(s"DeployableFiles is Empty")
      else log.info(s"DeployableFiles is not Empty")
      for (detail <- module.deployableFiles) {
        myABIC.upload(detail.deployDetails)
        log.info(s"BuildInfo: Publishing Detail ${detail.deployDetails.getArtifactPath}")
      }
    }
    val bi = makeBuildInfo(log, configuration, defaultProjectName, modules)
    log.info("---------------------")
    log.info(s"BI:   $bi")
    log.info("---------------------")
    log.info(s"Module:  ")
    val m = bi.getModules.asScala.head
    log.info(toJsonString(m))
    log.info("---------------------")
    exportBuildInfoToFileSystem(log, configuration, bi, defBIFile)
    if (configuration.publisher.getContextUrl != null) {
      //val artifactoryManager = myABIC//createArtifactoryManager(configuration.publisher)

      // configureProxy(accRoot.proxy, artifactoryManager)
      //  if (configuration.publisher.isPublishBuildInfo) {
      log.info(s"Publishing build info to artifactory at: ${configuration.publisher.getContextUrl}")
      Utils.sendBuildAndBuildRetention(myABIC, bi, configuration)
      //   }
      // exportDeployedArtifacts(accRoot, allDeployDetails)
    }


    if (myACC.publisher.isPublishBuildInfo) {
      //     myABIC.sendBuildInfo(???)
    }
  }

  private def createArtifactoryManager(publisher: ArtifactoryClientConfiguration#PublisherHandler) = {
    val contextUrl = publisher.getContextUrl
    var username = publisher.getUsername
    var password = publisher.getPassword
    if (StringUtils.isBlank(username)) username = ""
    if (StringUtils.isBlank(password)) password = ""
    new ArtifactoryManager(contextUrl, username, password, publisher.getLog)
  }

  //  private def getPropsToAdd(destination: ArtifactoryTask, artifact: Artifact, publicationName: String): util.Map[String, String] = {
  //  //  val project: Project = destination.getProject
  //    val propsToAdd: util.Map[String, String] = new util.HashMap[String, String]()//(destination.getDefaultProps)
  //    // Apply artifact-specific props from the artifact specs
  //    val spec: ArtifactSpec = ArtifactSpec.builder.configuration(publicationName).
  //      group(project.getGroup.toString).
  //      name(project.getName).
  //      version(project.getVersion.toString).
  //      classifier(artifact.classifier.getOrElse("")).`type`(artifact.`type`).build
  //    val artifactSpecsProperties: Multimap[String, CharSequence] = destination.artifactSpecs.getProperties(spec)
  //    addProps(propsToAdd, artifactSpecsProperties)
  //    propsToAdd
  //  }


  private def makeBuildInfo(log: sbt.Logger, configuration: ArtifactoryClientConfiguration, defaultProjectName: String, modules: Seq[ArtifactoryModule]): BuildInfo = {
    var buildName = configuration.info.getBuildName
    if (StringUtils.isBlank(buildName)) {
      buildName = defaultProjectName
      configuration.info.setBuildName(buildName)
    }
    //   config.publisher.setMatrixParam(BuildInfoFields.BUILD_NAME, buildName)

    // Build number// Build number
    var buildNumber = configuration.info.getBuildNumber
    if (StringUtils.isBlank(buildNumber)) {
      buildNumber = new Date().getTime + ""
      configuration.info.setBuildNumber(buildNumber)
    }
    val bib = new BuildInfoBuilder(configuration.info.getBuildName).number(configuration.info.getBuildNumber).
      project(configuration.info.getProject).modules(modules.map(_.module).asJava).startedDate(populateBuilderDateTimeFields(log, configuration)).
      buildAgent(new BuildAgent("Ivy", Ivy.getIvyVersion)).
      agent(new Agent("Ivy", Ivy.getIvyVersion))
    bib.build
    //    val buildName: String = if (StringUtils.isBlank(configuration.info.getBuildName)) "sbt-default" else configuration.info.getBuildName
    /*
    populateBuilderModulesFields(bib);

            // Run Parameters (Properties)
            for (Map.Entry<String, String> runParam : clientConf.info.getRunParameters().entrySet()) {
                MatrixParameter matrixParameter = new MatrixParameter(runParam.getKey(), runParam.getValue());
                bib.addRunParameters(matrixParameter);
            }

            // Other Meta Data
            populateBuilderAgentFields(bib);
            populateBuilderParentFields(bib);
            populateBuilderArtifactoryPluginVersionField(bib);

            Date buildStartDate = populateBuilderDateTimeFields(bib);
            String principal = populateBuilderPrincipalField(bib);
            String artifactoryPrincipal = populateBuilderArtifactoryPrincipalField(bib);

            // Other services information
            populateBuilderPromotionFields(bib, buildStartDate, principal, artifactoryPrincipal);
            populateBuilderVcsFields(bib);
            populateBuilderIssueTrackerFields(bib);
     */
    //    val builder: BuildInfoBuilder = new BuildInfoBuilder(buildName).modules(modules.map(_.module).asJava).
    //      number("0").
    // durationMillis(System.currentTimeMillis - ctx.getBuildStartTime).

  }

  private def populateBuilderDateTimeFields(log: sbt.Logger, configuration: ArtifactoryClientConfiguration): Date = {
    val buildStartedIso = configuration.info.getBuildStarted
    var buildStartDate: Date = null
    try {
      buildStartDate = new SimpleDateFormat(BuildInfo.STARTED_FORMAT).parse(buildStartedIso)
    } catch {
      case e: ParseException => log.info("Build start date format error: " + buildStartedIso + " " + e.getMessage)
    }
    //    val durationMillis = if (buildStartDate != null) System.currentTimeMillis() - buildStartDate.getTime else 0

    buildStartDate
  }
  
  private def makeACC(log: sbt.Logger,configuration: ArtifactoryClientConfiguration): ArtifactoryClientConfiguration = {
    val props: Properties = new Properties
    props.putAll(System.getenv.asInstanceOf[java.util.Map[?, ?]])

    val mergedProps = BuildInfoExtractorUtils.mergePropertiesWithSystemAndPropertyFile(props, configuration.getLog)

    val buildInfoProps: java.util.Map[?,?] =
      BuildInfoExtractorUtils.stripPrefixFromProperties(BuildInfoExtractorUtils.filterDynamicProperties(mergedProps, BuildInfoExtractorUtils.BUILD_INFO_PROP_PREDICATE), BuildInfoProperties.BUILD_INFO_PROP_PREFIX)
    mergedProps.putAll(buildInfoProps)

    // Add the properties to the Artifactory client configuration.
    // In case the build name and build number have already been added to the configuration
    // from inside the gradle script, we do not want to override them by the values sent from
    // the CI server plugin.
    val prefix = BuildInfoProperties.BUILD_INFO_PREFIX
    val excludeIfExist = CommonUtils.newHashSet(prefix + BuildInfoFields.BUILD_NUMBER, prefix + BuildInfoFields.BUILD_NAME, prefix + BuildInfoFields.BUILD_STARTED)
    configuration.fillFromProperties(mergedProps, excludeIfExist);

    configuration
  }

  private def calculateArtifactPath(publisher: ArtifactoryClientConfiguration#PublisherHandler, moduleId: ModuleID, artf: Artifact): String = {
    //attributes: Map[String, String], extraAttributes: Map[String, String]
    val organization = if (publisher.isM2Compatible) {
      moduleId.organization.replace(".", "/")
    } else {
      moduleId.organization
    }

    val artfType: String = artf.`type`
    val artifactPattern: String = getPattern(publisher, artfType)
    val extraTokens = new util.HashMap[String, String]
    artf.classifier.filter(StringUtils.isNotBlank).foreach(extraTokens.put("classifier", _))
    IvyPatternHelper.substitute(artifactPattern, organization, moduleId.name, moduleId.revision, artf.name, artfType, artf.extension, "XXXX"
      , extraTokens, null)
  }

  private def getPattern(pub: ArtifactoryClientConfiguration#PublisherHandler, typestring: String): String =
    if (typestring == "Ivy") pub.getIvyPattern else pub.getIvyArtifactPattern

  private def calculateArtifacts(deployDetails: List[SbtDeployDetails]) = {
    deployDetails.map { case SbtDeployDetails(dd, art) =>
      val artifactPath = dd.getArtifactPath
      new ArtifactBuilder(artifactPath.substring(artifactPath.lastIndexOf('/') + 1)).`type`(
        getTypeString(art.`type`, art.classifier.getOrElse(""), art.extension)
      )
        .md5(dd.getMd5)
        .sha1(dd.getSha1)
        .sha256(dd.getSha256)
        .remotePath(artifactPath).build()
    }
  }

  // TODO: support include/exclude
  def filterIncludeExcludeDetails(publisher: ArtifactoryClientConfiguration#PublisherHandler, deployDetails: List[SbtDeployDetails],
                                  isInclude: Boolean): List[SbtDeployDetails] = {
    val patterns = new IncludeExcludePatterns(publisher.getIncludePatterns, publisher.getExcludePatterns)
    if (publisher.isFilterExcludedArtifactsFromBuild) {
      deployDetails.filter { dd =>
        if (isInclude) {
          !PatternMatcher.pathConflicts(dd.deployDetails.getArtifactPath, patterns)
        }
        PatternMatcher.pathConflicts(dd.deployDetails.getArtifactPath, patterns)
      }
    } else {
      if (isInclude) deployDetails.filter {
        dd =>
          if (dd == null)
            false
          true // TODO: filter on project??  dd.getProject().equals(project);
      } else Nil

    }
  }

  private def calculateDependencies(publisher: ArtifactoryClientConfiguration#PublisherHandler, report: UpdateReport): List[Dependency] = {
    import org.jfrog.build.api.util.FileChecksumCalculator.*
    // TODO: will try to group by moduleId+artifact+file to be on the safe side, not sure when they combine differently
    report.toSeq.groupBy {
      case (_, moduleId, artifact, file) => (moduleId, artifact, file)
    }.mapValues(_.map(_._1))
      .map {
        case ((moduleId, artifact, file), scopes) =>
          val depId = s"${moduleId.organization}:${moduleId.name}:${moduleId.revision}"
          // configRef is the compile/runtime/test/etc...
          val dependencyBuilder = new DependencyBuilder().id(depId)
            .scopes(scopes.map(_.name).toSet.asJava)
          /*if (requestedByMap != null) {
            dependencyBuilder.requestedBy(requestedByMap.get(depId));
          }*/

          val checksums = FileChecksumCalculator.calculateChecksums(file, MD5_ALGORITHM, SHA1_ALGORITHM, SHA256_ALGORITHM)
          dependencyBuilder.md5(checksums.get(MD5_ALGORITHM)).
            sha1(checksums.get(SHA1_ALGORITHM)).
            sha256(checksums.get(SHA256_ALGORITHM))

          dependencyBuilder.build()
      }.toList
  }

  private def exportBuildInfoToFileSystem(log: sbt.Logger, configuration: ArtifactoryClientConfiguration, buildInfo: BuildInfo, defBIFile: File): Unit = {
    exportBuildInfo(log, buildInfo, getExportFile(configuration, defBIFile))

    if (!StringUtils.isEmpty(configuration.info.getGeneratedBuildInfoFilePath)) {
      exportBuildInfo(log, buildInfo, new File(configuration.info.getGeneratedBuildInfoFilePath))
    }
  }

  private def getExportFile(clientConf: ArtifactoryClientConfiguration, defBIFile: File): File = {
    // Configured path
    val fileExportPath = clientConf.getExportFile
    if (StringUtils.isNotBlank(fileExportPath)) new File(fileExportPath) else defBIFile

  }

  private def exportBuildInfo(log: sbt.Logger, buildInfo: BuildInfo, toFile: File): Unit = {
    log.log(Level.Info, s"Exporting generated build info to ${toFile.getAbsolutePath}")
    BuildInfoExtractorUtils.saveBuildInfoToFile(buildInfo, toFile)
  }
}

case class ArtifactoryModule(module: Module, deployableFiles: Iterable[SbtDeployDetails])

case class SbtDeployDetails(deployDetails: DeployDetails, artifact: Artifact)