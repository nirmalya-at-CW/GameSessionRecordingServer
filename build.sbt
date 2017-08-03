import Dependencies._

lazy val root = (project in file(".")).
  enablePlugins(JavaAppPackaging).
  settings(
    inThisBuild(List(
      organization := "Redissson-experiments",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT",
      resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hseeberger", "maven"))
    )),
    name := "GameSessionRecordingServer",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "net.debasishg" %% "redisclient" % "3.4",

    libraryDependencies +=  "org.json4s" % "json4s-native_2.11" % "3.5.2",
    //libraryDependencies += "org.javalite" % "javalite-common" % "1.4.12",
    libraryDependencies +=  "com.mashape.unirest" % "unirest-java" % "1.4.9",
    libraryDependencies +=  "org.json4s" % "json4s-ext_2.11" % "3.5.2",
    libraryDependencies +=  "de.heikoseeberger" %% "akka-http-json4s" % "1.16.0",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.9",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.9",
    libraryDependencies += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.9",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-http" % "10.0.7",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.8" % "test",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-actor" % "2.5.2",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-testkit" % "2.5.2" % "test",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-stream" % "2.5.2",
    libraryDependencies +=  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.2",
    libraryDependencies +=  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies +=  "junit" % "junit" % "4.12" % "test",
    libraryDependencies +=  "com.novocode" % "junit-interface" % "0.11" % "test",
    libraryDependencies +=  "org.hamcrest" % "hamcrest-all" % "1.3",
    libraryDependencies +=  "com.mashape.unirest" % "unirest-java" % "1.4.9"


  )

packageName in Docker := "huddle-gamesession-server"
