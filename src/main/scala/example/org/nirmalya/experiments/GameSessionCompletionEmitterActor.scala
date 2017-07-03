package example.org.nirmalya.experiments

import akka.actor.{Actor, ActorLogging, Props}
import com.mashape.unirest.http.Unirest
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.EmitWhenGameSessionIsFinished

import scala.concurrent.Future
import scala.util.{Failure, Success}


/**
  * Created by nirmalya on 28/6/17.
  */
class GameSessionCompletionEmitterActor (consumingEndpoints: List[String]) extends Actor with ActorLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  def receive = {

    case EmitWhenGameSessionIsFinished(whatToEmit) =>

      if (consumingEndpoints.isEmpty)

        log.info(s"No endpoints supplied, cannot emit ($whatToEmit)!")

      else {

        val f = consumingEndpoints.map(nextEndPoint => {
          val completeReqString =
            StringBuilder
              .newBuilder
              .append(nextEndPoint)
              .append("/")
              .append(whatToEmit)
              .toString()
          val callAction = Future {
            Unirest.put(completeReqString).asString // TODO: should be a toJSON, finally!
          }
          (nextEndPoint, callAction)
        }).map(endPointCallActionPair => {
          endPointCallActionPair._2.onComplete {
            case Success(x) => log.info(s"Emitted: $whatToEmit, to ($endPointCallActionPair._1), response (${x.getStatusText})")
            case Failure(y) => log.info(s"Failed:  $whatToEmit, to ($endPointCallActionPair._1), reason (${y.getMessage})")
          }
        })
      }

    case x: Any => log.info("Unknown message ($x) received")
  }

}

object GameSessionCompletionEmitterActor {
  def apply(consumingEndpoints: List[String]): Props = Props(new GameSessionCompletionEmitterActor(consumingEndpoints))
}
