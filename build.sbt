enablePlugins(GatlingPlugin)

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-encoding", "UTF-8", "-target:jvm-1.7", "-deprecation",
  "-feature", "-unchecked", "-language:implicitConversions", "-language:postfixOps")

val gatlingVersion = "2.2.2"

xerial.sbt.Sonatype.sonatypeRootSettings

// Maven Publishing
// http://www.scala-sbt.org/0.13/docs/Using-Sonatype.html

publishMavenStyle := true
// just run sbt publish; (or experiment with sbt publishLocaly)
publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository")))

version := "0.7-SNAPSHOT"
organization := "sc.ala"
name := "gatling-amqp"
description := "Gatling AMQP support"
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
          <id>LuboVarga</id>
          <name>Ľubomír Varga</name>
          <url>https://github.com/LuboVarga</url>
        </developer>
      </developers>
      <scm>
        <url>https://github.com/LuboVarga/gatling-amqp</url>
        <connection>scm:git:git@github.com:LuboVarga/gatling-amqp.git</connection>
      </scm>
)

libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion
libraryDependencies += "io.gatling"            % "gatling-test-framework"    % gatlingVersion
libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.6.5"
libraryDependencies += "pl.project13.scala" %% "rainbow" % "0.2"
