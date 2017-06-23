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
import org.json4s.native.Serialization
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{REQPauseAGameWith, REQPlayAGameWith, REQStartAGameWith}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.RecordingStatus

import scala.concurrent.Future
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration.Duration

/**
  * Created by nirmalya on 19/6/17.
  */
object GameSessionRecordingServer {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val askTimeOutDuration:Timeout = Duration(3, "seconds")

  val sessionHandlingSPOC = system.actorOf(GameSessionSPOCActor.props, "SPOC")

  def main(args: Array[String]) {

    val special = Logging(system, "SpecialRoutes")

    val route: Route = startRoute ~ playRoute ~ pauseRoute ~ endRoute

    val (host, port) = ("localhost", 9090)
    val bindingFuture: Future[ServerBinding] =
      Http().bindAndHandle(route, host, port)

    bindingFuture.failed.foreach { ex =>
      println(ex, "Failed to bind to {}:{}!", host, port)
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
}
