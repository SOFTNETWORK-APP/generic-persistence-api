import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-core"

resolvers += Resolver.bintrayRepo("dnvriend", "maven")

val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % Versions.akka,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-slf4j" % Versions.akka
)

val akkaPersistence: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akka
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

libraryDependencies ++= Seq(
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"  excludeAll ExclusionRule(organization = "com.typesafe.akka")
) ++ akka ++ akkaPersistence ++ kryo ++ chill ++ logback
