lazy val root = (project in file("."))
  .aggregate(core, macros)
  .settings(
    inThisBuild(
      commonSettings ++ compilerOptions ++ releaseOptions ++ consoleSettings
    )
  )

lazy val core = (project in file("modules/core"))
  .dependsOn(macros)
  .settings(
    name := "dynosaur-core",
    scalafmtOnCompile := true,
    dependencies
  )
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(
    inConfig(IntegrationTest)(Defaults.itSettings),
    automateHeaderSettings(IntegrationTest)
  )

lazy val macros = (project in file("modules/macros"))
  .settings(
    name := "dynosaur-macros",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val IntegrationTest = config("it") extend Test

lazy val commonSettings = Seq(
  organization := "com.ovoenergy",
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("https://ovoenergy.com")),
  developers := List(
    Developer(
      "SystemFw",
      "Fabio Labella",
      "",
      url("https://github.com/SystemFw")
    ),
    Developer(
      "filosganga",
      "Filippo De Luca",
      "",
      url("https://github.com/filosganga")
    )
  ),
  startYear := Some(2019),
  licenses := Seq(
    "Apache-2.0" -> url("https://opensource.org/licenses/apache-2.0")
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ovotech/dynosaur"),
      "scm:git:git@github.com:ovotech/dynosaur.git"
    )
  )
)

lazy val consoleSettings = Seq(
  initialCommands := s"import dynosaur._",
  scalacOptions in (Compile, console) --= Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint")
)

lazy val compilerOptions = Seq(
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:experimental.macros",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Ywarn-unused-import",
    "-Ywarn-unused",
    "-Ypartial-unification"
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
)

def dep(org: String)(version: String)(modules: String*) =
  Seq(modules: _*) map { name =>
    org %% name % version
  }

lazy val dependencies = {
  val commsAwsVersion = "0.2.15"
  val fs2Version = "1.0.4"
  val catsEffectVersion = "0.10.1"
  val catsVersion = "1.6.0"
  val awsSdkVersion = "1.11.534"
  val scalatestVersion = "3.0.5"
  val scalacheckVersion = "1.14.0"
  val slf4jVersion = "1.7.26"
  val log4jVersion = "2.11.2"
  val http4sVersion = "0.20.0"
  val commsDockerkitVersion = "1.8.6"
  val scalaXmlVersion = "1.1.1"
  val circeVersion = "0.11.1"
  val scodecBitsVersion = "1.1.9"

  val deps = libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-core" % http4sVersion,
    "org.http4s" %% "http4s-client" % http4sVersion,
    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io" % fs2Version,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.scodec" %% "scodec-bits" % scodecBitsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-literal" % circeVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion % Optional,
    "com.ovoenergy.comms" %% "comms-aws-common" % commsAwsVersion,
    "com.ovoenergy.comms" %% "comms-aws-auth" % commsAwsVersion,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

  val testDeps = libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % scalatestVersion,
    "org.scalacheck" %% "scalacheck" % scalacheckVersion,
    "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
    "com.ovoenergy" %% "comms-docker-testkit" % commsDockerkitVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion
  ).map(_ % s"$Test,$IntegrationTest")

  val ovoMaven = resolvers += Resolver.bintrayRepo("ovotech", "maven")

  Seq(deps, testDeps, ovoMaven)
}

lazy val releaseOptions = Seq(
  releaseEarlyWith := BintrayPublisher,
  releaseEarlyEnableSyncToMaven := false,
  releaseEarlyNoGpg := true,
  //  releaseEarlyEnableSyncToMaven := false,
  bintrayOrganization := Some("ovotech"),
  bintrayRepository := "maven",
  bintrayPackageLabels := Seq(
    "aws",
    "cats",
    "cats-effect",
    "dynamoDb",
    "fs2",
    "http4s",
    "scala"
  ),
  version ~= (_.replace('+', '-')),
  dynver ~= (_.replace('+', '-'))
)
