lazy val root = (project in file(".")).settings(
  name := "conver",
  version := "0.1",
  scalaVersion := "2.11.8"
)

EclipseKeys.withJavadoc := true

libraryDependencies += "org.scala-graph" %% "graph-core" % "1.11.5"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.3"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % "test"
libraryDependencies += "org.rogach" %% "scallop" % "3.0.1"

libraryDependencies += "com.github.docker-java" % "docker-java" % "3.0.10" exclude ("org.slf4j", "slf4j-log4j12")
// The following dependency has been added because of guava dependecy of docker-java:
// http://stackoverflow.com/questions/13162671/missing-dependency-class-javax-annotation-nullable
// http://stackoverflow.com/questions/10007994/why-do-i-need-jsr305-to-use-guava-in-scala
libraryDependencies += "com.google.code.findbugs" % "jsr305" % "3.0.1" % "compile"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// databases
libraryDependencies += "org.apache.zookeeper" % "zookeeper" % "3.4.9" exclude ("org.slf4j", "slf4j-log4j12")
libraryDependencies += "eu.antidotedb" % "antidote-java-client" % "0.0.6"
