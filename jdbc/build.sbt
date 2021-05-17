import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc"

val akkaPersistenceJdbc = Seq(
  "com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.akkaPersistenceJdbc excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  "org.postgresql"       % "postgresql"  % Versions.postgresql
)

libraryDependencies ++= akkaPersistenceJdbc

// enable publishing the test jar
publishArtifact in (Test, packageBin) := true

// enable publishing the test API jar
publishArtifact in (Test, packageDoc) := true

// enable publishing the test sources jar
publishArtifact in (Test, packageSrc) := true
