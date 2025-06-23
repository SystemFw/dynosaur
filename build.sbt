import com.typesafe.tools.mima.core.ReversedMissingMethodProblem
import com.typesafe.tools.mima.core.ProblemFilters
Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / tlBaseVersion := "0.7"
ThisBuild / tlCiReleaseBranches := Seq()
ThisBuild / organization := "org.systemfw"
ThisBuild / organizationName := "Fabio Labella"
ThisBuild / developers ++= List(
  tlGitHubDev("SystemFw", "Fabio Labella")
)
ThisBuild / startYear := Some(2020)

val Scala213 = "2.13.16"

ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.6", "2.12.20")
ThisBuild / tlVersionIntroduced := Map("3.0.0" -> "0.3.0")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.head
ThisBuild / initialCommands := """
  |import cats._, data._, syntax.all._
  |import dynosaur._
""".stripMargin

// If debugging tests, it's sometimes useful to disable parallel
// execution and test result buffering:
// ThisBuild / Test / parallelExecution := false
// ThisBuild / Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b")

lazy val root = tlCrossRootProject
  .aggregate(core.js, core.jvm, benchmark)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .settings(
    name := "dynosaur-core",
    scalafmtOnCompile := true,
    libraryDependencies ++= List(
      "org.typelevel" %%% "cats-core" % "2.11.0",
      "org.typelevel" %%% "cats-free" % "2.11.0",
      "org.typelevel" %%% "alleycats-core" % "2.11.0",
      "org.typelevel" %%% "paiges-core" % "0.4.4",
      "org.typelevel" %%% "paiges-cats" % "0.4.4",
      "org.scodec" %%% "scodec-bits" % "1.2.1",
      "org.scalameta" %%% "munit" % "1.1.1" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.1.0" % Test
    ),
    mimaBinaryIssueFilters ++= List(
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "dynosaur.Schema.dynosaur$Schema$$read_"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "dynosaur.Schema.dynosaur$Schema$$read__="
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "dynosaur.Schema.dynosaur$Schema$$write_"
      ),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "dynosaur.Schema.dynosaur$Schema$$write__="
      )
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "dynamodb" % "2.31.68"
    )
  )

lazy val benchmark = project
  .in(file("modules/benchmark"))
  .dependsOn(core.jvm)
  .enablePlugins(JmhPlugin, NoPublishPlugin)
  .disablePlugins(MimaPlugin)

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
    tlFatalWarnings := false
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
      UseRef.Public("peaceiris", "actions-gh-pages", "v4"),
      name = Some(s"Deploy docs"),
      params = Map(
        "publish_dir" -> "./target/website",
        "github_token" -> "${{ secrets.GITHUB_TOKEN }}"
      )
    ),
  scalas = List(Scala213),
  javas = githubWorkflowJavaVersions.value.toList
)
