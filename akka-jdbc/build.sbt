import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "akka-persistence-jdbc"

val akkaPersistenceJdbc = Seq(
  "com.lightbend.akka" %% "akka-persistence-jdbc" % Versions.lightbendAkkaPersistenceJdbc,
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.slick" %% "slick" % Versions.slick,
  "com.typesafe.slick" %% "slick-hikaricp" % Versions.slick
)

libraryDependencies ++= akkaPersistenceJdbc

excludeDependencies ++= Seq(
  ExclusionRule(organization = "com.github.dnvriend", name="akka-persistence-jdbc")
) 
