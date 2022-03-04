import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.api"

name := "generic-server-api"

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp,
  "de.heikoseeberger" %% "akka-http-json4s" % Versions.akkaHttpJson4s
)

libraryDependencies ++= akkaHttp
