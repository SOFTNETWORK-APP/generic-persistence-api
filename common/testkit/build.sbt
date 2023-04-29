import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-common-testkit"

val scalatest = Seq(
  "org.scalatest" %% "scalatest" % Versions.scalatest
)

libraryDependencies ++= scalatest
