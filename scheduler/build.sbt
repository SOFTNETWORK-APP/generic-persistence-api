Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-scheduler"

libraryDependencies ++= Seq(
  "com.markatta" %% "akron" % "1.2" excludeAll(ExclusionRule(organization = "com.typesafe.akka"), ExclusionRule(organization = "org.scala-lang.modules")),
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.5"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
