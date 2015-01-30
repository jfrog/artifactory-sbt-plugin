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

import org.jfrog.build.client.ArtifactoryClientConfiguration
import org.jfrog.build.client.DeployDetails  //Provides details for deployment
import org.jfrog.build.api.util.FileChecksumCalculator
import sbt._
import org.jfrog.build.api.Module  //Contains build module information
//import org.jfrog.build.api.util.DeployableFile  //markg: This would be a good way to do it, but build-info doesn't yet implement
import java.io.File
import org.jfrog.build.api.builder.ModuleBuilder
import org.jfrog.build.extractor.BuildInfoExtractorUtils.getModuleIdString

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

  def extractModule(log: sbt.Logger, artifacts: Map[Artifact, File], report: UpdateReport, moduleId: ModuleID): ArtifactoryModule = {
    log.info(s"BuildInfo: extracting info for module $moduleId")
  //TODO - figure out what to do with report
  //  log.info(s"ArtifactoryPluginInfo report: ${report}")
    val module: Module = new ModuleBuilder().id(getModuleIdString(moduleId.organization, moduleId.name, moduleId.revision)).build()
    ArtifactoryModule(module, createDeployDetailsSeq(log, artifacts))
  }

  def createDeployDetailsSeq(log: sbt.Logger, artifacts: Map[Artifact, File]): Seq[DeployDetails] = {
    // TODO - Figure out targetRepo
    // TODO - Figure out what to do with extra file metadata.  Properties?
    log.info(s"ArtifactoryPluginInfo Artifacts: $artifacts")
    def tempSeqDD: Seq[DeployDetails] = Seq.empty
    if(artifacts.nonEmpty) {
      for (f <- artifacts.values) {
        log.info(s"ArtifactoryPlugInfo File: $f")
        val checksums: java.util.Map[String, String] = FileChecksumCalculator.calculateChecksums(f, "md5", "sha1")
        val tempDD: DeployDetails = new DeployDetails.Builder().file(f).targetRepository("MarkTestTarget").artifactPath(s"$f").
          md5(checksums.get("md5")).sha1(checksums.get("sha1")).build()
        log.info(s"ArtifactoryPlugInfo DeployDetails: $tempDD file: ${tempDD.getFile} TargetRepo: ${tempDD.getTargetRepository}" +
          s" ArtfPath: ${tempDD.getArtifactPath} md5: ${tempDD.getMd5} sha1: ${tempDD.getSha1}")
        tempSeqDD :+ tempDD
      }
    }
    tempSeqDD
  }


  def publish(log: sbt.Logger, configuration: ArtifactoryClientConfiguration, modules: Seq[ArtifactoryModule]): Unit = {
    // Publish
    log.info(s"BuildInfo: Publishing ${modules.map (_.module.getId) mkString ", "}")
  }

}

case class ArtifactoryModule(
                              module: Module,
                              deployableFiles: Seq[DeployDetails]
                              )

