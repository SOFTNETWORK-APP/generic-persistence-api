import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-core-testkit"

val akkaTestKit = Seq(
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka
)

libraryDependencies ++= akkaTestKit
