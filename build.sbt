name := "rehearsal"
version := "0.1"
scalaVersion := "2.11.7"
scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Xfatal-warnings"
)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Typesafe Repository" at "http://repo.akka.io/snapshots/",
  "PLASMA" at "https://dl.bintray.com/plasma-umass/maven"
)

libraryDependencies ++= Seq(
  "com.regblanc" %% "scala-smtlib" % "0.2",
  "edu.umass.cs" %% "config" % "1.0.1",
  "edu.umass.cs" %% "extras" % "1.3.0",
  "edu.umass.cs" %% "smtlib" % "1.0.1",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "com.assembla.scala-incubator" %% "graph-core" % "1.9.0",
  "com.assembla.scala-incubator" %% "graph-dot" % "1.9.0",
  "io.spray" %%  "spray-json" % "1.3.2",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "org.scalaj" %% "scalaj-http" % "1.1.6")

parallelExecution in Test := false

// Logging dependencies
libraryDependencies ++=
  Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "org.slf4j" % "slf4j-simple" % "1.7.12")

testOptions in Test += Tests.Argument("-oD")

assemblyJarName in assembly := "rehearsal.jar"
test in assembly := {}
