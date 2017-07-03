package example.org.nirmalya.experiments

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling.{ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import org.json4s.{DefaultFormats, Formats, ShortTypeHints, native}

import scala.concurrent.Future
import akka.pattern._
import akka.util.Timeout
import com.redis.RedisClient
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.Duration
import collection.JavaConversions._
import scala.util.{Failure, Success, Try}

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{REQEndAGameWith, REQPauseAGameWith, REQPlayAGameWith, REQStartAGameWith}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.RecordingStatus



/**
  * Created by nirmalya on 19/6/17.
  */
object GameSessionRecordingServer {

  implicit val underlyingActorSystem = ActorSystem("GameSessionRecording")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = underlyingActorSystem.dispatcher
  implicit val askTimeOutDuration:Timeout = Duration(3, "seconds")

  val config = ConfigFactory.load()

  val gameSessionCompletionSubscriberEndpoints =
    config.getConfig("GameSession.externalServices").getStringList("completionSubscribers").toList

  val gameSessionCompletionEmitter =
    underlyingActorSystem
    .actorOf(
      GameSessionCompletionEmitterActor(gameSessionCompletionSubscriberEndpoints),"EmitterOnFinishingGameSession")

  val sessionHandlingSPOC =
    underlyingActorSystem
    .actorOf(GameSessionSPOCActor(gameSessionCompletionEmitter), "GameSessionSPOC")

  def main(args: Array[String]) {

    val special = Logging(underlyingActorSystem, "SpecialRoutes")

    val route: Route = startRoute ~ playRoute ~ pauseRoute ~ endRoute



    val (serviceHost, servicePort) = (

        config.getConfig("GameSession.availableAt").getString("host"),
        config.getConfig("GameSession.availableAt").getInt("port")

    )

    val (redisHost, redisPort) = (

      config.getConfig("GameSession.redisEndPoint").getString("host"),
      config.getConfig("GameSession.redisEndPoint").getInt("port")
    )

    if (!isRedisReachable(redisHost,redisPort)) {

      println(s"Cannot reach a redis node at $redisHost:$redisPort")
      println("GamesSessionRecordingServer cannot start! Connectivity to a REDIS instance is mandatory.")

      System.exit(-1)
    }

    val bindingFuture: Future[ServerBinding] =
      Http().bindAndHandle(route, serviceHost, servicePort)

    bindingFuture.failed.foreach { ex =>
      println(ex, "Failed to bind to {}:{}!", serviceHost, servicePort)
    }
  }

  private def isRedisReachable(redisHost: String,redisPort: Int): Boolean = {

    val redisClient =
      Try {
        new RedisClient(redisHost, redisPort)
      } match {

        case Success(e)   => Some(e)
        case Failure(ex)  =>
          println(s"Exception while connecting to redis, ${ex.getMessage}")
          None
      }

    if (redisClient.isEmpty)
         false
    else {
         val redisResponse= redisClient.get.ping.getOrElse("NoPONG")
         redisClient.get.disconnect
         // REDIS is expected to respond with a PONG when 'pinged'.
         if (redisResponse == "NoPong") false else true
    }

  }

  def startRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    // TODO: I still don't know how to log the HTTP Requests and Responses, by using Directives!
    post {
      logRequest("StartRequest") {
        pathPrefix("start") {
          entity(as[REQStartAGameWith]) { reqStartAGameWith =>
            complete {
              println(s"req: $reqStartAGameWith")
              println("SPOC: " + sessionHandlingSPOC.path)
              (sessionHandlingSPOC ? reqStartAGameWith).mapTo[RecordingStatus]

            }
          }
        }
      }
    }
  }

  def playRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
      logRequest("StartRequest") {
        pathPrefix("play") {
          entity(as[REQPlayAGameWith]) { reqPlayAGameWith =>
            complete {
              println(s"req: $reqPlayAGameWith")
              println("SPOC: " + sessionHandlingSPOC.path)
              (sessionHandlingSPOC ? reqPlayAGameWith).mapTo[RecordingStatus]
            }
          }
        }
      }
    }
  }

  def pauseRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
      logRequest("StartRequest") {
        pathPrefix("pause") {
          entity(as[REQPauseAGameWith]) { reqPauseAGameWith =>
            complete {
              println(s"req: $reqPauseAGameWith")
              (sessionHandlingSPOC ? reqPauseAGameWith).mapTo[RecordingStatus]
            }
          }
        }
      }
    }
  }

  def endRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
      logRequest("StartRequest") {
        pathPrefix("end") {
          entity(as[REQEndAGameWith]) { reqEndAGameWith =>
            complete {
              println(s"req: $reqEndAGameWith")
              (sessionHandlingSPOC ? reqEndAGameWith).mapTo[RecordingStatus]
            }
          }
        }
      }
    }
  }
}
