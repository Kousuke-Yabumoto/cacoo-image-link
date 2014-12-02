name := """cacoo-image-link"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "com.propensive" %% "rapture-core" % "1.0.0",
  "com.propensive" %% "rapture-json-play" % "1.0.8"
)
