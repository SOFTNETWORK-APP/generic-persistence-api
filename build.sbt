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

ThisBuild / version := "0.1.6.17"

ThisBuild / scalaVersion := "2.12.11"

ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature")

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

val pbSettings = Seq(
  Compile / PB.targets := Seq(
    scalapb.gen() -> crossTarget.value / "protobuf_managed/main"
  )
)

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)

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

lazy val schema = project.in(file("jdbc/schema"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
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

lazy val akkaJdbc = project.in(file("akka-jdbc"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )

lazy val counter = project.in(file("counter"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val scheduler = project.in(file("scheduler"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val session = project.in(file("session"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val notification = project.in(file("notification"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )

lazy val elasticTestkit = project.in(file("elastic/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val elastic = project.in(file("elastic"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )
  .dependsOn(
    elasticTestkit % "test->test;it->it"
  )

lazy val server = project.in(file("server"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
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

lazy val sequence = project.in(file("sequence"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val auth = project.in(file("auth"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, pbSettings)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notification % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val resource = project.in(file("resource"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val payment = project.in(file("payment"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    auth % "compile->compile;test->test;it->it"
  )

lazy val root = project.in(file("."))
  .aggregate(
    common,
    commonTestkit,
    core,
    coreTestkit,
    jdbc,
    schema,
    jdbcTestkit,
    akkaJdbc,
    counter,
    scheduler,
    session,
    notification,
    elasticTestkit,
    elastic,
    server,
    serverTestkit,
    sequence,
    auth,
    resource,
    payment
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

envVars in Test := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
