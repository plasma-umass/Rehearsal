name := "package-listing"
version := "0.1"
organization := "edu.umass.cs"
scalaVersion := "2.11.7"
scalacOptions ++=
 Seq("-deprecation",
     "-unchecked",
     "-feature",
     "-Xfatal-warnings")

libraryDependencies ++=
  Seq("org.postgresql" % "postgresql" % "9.4-1204-jdbc42",
      "org.scalikejdbc" %% "scalikejdbc" % "2.2.9",
      "com.typesafe.akka" %% "akka-actor" % "2.4.0",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.0",
      "io.spray" %% "spray-routing" % "1.3.3",
      "io.spray" %% "spray-can" % "1.3.3",
      "io.spray" %% "spray-http" % "1.3.3",
      "ch.qos.logback" % "logback-classic" % "1.0.9",
      "ch.qos.logback" % "logback-core" % "1.0.9",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0")

