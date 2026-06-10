import app.softnetwork.*

/////////////////////////////////
// Defaults
/////////////////////////////////

lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.16"
lazy val javacCompilerVersion = "17"
lazy val scalacCompilerOptions = Seq(
  "-deprecation",
  "-feature"
)

ThisBuild / organization := "app.softnetwork"

name := "generic-persistence-api"

ThisBuild / version := "0.8.6"

lazy val moduleSettings = Seq(
  crossScalaVersions := Seq(scala212, scala213),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => scalacCompilerOptions :+ "-Ypartial-unification"
      case Some((2, 13)) => scalacCompilerOptions :+ s"-release:$javacCompilerVersion"
      case _             => Seq.empty
    }
  }
)

ThisBuild / javacOptions ++= Seq("-source", javacCompilerVersion, "-target", javacCompilerVersion)

ThisBuild / scalaVersion := scala212

//ThisBuild / versionScheme := Some("early-semver")

ThisBuild / resolvers ++= Seq(
  "Softnetwork Server" at "https://softnetwork.jfrog.io/artifactory/releases/",
  "Maven Central Server" at "https://repo1.maven.org/maven2",
  "Typesafe Server" at "https://repo.typesafe.com/typesafe/releases"
)

ThisBuild / libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
)

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

ThisBuild / dependencyOverrides ++= Seq(
  "com.github.jnr" % "jnr-ffi" % "2.2.17",
  "com.github.jnr" % "jffi"    % "1.3.13" classifier "native",
  "org.lmdbjava" % "lmdbjava" % "0.9.1" exclude("org.slf4j", "slf4j-api"),
)

ThisBuild / javaOptions ++= Seq(
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens=java.base/java.math=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/java.text=ALL-UNNAMED",
  "--add-opens=java.base/java.time=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)

ThisBuild / Test / fork := true

Test / javaOptions ++= javaOptions.value

Test / parallelExecution := false

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )

lazy val commonTestkit = project.in(file("common/testkit"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val core = project.in(file("core"))
  .configs(IntegrationTest)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    Defaults.itSettings,
    app.softnetwork.Info.infoSettings,
    moduleSettings
  )
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val coreTestkit = project.in(file("core/testkit"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val server = project.in(file("server"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val serverTestkit = project.in(file("server/testkit"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val sessionCommon = project.in(file("session/common"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )

lazy val sessionCore = project.in(file("session/core"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    sessionCommon % "compile->compile;test->test;it->it"
  )

lazy val sessionTestkit = project.in(file("session/testkit"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    sessionCore % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "compile->compile;test->test;it->it"
  )

lazy val jdbc = project.in(file("jdbc"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val jdbcTestkit = project.in(file("jdbc/testkit"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val cassandra = project.in(file("cassandra"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val counter = project.in(file("counter"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val kv = project.in(file("kv"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    moduleSettings
  )
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
//    cassandra,
    counter,
    kv,
    server,
    serverTestkit,
    sessionCommon,
    sessionCore,
    sessionTestkit
  )
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    Publish.noPublishSettings,
    crossScalaVersions := Nil
  )

Test / envVars := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
