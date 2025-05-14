import com.typesafe.tools.mima.core.ReversedMissingMethodProblem
import com.typesafe.tools.mima.core.ProblemFilters

ThisBuild / tlBaseVersion := "0.3"
ThisBuild / organization := "org.systemfw"
ThisBuild / organizationName := "SystemFw"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / homepage := Some(url("https://github.com/SystemFw/dynosaur"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/SystemFw/dynosaur"),
    "git@github.com:SystemFw/dynosaur.git"
  )
)
ThisBuild / developers := List(
  "SystemFw" -> "Fabio Labella"
).map { case (username, fullname) => tlGitHubDev(username, fullname) }
ThisBuild / startYear := Some(2020)

val Scala213 = "2.13.10"
val scala3 = "3.3.6"

ThisBuild / crossScalaVersions := Seq(Scala213, scala3, "2.12.14")
ThisBuild / tlVersionIntroduced := Map("3.0.0" -> "0.3.0")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.head

ThisBuild / initialCommands := """
                                 |import cats._, data._, syntax.all._
                                 |import dynosaur._
""".stripMargin

lazy val root = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .aggregate(core.js, core.jvm, benchmark, docs)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .settings(
    name := "dynosaur-core",
    scalafmtOnCompile := true,
    libraryDependencies ++= List(
      "org.typelevel" %%% "cats-core" % "2.10.0",
      "org.typelevel" %%% "cats-free" % "2.10.0",
      "org.typelevel" %%% "alleycats-core" % "2.10.0",
      "org.typelevel" %%% "paiges-core" % "0.4.3",
      "org.typelevel" %%% "paiges-cats" % "0.4.3",
      "org.scodec" %%% "scodec-bits" % "1.1.38",
      "org.scalameta" %%% "munit" % "0.7.29" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "0.7.29" % Test
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
      "software.amazon.awssdk" % "dynamodb" % "2.31.42"
    )
  )
  .jsSettings(
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val benchmark = project
  .in(file("modules/benchmark"))
  .dependsOn(core.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
  )

lazy val jsdocs = project
  .in(file("jsdocs"))
  .dependsOn(core.js)
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0"
  )
  .enablePlugins(ScalaJSPlugin, NoPublishPlugin)

lazy val docs = project
  .in(file("mdoc"))
  .dependsOn(core.jvm)
  .settings(
    name := "dynosaur-docs",
    mdocIn := file("docs"),
    mdocVariables := Map(
      "version" -> version.value,
      "scalaVersions" -> crossScalaVersions.value
        .map(v => s"- **$v**")
        .mkString("\n"),
      "GH_USER" -> "SystemFw",
      "GH_REPO" -> "dynosaur"
    ),
    tlFatalWarnings := false
  )
