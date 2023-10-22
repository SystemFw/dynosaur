Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / baseVersion := "0.3.0"
ThisBuild / organization := "org.systemfw"
ThisBuild / publishGithubUser := "SystemFw"
ThisBuild / publishFullName := "Fabio Labella"
ThisBuild / homepage := Some(url("https://github.com/SystemFw/dynosaur"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/SystemFw/dynosaur"),
    "git@github.com:SystemFw/dynosaur.git"
  )
)
ThisBuild / startYear := Some(2020)
Global / excludeLintKeys += scmInfo

val Scala213 = "2.13.10"
ThisBuild / spiewakMainBranches := Seq("main")

ThisBuild / crossScalaVersions := Seq(Scala213, "3.2.2", "2.12.14")
ThisBuild / versionIntroduced := Map("3.0.0" -> "0.3.0")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.head
ThisBuild / initialCommands := """
  |import cats._, data._, syntax.all._
  |import dynosaur._
""".stripMargin

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin, SonatypeCiReleasePlugin)
  .aggregate(core.js, core.jvm)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .settings(
    name := "dynosaur-core",
    scalafmtOnCompile := true,
    libraryDependencies ++= List(
      "org.typelevel" %%% "cats-core" % "2.6.1",
      "org.typelevel" %%% "cats-free" % "2.6.1",
      "org.typelevel" %%% "alleycats-core" % "2.6.1",
      "org.typelevel" %%% "paiges-core" % "0.4.2",
      "org.typelevel" %%% "paiges-cats" % "0.4.2",
      "org.scodec" %%% "scodec-bits" % "1.1.34",
      "org.scalameta" %%% "munit" % "0.7.29" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "0.7.29" % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "dynamodb" % "2.21.5"
    )
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val jsdocs = project
  .dependsOn(core.js)
  .settings(
    githubWorkflowArtifactUpload := false,
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.3.0"
  )
  .enablePlugins(ScalaJSPlugin)

lazy val docs = project
  .in(file("mdoc"))
  .settings(
    mdocJS := Some(jsdocs),
    mdocIn := file("docs"),
    mdocOut := file("target/website"),
    mdocVariables := Map(
      "version" -> version.value,
      "scalaVersions" -> crossScalaVersions.value
        .map(v => s"- **$v**")
        .mkString("\n")
    ),
    githubWorkflowArtifactUpload := false,
    fatalWarningsInCI := false
  )
  .dependsOn(core.jvm)
  .enablePlugins(MdocPlugin, NoPublishPlugin)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

ThisBuild / githubWorkflowBuildPostamble ++= List(
  WorkflowStep.Sbt(
    List("docs/mdoc"),
    cond = Some(s"matrix.scala == '$Scala213'")
  )
)

ThisBuild / githubWorkflowAddedJobs += WorkflowJob(
  id = "docs",
  name = "Deploy docs",
  needs = List("publish"),
  cond = """
  | always() &&
  | needs.build.result == 'success' &&
  | (needs.publish.result == 'success' || github.ref == 'refs/heads/docs-deploy')
  """.stripMargin.trim.linesIterator.mkString.some,
  steps = githubWorkflowGeneratedDownloadSteps.value.toList :+
    WorkflowStep.Use(
      UseRef.Public("peaceiris", "actions-gh-pages", "v3"),
      name = Some(s"Deploy docs"),
      params = Map(
        "publish_dir" -> "./target/website",
        "github_token" -> "${{ secrets.GITHUB_TOKEN }}"
      )
    ),
  scalas = List(Scala213),
  javas = githubWorkflowJavaVersions.value.toList
)
