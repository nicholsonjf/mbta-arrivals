import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11"

  val AkkaVersion = "2.7.0"
  val AkkaHttpVersion = "10.4.0"
  lazy val core = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-sse" % "5.0.0",
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
  )
}
