organization := "app.softnetwork.persistence"

name := "persistence-resource"

libraryDependencies ++= Seq(
  //"com.google.cloud" % "google-cloud-storage" % "2.6.1",
  //"com.github.seratch" %% "awscala-s3" % "0.9.+"    
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.1.5"
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
