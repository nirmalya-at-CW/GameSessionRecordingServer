package example.org.nirmalya.experiments

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, REQStartAGameWith, RESPGameSessionBody}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{ExternalAPIParams, GameSession, HuddleGame, RecordingStatus}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * Created by nirmalya on 11/10/17.
  */
class GameSessionCustodianActor (
          val reqToStartAGameSession: REQStartAGameWith,
          val redisHost: String,
          val redisPort: Int,
          val maxGameSessionLifetime: FiniteDuration,
          val dbAccessURL: Option[String] = None,
          val sessionCompletionEvReceiverActor: ActorRef

      ) extends Actor with ActorLogging {


  val gameSessionStateHolderActor =
    context.actorOf(
      GameSessionStateHolderActor(
        true,
        GameSession(reqToStartAGameSession.toString),
        redisHost,
        redisPort,
        maxGameSessionLifetime,
        sessionCompletionEvReceiverActor
      ), GameSession(reqToStartAGameSession.toString).toString)

  val gameSessionEndComputingActor = context.actorOf(GameSessionEndComputingActor.apply)

  context.watch(gameSessionStateHolderActor)
  context.watch(gameSessionEndComputingActor)

  override def receive = {

    case r: ExternalAPIParams.REQStartAGameWith =>

      val originalSender = sender()
      val gameSession = GameSession(r.toString)

      val confirmation =
        (gameSessionStateHolderActor ? HuddleGame.EvInitiated(System.currentTimeMillis(), gameSession)).mapTo[RecordingStatus]
        confirmation.onComplete {
          case Success(d) =>
            originalSender ! RESPGameSessionBody(
              true,
              ExpandedMessage(2100, d.details),
              Some(Map("gameSessionID" -> gameSession.toString))
            )
          case Failure(e) =>
            originalSender ! RESPGameSessionBody(
              false,
              ExpandedMessage(1200, e.getMessage)
            )
        }

    case r: ExternalAPIParams.REQSetQuizForGameWith =>

      val originalSender = sender()
      val gameSession = GameSession(r.toString)
      val confirmation = (gameSessionStateHolderActor ? HuddleGame.EvQuizIsFinalized(
                                                          System.currentTimeMillis(),
                                                          r.questionMetadata,
                                                          gameSession
                                                        )
                         ).mapTo[RecordingStatus]
      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBody(true, ExpandedMessage(2200, d.details))
        case Failure(e) => originalSender ! RESPGameSessionBody(false,ExpandedMessage(1200, e.getMessage))
      }
  }


}
