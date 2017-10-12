package example.org.nirmalya.experiments

import akka.actor.ActorSystem
import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling.{ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry, LoggingMagnet}
import akka.stream.{ActorMaterializer, Materializer}
import org.json4s.{DefaultFormats, Formats, ShortTypeHints, native}

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.redis.RedisClient
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.Duration
import collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams._
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.RecordingStatus



/**
  * Created by nirmalya on 19/6/17.
  */
object GameSessionRecordingServer {

  implicit val underlyingActorSystem = ActorSystem("GameSessionRecording")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = underlyingActorSystem.dispatcher


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

  implicit val askTimeOutDuration:Timeout =
    Duration(
      underlyingActorSystem.settings.config.
        getConfig("GameSession.maxResponseTimeLimit").
        getString("duration").
        toInt,
      "seconds")

  def start(args: Array[String]) {

    val logger = Logging(underlyingActorSystem, getClass)


   /* // This one will only log rejections
    val rejectionLogger: HttpRequest ⇒ RouteResult ⇒ Option[LogEntry] = req ⇒ {
      case Rejected(rejections) ⇒ Some(LogEntry(s"Request: $req\nwas rejected with rejections:\n$rejections",
        Logging.ErrorLevel))
      case _                    ⇒ None
    }
    DebuggingDirectives.logRequestResult(rejectionLogger)*/


   /* def requestMethodAndResponseStatusAsInfo(req: HttpRequest): RouteResult => Option[LogEntry] = {
      case RouteResult.Complete(res) => Some(LogEntry(req.method.name + ": " + res.status, Logging.InfoLevel))
      case _                         => None // no log entries for rejections
    }
    DebuggingDirectives.logRequestResult(requestMethodAndResponseStatusAsInfo _)*/


    val completeRoute = startRoute ~ prepareRoute ~ playRoute ~ endByManagerRoute ~ endRoute
    val route: Route = logRequestResult(Logging.WarningLevel, completeRoute)

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
    else {
      println(s"A Redis instance found at $redisHost:$redisPort, and connected to.")
    }

    Http().bindAndHandle(route, serviceHost, servicePort).map(f => {

      println(s"GameSessionRecordingServer: started at @$serviceHost:$servicePort")
    }). recover {
      case ex: Exception =>
        println(s"Error: GameSessionRecordingServer fails to start @$serviceHost:$servicePort, reason: ${ex.getMessage}")
    }

    // TODO
    // How to stop the Server cleanly?
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
        pathPrefix("start") {
          entity(as[REQStartAGameWith]) { reqStartAGameWith =>
            complete {
              println(s"req: $reqStartAGameWith")
              println("SPOC: " + sessionHandlingSPOC.path)
              (sessionHandlingSPOC ? reqStartAGameWith).mapTo[RESPGameSessionBody]

            }
          }
        }
    }
  }

  def prepareRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
      pathPrefix("prepare") {
        entity(as[REQSetQuizForGameWith]) { reqSetQuizForGameWith: REQSetQuizForGameWith =>
          complete {
            println(s"req: $reqSetQuizForGameWith")
            println("SPOC: " + sessionHandlingSPOC.path)
            (sessionHandlingSPOC ? reqSetQuizForGameWith).mapTo[RESPGameSessionBody]

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
        pathPrefix("play") {
          entity(as[REQPlayAGameWith]) { reqPlayAGameWith =>
            complete {
              println(s"req: $reqPlayAGameWith")
              println("SPOC: " + sessionHandlingSPOC.path)
              (sessionHandlingSPOC ? reqPlayAGameWith).mapTo[RESPGameSessionBody]
            }
          }
        }
    }
  }

  def playClipRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
        pathPrefix("playClip") {
          entity(as[REQPlayAClipWith]) { reqPlayAClipWith =>
            complete {
              println(s"req: $reqPlayAClipWith")
              println("SPOC: " + sessionHandlingSPOC.path)
              (sessionHandlingSPOC ? reqPlayAClipWith).mapTo[RESPGameSessionBody]
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
        pathPrefix("pause") {
          entity(as[REQPauseAGameWith]) { reqPauseAGameWith =>
            complete {
              println(s"req: $reqPauseAGameWith")
              (sessionHandlingSPOC ? reqPauseAGameWith).mapTo[RESPGameSessionBody]
            }
          }
        }
    }
  }

  def endByManagerRoute(implicit mat: Materializer) = {
    import akka.http.scaladsl.server.Directives._
    import Json4sSupport._

    implicit val serialization = native.Serialization
    implicit val formats       = DefaultFormats

    post {
      pathPrefix("endByManager") {
        entity(as[REQEndAGameByManagerWith]) { reqEndedByManagerWith =>
          complete {
            println(s"req: $reqEndedByManagerWith")
            (sessionHandlingSPOC ? reqEndedByManagerWith).mapTo[RESPGameSessionBody]
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
        pathPrefix("end") {
          entity(as[REQEndAGameWith]) { reqEndAGameWith =>
            complete {
              println(s"req: $reqEndAGameWith")
              (sessionHandlingSPOC ? reqEndAGameWith).mapTo[RESPGameSessionBody]
            }
          }
        }
    }
  }

  def entityAsString(entity: HttpEntity)
                    (implicit m: Materializer, ex: ExecutionContext): Future[String] = {

    entity.dataBytes
      .map(_.decodeString(entity.contentType.value))
      .runWith(Sink.head)
  }

  def logRequestResult(level: LogLevel, route: Route)
                      (implicit m: Materializer, ex: ExecutionContext) = {
    def myLoggingFunction(logger: LoggingAdapter)(req: HttpRequest)(res: Any): Unit = {
      val entry = res match {
        case Complete(resp) =>
          println(s"** ${req.method} ${req.uri}: ${resp.status} **")
          entityAsString(resp.entity).map(data ⇒ (s"${req.method} ${req.uri}: ${resp.status} \n entity: $data", level))
        case Rejected(reason) =>
          Future.successful(s"Request: ${req._4}, Rejected Reason: ${reason.head}", level)
        case other =>
          Future.successful((s"$other", level))
      }
      entry.map(x => println(x._1))
    }

    println(s"**  setting up debugging directive **")

    DebuggingDirectives.logRequestResult(LoggingMagnet(log => myLoggingFunction(log)))(route)
  }
}
