enablePlugins(GatlingPlugin)

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-encoding", "UTF-8", "-target:jvm-1.7", "-deprecation",
  "-feature", "-unchecked", "-language:implicitConversions", "-language:postfixOps")

val gatlingVersion = "2.2.5"

xerial.sbt.Sonatype.sonatypeRootSettings

publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

version := "0.6-SNAPSHOT"
organization := "sc.ala"
name := "gatling-amqp"
description := "Gatling AMQP unofficial support"
homepage := Some(url("https://github.com/maiha/gatling-amqp"))
licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

pomExtra := (
     <developers>
        <developer>
          <id>maiha</id>
          <name>Kazunori Nishi</name>
          <url>https://github.com/maiha</url>
        </developer>
       <developer>
         <id>dieselr</id>
         <name>DieselR</name>
         <url>https://github.com/dieselr</url>
       </developer>
      </developers>
      <scm>
        <url>https://github.com/dieselr/gatling-amqp</url>
        <connection>scm:git:git@github.com:dieselr/gatling-amqp.git</connection>
      </scm>
)

libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion
libraryDependencies += "io.gatling"            % "gatling-test-framework"    % gatlingVersion
libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.5.3"
libraryDependencies += "pl.project13.scala" %% "rainbow" % "0.2"
