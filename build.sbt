name := """SipUploader"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.5.10" % "provided",
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "jline" % "jline" % "2.14.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")

scalacOptions += "-feature"
scalacOptions += "-deprecation"

fork in run := true