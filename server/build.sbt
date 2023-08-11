Test / parallelExecution := false

organization := "app.softnetwork.api"

name := "generic-server-api"

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-http2-support" % Versions.akkaHttp, // required for akka-grpc
  "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp,
  "de.heikoseeberger" %% "akka-http-json4s" % Versions.akkaHttpJson4s
)

val tapir = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-core" % Versions.tapir,
  "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % Versions.tapir excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  "com.softwaremill.sttp.tapir" %% "tapir-json-spray" % Versions.tapir,
  "com.softwaremill.sttp.tapir" %% "tapir-json-json4s" % Versions.tapir,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % Versions.tapir
)

libraryDependencies ++= akkaHttp ++ tapir
