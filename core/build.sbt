import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-core"

resolvers += Resolver.bintrayRepo("dnvriend", "maven")

val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % Versions.akka,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-slf4j" % Versions.akka,
  "com.typesafe.akka" %% "akka-discovery" % Versions.akka
)

val akkaPersistence: Seq[ModuleID] = Seq(
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2" excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akka
)

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-http2-support" % Versions.akkaHttp, // required for akka-grpc
  "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp
)

val akkaManagement: Seq[ModuleID] = Seq(
  // This provides HTTP management endpoints as well as a cluster health check
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % Versions.akkaManagement excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  // This bootstraps the cluster from nodes discovered via the Kubernetes API
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.akkaManagement excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  // This provides a discovery mechanism that queries the Kubernetes API
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % Versions.akkaManagement excludeAll ExclusionRule(organization = "com.typesafe.akka")
)

val kryo = Seq(
  "com.twitter" %% "chill-bijection" % Versions.chill
)

val chill = Seq(
  "com.twitter" % "chill-akka_2.12" % Versions.chill excludeAll ExclusionRule(organization = "com.typesafe.akka")
)

val logback = Seq(
  "ch.qos.logback" % "logback-classic"  % Versions.logback,
  "org.slf4j"      % "log4j-over-slf4j" % Versions.slf4j
)

val akkaTestKit = Seq(
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka % Test
)

libraryDependencies ++= Seq(
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"  excludeAll ExclusionRule(organization = "com.typesafe.akka")
) ++ akka ++ akkaPersistence ++ akkaHttp ++ kryo ++ chill ++ logback ++ akkaTestKit ++ akkaManagement
