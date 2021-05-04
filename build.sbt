Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / baseVersion := "0.2.0"
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

val Scala213 = "2.13.4"
ThisBuild / spiewakMainBranches := Seq("main")

ThisBuild / crossScalaVersions := Seq(Scala213, "3.0.0-RC2", "2.12.10")
ThisBuild / versionIntroduced := Map("3.0.0-RC1" -> "3.0.0")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.head
ThisBuild / initialCommands := """
  |import cats._, data._, syntax.all._
  |import dynosaur._
""".stripMargin
ThisBuild / testFrameworks += new TestFramework("munit.Framework")

def dep(org: String, prefix: String, version: String)(modules: String*)(
    testModules: String*
) =
  modules.map(m => org %% (prefix ++ m) % version) ++
    testModules.map(m => org %% (prefix ++ m) % version % Test)

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
      dep("org.typelevel", "cats-", "2.6.0")("core", "free")() ++
        dep("org.typelevel", "", "2.6.0")("alleycats-core")() ++
        dep("org.scodec", "scodec-bits", "1.1.26")("")() ++
        dep("org.scalameta", "munit", "0.7.25")()("", "-scalacheck") ++
        dep("org.typelevel", "paiges-", "0.4.1")("core", "cats")() ++
        Seq("software.amazon.awssdk" % "dynamodb" % "2.14.15")
  )

lazy val docs = project
  .in(file("mdoc"))
  .settings(
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
  .dependsOn(core)
  .enablePlugins(MdocPlugin, NoPublishPlugin)

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
    )
)
