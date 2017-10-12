import Dependencies._

lazy val root = (project in file(".")).
  enablePlugins(JavaAppPackaging).
  enablePlugins(GatlingPlugin).
  settings(
    inThisBuild(List(
      organization := "Codewalla-Nuovo-experiments",
      scalaVersion := "2.11.11",
      version      := "0.1.0-SNAPSHOT",
      resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("hseeberger", "maven"))
    )),
    name := "GameSessionRecordingServer",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "net.debasishg" %% "redisclient" % "3.4",

    libraryDependencies +=  "org.json4s" % "json4s-native_2.11" % "3.5.2",
    libraryDependencies +=  "org.json4s" % "json4s-ext_2.11" % "3.5.2",
    libraryDependencies +=  "de.heikoseeberger" %% "akka-http-json4s" % "1.16.0",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.9",
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.9",
    libraryDependencies += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.9",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-http" % "10.0.7",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.8" % "test",
    libraryDependencies +=  "ch.qos.logback" % "logback-classic" % "1.1.7",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-actor" % "2.5.2",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-testkit" % "2.5.2" % "test",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-stream" % "2.5.2",
    libraryDependencies +=  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    libraryDependencies += "org.jooq" % "jooq" % "3.10.0",
    libraryDependencies += "org.jooq" % "jooq-meta" % "3.10.0",
    libraryDependencies += "org.jooq" % "jooq-codegen" % "3.10.0",
    libraryDependencies += "org.jooq" % "jooq-scala" % "3.9.6",
    libraryDependencies += "org.mariadb.jdbc" % "mariadb-java-client" % "2.1.2",
    libraryDependencies +=  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.2",
    libraryDependencies +=  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    libraryDependencies +=  "junit" % "junit" % "4.12" % "test",
    libraryDependencies +=  "com.novocode" % "junit-interface" % "0.11" % "test",
    libraryDependencies +=  "org.hamcrest" % "hamcrest-all" % "1.3",
    libraryDependencies +=  "com.mashape.unirest" % "unirest-java" % "1.4.9",
    libraryDependencies +=  "io.kamon" % "kamon-core_2.11" % "0.6.7",
    libraryDependencies +=  "io.kamon" % "kamon-statsd_2.11" % "0.6.7",
    //libraryDependencies +=  "io.kamon" % "kamon-log-reporter_2.11" % "0.6.8",
    libraryDependencies +=  "io.kamon" % "kamon-akka-2.4_2.11" % "0.6.8",
    libraryDependencies +=  "io.kamon" % "kamon-system-metrics_2.11" % "0.6.7"

  )

aspectjSettings

javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj

fork in run := true

packageName in Docker := "huddle-gamesession-server"
