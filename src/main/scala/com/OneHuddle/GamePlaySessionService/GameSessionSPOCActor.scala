package com.OneHuddle.GamePlaySessionService

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams._
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{ExternalAPIParams, GameSession, GameSessionEndedByManager, GameSessionEndedByPlayer, HuddleGame, QuestionAnswerTuple, RedisRecordingStatus}
import com.OneHuddle.GamePlaySessionService.MariaDBAware.GameSessionDBButlerActor

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}


/**
  * Created by nirmalya on 20/6/17.
  */
class GameSessionSPOCActor(gameSessionFinishedEventEmitter: ActorRef) extends Actor with ActorLogging {

  case object ShutYourself


  implicit val executionContext = context.dispatcher


  val (redisHost,redisPort) = (
    context.system.settings.config.getConfig("GameSession.redisEndPoint").getString("host"),
    context.system.settings.config.getConfig("GameSession.redisEndPoint").getInt("port")
  )

  val maxGameSessionLifetime =
    FiniteDuration(
        context.system.settings.config.getConfig("GameSession.maxGameSessionLifetime").getInt("duration"),
        TimeUnit.SECONDS
    )

  val dbAccessURL = context.system.settings.config.getConfig("GameSession.externalServices").getString("dbAccessURL")

  // TODO: arrange for a specific dispatcher for the actors, accessing databases.
  val gameSessionRecordDBButler = context.actorOf(GameSessionDBButlerActor(dbAccessURL, context.system.dispatcher))

  var activeGameSessionCustodians: Map[String, ActorRef] = Map.empty

  implicit val askTimeOutDuration:Timeout = Duration(
    context.system.settings.config.
      getConfig("GameSession.maxResponseTimeLimit").
      getString("duration").
      toInt,
    "seconds")

  def receive = LoggingReceive.withLabel("SPOC") {

    case r: ExternalAPIParams.REQStartAGameWith =>
      val gameSessionInfo = GameSession(
        r.companyID,
        r.departmentID,
        r.gameID,
        r.playerID,
        r.gameName,
        r.gameSessionUUID,
        r.groupID.getOrElse("NOTSET"),
        r.gameType,
        r.playedInTimezone
      )

      if (activeGameSessionCustodians.isDefinedAt(r.gameSessionUUID))
        sender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, s"GameSession with $r is already active."))
      else {
        val originalSender = sender()
        val child = context.actorOf(
              GameSessionCustodianActor(
                  gameSessionInfo,
                  redisHost,
                  redisPort,
                  maxGameSessionLifetime,
                  gameSessionFinishedEventEmitter,
                  dbAccessURL
              ),
              gameSessionInfo.toString
           )
        context.watch(child) // Because we want to restart failing actors
                             // TODO: implement logic to restart!

        this.activeGameSessionCustodians = this.activeGameSessionCustodians + Tuple2(r.gameSessionUUID,child)

        // TODO: We need to send an appropriate RESP object here; just a pipeTo may be inadequate.
        (child ? HuddleGame.EvInitiated(System.currentTimeMillis())).pipeTo(sender)

      }

    case r: ExternalAPIParams.REQSetQuizForGameWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

         case Some (sessionActor) =>
           (sessionActor ? HuddleGame.EvQuizIsFinalized(System.currentTimeMillis(),r.questionMetadata)).pipeTo(originalSender)
         case None  =>
           originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }


    case r: ExternalAPIParams.REQPlayAGameWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

          case Some (sessionActor) =>

              (sessionActor ? HuddleGame.EvQuestionAnswered(
                                                    System.currentTimeMillis(),
                                                    QuestionAnswerTuple(
                                                      r.questionID.toInt,
                                                      r.answerID.toInt,
                                                      r.isCorrect,
                                                      r.points,
                                                      r.timeSpentToAnswerAtFE
                                                    )
                                                  )
              )
              .recoverWith{
                case e: AskTimeoutException => Future(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"Request TimedOut")))
              }
              .pipeTo(originalSender)

          case None  =>
            originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }


    case r: ExternalAPIParams.REQPlayAClipWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

      case Some (sessionActor) =>
        (sessionActor ? HuddleGame.EvPlayingClip(System.currentTimeMillis(),r.clipName))
        .recoverWith{
            case e: AskTimeoutException => Future(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"Request TimedOut")))
        }
        .pipeTo(originalSender)
      case None =>
        originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }

    case r: ExternalAPIParams.REQPauseAGameWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

        case Some (sessionActor) =>
          (sessionActor ? HuddleGame.EvPaused(System.currentTimeMillis()))
          .recoverWith{
            case e: AskTimeoutException => Future(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"Request TimedOut")))
          }
          .pipeTo(originalSender)
        case None =>
          originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }

    case r: ExternalAPIParams.REQEndAGameWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

        case Some (sessionActor) =>
          (sessionActor ? HuddleGame.EvEndedByPlayer(
                                      System.currentTimeMillis(),
                                      r.totalTimeTakenByPlayerAtFE
                          ))
          .recoverWith{
            case e: AskTimeoutException => Future(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"Request TimedOut")))
          }
          .pipeTo(originalSender)

        case None =>
          originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }

    case r: ExternalAPIParams.REQEndAGameByManagerWith =>

      val originalSender = sender()

      activeGameSessionCustodians.get(r.sessionID) match {

        case Some (sessionActor) =>
          (sessionActor ? HuddleGame.EvForceEndedByManager(
                                      System.currentTimeMillis(),
                                      r.managerName)
          )
          .recoverWith{
            case e: AskTimeoutException => Future(HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"Request TimedOut")))
          }
          .pipeTo(originalSender)

        case None =>
          originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1300, s"No gameSession (${r.sessionID}) exists"))
      }

    // TODO: Revisit the following handler. What is the best way to remember the session that the this
    // TODO: this terminated actor has been seeded with?
    case Terminated(custodianActor) =>

      // TODO: Change the mechanism to extract SessionUUID, as below. We will shorten the Actor Name, to hold
      // TODO: only the SessionUUID in the next version.
      val custodianKey =  custodianActor.path.name.split("\\.").toIndexedSeq(5)

       activeGameSessionCustodians = activeGameSessionCustodians - custodianKey
      log.info(s"Custodian Actor ($custodianActor) terminated." )
      // context stop(self)

    case (m: Any) =>

      println("Unknown message = [" + m + "] received!")
  }

}

object GameSessionSPOCActor {
  def apply(gameSessionFinishEmitter: ActorRef): Props = Props(new GameSessionSPOCActor(gameSessionFinishEmitter))
}
