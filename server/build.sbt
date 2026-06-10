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

// Story 13.6 Phase B — record HTTP-route rate+latency into PrometheusRegistry.defaultRegistry.
val prometheus = Seq(
  "io.prometheus" % "prometheus-metrics-core" % Versions.prometheus
)

// Route-level test for the HttpMetrics directive (akka-http-testkit + text exposition to assert
// registry samples). Test-scope only.
val httpMetricsTest = Seq(
  "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test,
  "io.prometheus" % "prometheus-metrics-exposition-textformats" % Versions.prometheus % Test,
  "org.scalatest" %% "scalatest" % Versions.scalatest % Test
)

libraryDependencies ++= akkaHttp ++ tapir ++ prometheus ++ httpMetricsTest
