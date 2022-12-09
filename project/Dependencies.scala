import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11"

  val AkkaVersion = "2.7.0"
  val AkkaHttpVersion = "10.4.0"
  val pureconfigVersion = "0.15.0"
  lazy val core = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-sse" % "5.0.0",
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.github.pureconfig" %% "pureconfig" % pureconfigVersion,
    "io.spray" %%  "spray-json" % "1.3.6",
    "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "5.0.0",
    "mysql" % "mysql-connector-java" % "6.0.6"
  )
}
