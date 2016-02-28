name := """fashion-film"""

organization := "fashion-film"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.0",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.7.0",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.0",
  "com.typesafe.play.modules" %% "play-modules-redis" % "2.4.0"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator


// fork in run := true
