app.softnetwork.sbt.build.Publication.settings

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-auth"

libraryDependencies ++= Seq(
  "org.passay" % "passay" % "1.3.1"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
