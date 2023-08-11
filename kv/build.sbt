Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-kv"

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
