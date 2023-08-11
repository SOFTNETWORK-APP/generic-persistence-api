Test / parallelExecution := false

organization := "app.softnetwork.api"

name := "generic-server-api-testkit"

val akkaHttpTestkit: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp
)

libraryDependencies ++= akkaHttpTestkit
