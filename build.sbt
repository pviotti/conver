lazy val root = (project in file(".")).

settings(
  name := "conver",
  version := "0.1",
  scalaVersion := "2.11.8"
)

libraryDependencies += "com.assembla.scala-incubator" %% "graph-core" % "1.11.0"
libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test"

libraryDependencies += "com.github.docker-java" % "docker-java" % "3.0.7"

libraryDependencies += "org.apache.zookeeper" % "zookeeper" % "3.4.9"
