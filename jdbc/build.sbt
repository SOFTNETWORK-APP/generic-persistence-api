import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc"

val akkaPersistenceJdbc = Seq(
  "com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.akkaPersistenceJdbc excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  "org.postgresql"       % "postgresql"  % Versions.postgresql
)

libraryDependencies ++= akkaPersistenceJdbc
