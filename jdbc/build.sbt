import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc"

val akkaPersistenceJdbc = Seq(
  "com.lightbend.akka" %% "akka-persistence-jdbc" % Versions.lightbendAkkaPersistenceJdbc % "provided",
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.slick" %% "slick" % Versions.slick,
  "com.typesafe.slick" %% "slick-hikaricp" % Versions.slick,
  "org.postgresql"       % "postgresql"  % Versions.postgresql
)

libraryDependencies ++= akkaPersistenceJdbc
