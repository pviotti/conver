lazy val root = (project in file(".")).

settings(
  name := "conver",
  version := "0.1",
  scalaVersion := "2.11.8"
)

libraryDependencies += "com.assembla.scala-incubator" %% "graph-core" % "1.11.0"
