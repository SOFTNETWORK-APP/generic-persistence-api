import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.api"

name := "generic-server-api"

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "de.heikoseeberger" %% "akka-http-json4s" % Versions.akkaHttpJson4s,
  "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test
)

libraryDependencies ++= akkaHttp
