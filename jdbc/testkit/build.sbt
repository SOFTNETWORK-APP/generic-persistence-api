import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc-testkit"

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "com.fasterxml.jackson.dataformat"),
  ExclusionRule(organization = "com.fasterxml.jackson.datatype"),
  ExclusionRule(organization = "com.fasterxml.jackson.module")
)

libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "2.1.210",
  "org.testcontainers" % "postgresql" % Versions.testContainers excludeAll(jacksonExclusions:_*),
  "org.testcontainers" % "mysql" % Versions.testContainers excludeAll(jacksonExclusions:_*)
)