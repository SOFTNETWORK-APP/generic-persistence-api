import app.softnetwork.sbt.build.Versions

app.softnetwork.sbt.build.Publication.settings

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc-schema"

libraryDependencies ++= Seq(
  "com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.akkaPersistenceJdbc excludeAll ExclusionRule(organization = "com.typesafe.akka")
)
