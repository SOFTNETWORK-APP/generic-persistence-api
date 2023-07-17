import sbt.Resolver
import Common._
import app.softnetwork.sbt.build._

/////////////////////////////////
// Defaults
/////////////////////////////////

app.softnetwork.sbt.build.Publication.settings

/////////////////////////////////
// Useful aliases
/////////////////////////////////

addCommandAlias("cd", "project") // navigate the projects

addCommandAlias("cc", ";clean;compile") // clean and compile

addCommandAlias("pl", ";clean;publishLocal") // clean and publish locally

addCommandAlias("pr", ";clean;publish") // clean and publish globally

addCommandAlias("pld", ";clean;local:publishLocal;dockerComposeUp") // clean and publish/launch the docker environment

addCommandAlias("dct", ";dockerComposeTest") // navigate the projects

ThisBuild / shellPrompt := prompt

ThisBuild / organization := "app.softnetwork"

name := "generic-persistence-api"

ThisBuild / version := "0.3.2.4"

ThisBuild / scalaVersion := "2.12.11"

ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-target:jvm-1.8", "-Ypartial-unification")

ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

ThisBuild / resolvers ++= Seq(
  "Softnetwork Server" at "https://softnetwork.jfrog.io/artifactory/releases/",
  "Maven Central Server" at "https://repo1.maven.org/maven2",
  "Typesafe Server" at "https://repo.typesafe.com/typesafe/releases"
)

ThisBuild / libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
)

Test / parallelExecution := false

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)

lazy val commonTestkit = project.in(file("common/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val core = project.in(file("core"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val coreTestkit = project.in(file("core/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val server = project.in(file("server"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val serverTestkit = project.in(file("server/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val sessionCommon = project.in(file("session/common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )

lazy val sessionCore = project.in(file("session/core"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    sessionCommon % "compile->compile;test->test;it->it"
  )

lazy val sessionTestkit = project.in(file("session/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    sessionCore % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "compile->compile;test->test;it->it"
  )

lazy val jdbc = project.in(file("jdbc"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val jdbcTestkit = project.in(file("jdbc/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val counter = project.in(file("counter"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val elastic = project.in(file("elastic"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val elasticTestkit = project.in(file("elastic/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    elastic % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    jdbcTestkit % "compile->compile;test->test;it->it"
  )

lazy val kv = project.in(file("kv"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val root = project.in(file("."))
  .aggregate(
    common,
    commonTestkit,
    core,
    coreTestkit,
    jdbc,
    jdbcTestkit,
    counter,
    elastic,
    elasticTestkit,
    kv,
    server,
    serverTestkit,
    sessionCommon,
    sessionCore,
    sessionTestkit
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

Test / envVars := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
