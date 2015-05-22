enablePlugins(GatlingPlugin)

scalaVersion := "2.11.6"

scalacOptions := Seq(
  "-encoding", "UTF-8", "-target:jvm-1.7", "-deprecation",
  "-feature", "-unchecked", "-language:implicitConversions", "-language:postfixOps")

val gatlingVersion = "2.2.0-M3"

libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion
libraryDependencies += "io.gatling"            % "gatling-test-framework"    % gatlingVersion
libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.5.1"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.3"
libraryDependencies += "jaxen" % "jaxen" % "1.1.6"
libraryDependencies += "com.jsuereth" %% "scala-arm" % "1.4"
libraryDependencies += "com.typesafe.scala-logging" % "scala-logging-slf4j_2.11" % "2.1.2"
libraryDependencies += "com.github.kxbmap" %% "configs" % "0.2.4"
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "3.0.0-SNAP4" % "test"
libraryDependencies += "pl.project13.scala" %% "rainbow" % "0.2"
