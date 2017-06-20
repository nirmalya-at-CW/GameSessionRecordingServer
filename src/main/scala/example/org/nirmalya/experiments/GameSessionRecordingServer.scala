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
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.REQStartAGameWith
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.RecordingStatus

import scala.concurrent.Future
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration.Duration

/**
  * Created by nirmalya on 19/6/17.
  */
object GameSessionRecordingServer {


  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future foreach in the end
    implicit val executionContext = system.dispatcher

    val sessionHandlingSPOC = system.actorOf(GameSessionSPOCActor.props, "SPOC")



    val special = Logging(system, "SpecialRoutes")


    import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
    import GameSessionHandlingServiceProtocol._
    import GameSessionHandlingServiceProtocol.formats_2
    implicit val serialization = native.Serialization // or native.Serialization
    implicit val formats       = DefaultFormats

    implicit val askTimeOutDuration:Timeout = Duration(3, "seconds")
    def firstToute(implicit mat: Materializer) = {
      import akka.http.scaladsl.server.Directives._
      import Json4sSupport._

      implicit val serialization = native.Serialization
      implicit val formats       = DefaultFormats

        post {
          logRequest("StartRequest") {
            pathPrefix("start") {
              entity(as[REQStartAGameWith]) { reqStartAGameWith =>
                complete {
                  println(s"req: $reqStartAGameWith")
                  (sessionHandlingSPOC ? reqStartAGameWith).mapTo[RecordingStatus]
                  //r
                }
              }
            }
          }
      }
    }

    // let's say the OS won't allow us to bind to 80.
    val (host, port) = ("localhost", 9090)
    val bindingFuture: Future[ServerBinding] =
      Http().bindAndHandle(firstToute, host, port)

    bindingFuture.failed.foreach { ex =>
      println(ex, "Failed to bind to {}:{}!", host, port)
    }
  }

  /*import GameSessionHandlingServiceProtocol._
  val gameStartRout: Route =
    post {
      decodeRequest {
        // unmarshal with in-scope unmarshaller
        entity(as[REQStartAGameWith]) { reqStartAGameWith =>
          complete {
            (sessionHandlingSPOC ? reqStartAGameWith).mapTo[RecordingStatus]
            "Order received"
          }
        }
      }
    }*/
}
