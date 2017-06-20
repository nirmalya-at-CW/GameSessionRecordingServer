import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "Redissson-experiments",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "EventReaderFromRedis",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "net.debasishg" %% "redisclient" % "3.4",

    libraryDependencies += "org.json4s" % "json4s-native_2.11" % "3.5.2",

    libraryDependencies += "org.json4s" % "json4s-ext_2.11" % "3.5.2",


    libraryDependencies += "com.typesafe.play" % "play-json_2.11" % "2.5.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.2",
    libraryDependencies +="com.typesafe.akka" %% "akka-testkit" % "2.5.2",
    libraryDependencies +="com.typesafe.akka" %% "akka-stream" % "2.5.2",
    libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    libraryDependencies +="com.typesafe.akka" %% "akka-stream-testkit" % "2.5.2",
    libraryDependencies +="org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies +="junit" % "junit" % "4.12",
    libraryDependencies +="com.novocode" % "junit-interface" % "0.11",
    libraryDependencies +="org.hamcrest" % "hamcrest-all" % "1.3",
    libraryDependencies +="com.mashape.unirest" % "unirest-java" % "1.4.9"


  )
