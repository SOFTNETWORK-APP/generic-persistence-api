organization := "app.softnetwork.persistence"

name := "persistence-payment"

libraryDependencies ++= Seq(
  "commons-validator" % "commons-validator" % "1.6",
  // mangopay
  "com.mangopay" % "mangopay2-java-sdk" % "2.25.1",
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.5"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
