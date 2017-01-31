lazy val root = (project in file(".")).

settings(
  name := "conver",
  version := "0.1",
  scalaVersion := "2.11.8"
)

libraryDependencies += "org.scala-graph" %% "graph-core" % "1.11.4"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "com.github.docker-java" % "docker-java" % "3.0.7"

libraryDependencies += "org.apache.zookeeper" % "zookeeper" % "3.4.9"

// The following dependency has been added because of guava dependecy of docker-java: 
// http://stackoverflow.com/questions/13162671/missing-dependency-class-javax-annotation-nullable
// http://stackoverflow.com/questions/10007994/why-do-i-need-jsr305-to-use-guava-in-scala
libraryDependencies += "com.google.code.findbugs" % "jsr305" % "3.0.1" % "compile"
