package example.org.nirmalya.experiments

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern._
import akka.util.Timeout
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{ExternalAPIParams, GameSession, HuddleGame, RecordingStatus}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

case object Fire
case object ShutYourself

/**
  * Created by nirmalya on 20/6/17.
  */
class GameSessionSPOCActor extends Actor with ActorLogging {


  implicit val executionContext = context.dispatcher

  var activeGameSessionActors: Map[GameSession, ActorRef] = Map.empty

  def receive = {

    case r: ExternalAPIParams.REQStartAGameWith =>
      val gameSession = GameSession(r.toString, "Ignore")

      if (activeGameSessionActors.isDefinedAt(gameSession))
        sender ! RecordingStatus(s"GameSession with $r is already active.")
      else {
        val originalSender = sender()
        val child = context.system.actorOf(GamePlayRecorderActor.props, gameSession.toString)
        context.watch(child)

        activeGameSessionActors = activeGameSessionActors + Tuple2(gameSession,child)

        implicit val askTimeOutDuration:Timeout = Duration(3, "seconds")
        val confirmation = (child ? HuddleGame.EvStarted(System.currentTimeMillis(), gameSession)).mapTo[RecordingStatus]
        confirmation.onComplete {
          case Success(d) =>   originalSender ! d
          case Failure(e) =>   originalSender ! RecordingStatus(e.getMessage)
        }
      }

    case Terminated(a) =>

      println("Actor $a terminated" )
      self ! ShutYourself

    case (ShutYourself) =>
      context stop(self)

    case (m: Any) =>

      println("Unknown message = [" + m + "] received!")
//      context stop(self)
  }

}

object GameSessionSPOCActor {
  def props = Props(new GameSessionSPOCActor)
}
