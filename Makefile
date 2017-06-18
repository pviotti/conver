all:
	sbt compile

test:
	sbt test

clean:
	sbt clean
	
eclipse:
	sbt eclipse

fmt:
	sbt scalafmt test:scalafmt sbt:scalafmt
