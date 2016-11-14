name := """SipUploader"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.5.9",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "jline" % "jline" % "2.14.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test")

scalacOptions += "-feature"
scalacOptions += "-deprecation"

fork in run := true