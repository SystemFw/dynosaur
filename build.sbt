Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / baseVersion := "0.1.0"
ThisBuild / organization := "org.systemfw"
ThisBuild / publishGithubUser := "SystemFw"
ThisBuild / publishFullName := "Fabio Labella"

replaceCommandAlias("ci","; project /; headerCheckAll; clean; testIfRelevant; docs/mdoc; mimaReportBinaryIssuesIfRelevant")

// sbt-sonatype wants these in Global
Global / homepage := Some(url("https://github.com/SystemFw/dynosaur"))
Global / scmInfo := Some(ScmInfo(url("https://github.com/SystemFw/dynosaur"), "git@github.com:SystemFw/dynosaur.git"))
Global / excludeLintKeys += scmInfo
ThisBuild / spiewakMainBranches := Seq("main")

ThisBuild / crossScalaVersions := Seq("3.0.0-M2", "2.12.10", "2.13.4")
ThisBuild / versionIntroduced := Map("3.0.0-M2" -> "3.0.0")

ThisBuild / initialCommands := """
  |import cats._, data._, syntax.all._
  |import cats.effect._, concurrent._, implicits._
  |import fs2._
  |import fs2.concurrent._
  |import scala.concurrent.duration._
  |import dynosaur
""".stripMargin

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin, SonatypeCiReleasePlugin)
  .aggregate(core)


lazy val core = project
  .in(file("modules/core"))
  .settings(
    name := "dynosaur-core",
    scalafmtOnCompile := true,
    libraryDependencies ++=
      dep("org.typelevel", "cats-", "2.3.0")("core")() ++
      dep("org.typelevel", "cats-effect", "2.3.0")("")("-laws") ++
      dep("co.fs2", "fs2-", "2.4.6")("core", "io")() ++
      dep("org.scalameta", "munit", "0.7.19")()("", "-scalacheck") ++
      dep("org.typelevel", "", "0.11.0")()("munit-cats-effect-2") ++
      dep("org.typelevel",  "scalacheck-effect", "0.6.0")()("", "-munit")
  )

lazy val docs = project
  .in(file("docs"))
  .settings(
    mdocIn := file("modules/docs"),
    mdocOut := file("docs"),
    mdocVariables := Map("VERSION" -> version.value),
    githubWorkflowArtifactUpload := false
  ).dependsOn(core)
   .enablePlugins(MdocPlugin, NoPublishPlugin)

def dep(org: String, prefix: String, version: String)(modules: String*)(testModules: String*) =
  modules.map(m => org %% (prefix ++ m) % version) ++
   testModules.map(m => org %% (prefix ++ m) % version % Test)

