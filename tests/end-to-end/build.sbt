// import NativePackagerKeys._ //Required for Heroku

// packageArchetype.java_application //Required for Heroku

name := "NetHSM Test"

version := "0.1"

organization  := "com.nitrokey"

scalaVersion  := "2.11.8"

libraryDependencies ++= {
    val sprayVersion = "1.3.3"
    Seq(
    "org.bouncycastle" % "bcprov-jdk16" % "1.46",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback" % "logback-classic" % "1.1.6",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe" % "config" % "1.2.1",
    "com.typesafe.akka" %% "akka-actor" % "2.3.14", // Note: 2.4.1 requires Java 8.
    "io.spray" %% "spray-http" % sprayVersion,
    "io.spray" %% "spray-httpx" % sprayVersion,
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-json" % "1.3.1"
)
}

scalacOptions ++= Seq(
	"-Xlint",
	"-deprecation",
	"-unchecked",
	"-Ywarn-dead-code",
	"-Ywarn-inaccessible"
	//"-Xfatal-warnings"
)

scalacOptions in Test ++= Seq("-Yrangepos") //For Specs2 required

