parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-scheduler"

libraryDependencies ++= Seq(
  "com.markatta" %% "akron" % "1.2" excludeAll(ExclusionRule(organization = "com.typesafe.akka"), ExclusionRule(organization = "org.scala-lang.modules")),
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.3"
)

unmanagedResourceDirectories in Compile += baseDirectory.value / "src/main/protobuf"
