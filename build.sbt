lazy val fs2Version = "1.0.2"
lazy val catsEffectVersion = "0.10.1"
lazy val catsVersion = "1.5.0"
lazy val awsSdkVersion = "1.11.465"
lazy val scalatestVersion = "3.0.5"
lazy val scalacheckVersion = "1.14.0"
lazy val slf4jVersion = "1.7.25"
lazy val log4jVersion = "2.11.1"
lazy val http4sVersion = "0.20.0-M5"
lazy val commsDockerkitVersion = "1.8.6"
lazy val scalaXmlVersion = "1.1.1"
lazy val circeVersion = "0.11.1"
lazy val scodecBitsVersion = "1.1.9"


lazy val IntegrationTest = config("it") extend Test

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

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
    "http4s",
    "fs2",
    "scala",
  ),
  version ~= (_.replace('+', '-')),
  dynver ~= (_.replace('+', '-'))
)

lazy val root = (project in file("."))
  .aggregate(auth, common, dynamodb, s3)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws",
    inThisBuild(List(
      organization := "com.ovoenergy.comms",
      organizationName := "OVO Energy",
      organizationHomepage := Some(url("https://ovoenergy.com")),
      developers := List(
        Developer("filosganga", "Filippo De Luca", "filippo.deluca@ovoenergy.com", url("https://github.com/filosganga")),
        Developer("laurence-bird", "Laurence Bird", "laurence.bird@ovoenergy.com", url("https://github.com/laurence-bird")),
        Developer("SystemFw", "Fabio Labella", "fabio.labella@ovoenergy.com", url("https://github.com/SystemFw")),
        Developer("ZsoltBalvanyos", "Zsolt Balvanyos", "zsolt.balvanyos@ovoenergy.com", url("https://github.com/ZsoltBalvanyos")),
      ),
      startYear := Some(2018),
      licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/apache-2.0")),
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/ovotech/comms-aws"),
          "scm:git:git@github.com:ovotech/comms-aws.git")
      ),
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
      resolvers ++= Seq(
        Resolver.bintrayRepo("ovotech", "maven")
      ),
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-core" % http4sVersion,
        "org.http4s" %% "http4s-client" % http4sVersion,
        "co.fs2" %% "fs2-core" % fs2Version,
        "co.fs2" %% "fs2-io" % fs2Version,
        "org.slf4j" % "slf4j-api" % slf4jVersion,
      ),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % scalatestVersion,
        "org.scalacheck" %% "scalacheck" % scalacheckVersion,
        "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
        "com.ovoenergy" %% "comms-docker-testkit" % commsDockerkitVersion,
        "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      ).map(_ % s"$Test,$IntegrationTest"),
      scalafmtOnCompile := true,
    )),
  )

lazy val common = (project in file("modules/common"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-common",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion % Optional,
    )
  )


lazy val auth = (project in file("modules/auth"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest")
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-auth",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion % s"$Test,$IntegrationTest",
    )
  )

lazy val s3 = (project in file("modules/s3"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest", auth)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-s3",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-scala-xml" % http4sVersion,
      "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion % Optional,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion % s"$Test,$IntegrationTest",
    )
  )

lazy val dynamodb = (project in file("modules/dynamodb"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest", auth)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-dynamodb",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.scodec" %% "scodec-bits" % scodecBitsVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-literal" % circeVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion % Optional,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion % s"$Test,$IntegrationTest",
    )
  )
