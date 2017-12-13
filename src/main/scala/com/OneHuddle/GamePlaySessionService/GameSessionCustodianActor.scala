package com.OneHuddle.GamePlaySessionService

import java.time.{LocalDateTime, ZoneId}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams.{ExpandedMessage, HuddleRESPGameSessionBodyWhenFailed, HuddleRESPGameSessionBodyWhenSuccessful}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.DBHatch.{DBActionGameSessionRecord, DBActionInsert, DBActionInsertSuccess, DBActionOutcome}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.EmittedEvents._
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{ComputedGameSessionRegSP, DBHatch, DespatchedToLiveboardAcknowledgement, GameSession, GameSessionEndedByPlayer, HuddleGame, LiveboardConsumableData, PlayerPerformanceRecordSP}
import com.OneHuddle.GamePlaySessionService.MariaDBAware.{GameSessionDBButlerActor, PlayerPerformanceDBButlerActor}
import com.OneHuddle.xAPI.UpdateLRSGamePlayed

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by nirmalya on 11/10/17.
  */
class GameSessionCustodianActor (
                                  val gameSessionInfo: GameSession,
                                  val redisHost: String,
                                  val redisPort: Int,
                                  val maxGameSessionLifetime: FiniteDuration,
                                  val liveBoardInformerActor: ActorRef,
                                  val adminPanelNotifierActor: ActorRef,
                                  val lrsExchangeActor: ActorRef,
                                  val dbAccessURL: String

      ) extends Actor with ActorLogging {

  implicit val askTimeOutDuration:Timeout = Duration(
    context.system.settings.config.
      getConfig("GameSession.maxResponseTimeLimit").
      getString("duration").
      toInt,
      "seconds")

  val playedInTimeZone = gameSessionInfo.playedInTimezone

  case object CloseGameSession

  val gameSessionStateHolderActor =
    context.actorOf(
      GameSessionStateHolderActor(
        true,
        gameSessionInfo,
        redisHost,
        redisPort,
        maxGameSessionLifetime
      ), gameSessionInfo.toString)

  //TODO: instead of making this a direct child, we should create a router and encapsulate that with HTTP apis.
  //TODO: we should pass a different dispatcher (properly configured) to the actor below; currently it is the same dispatcher
  val gameSessionRecordDBButlerActor = context.system.actorOf(GameSessionDBButlerActor(dbAccessURL, context.dispatcher))
  context.watch(gameSessionStateHolderActor)

  val playerPerformanceDBButlerActor = context.system.actorOf(PlayerPerformanceDBButlerActor(dbAccessURL, context.dispatcher))
  context.watch(playerPerformanceDBButlerActor)

  def gamePlayIsOnState: Receive = LoggingReceive.withLabel("gamePlayIsOnState") {

    case ev: HuddleGame.EvQuizIsFinalized =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvQuestionAnswered =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvPlayingClip  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvPaused  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvEndedByPlayer  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) =>
          adminPanelNotifierActor ! EvGameSessionFinishedByPlayer(
                                      DataSharedWithAdminPanel(
                                        gameSessionInfo.companyID,
                                        gameSessionInfo.departmentID,
                                        gameSessionInfo.playerID,
                                        gameSessionInfo.gameSessionUUID)
                                    )

          originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))

        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      log.debug(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends. Cause: Player finished.")
      context.become(gamePlayIsBarredState)

    case ev: HuddleGame.EvForceEndedByManager  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {

        case Success(d) =>
          adminPanelNotifierActor ! EvGameSessionTerminatedByManager(
            DataSharedWithAdminPanel(
              gameSessionInfo.companyID,
              gameSessionInfo.departmentID,
              gameSessionInfo.playerID,
              gameSessionInfo.gameSessionUUID),ev.managerName
          )
          println(s"|**********| message sent to adminPanelNotifierActor(${adminPanelNotifierActor.path})")
          originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))

        case Failure(e) => originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends. Cause: Manager ${ev.managerName} forced termination.")
      context.become(gamePlayIsBarredState)


    case ev: HuddleGame.EvEndedByTimeout =>

      adminPanelNotifierActor ! EvGameSessionTerminatedByTimeOut(
        DataSharedWithAdminPanel(
          gameSessionInfo.companyID,
          gameSessionInfo.departmentID,
          gameSessionInfo.playerID,
          gameSessionInfo.gameSessionUUID)
      )
      println(s"|**********| message sent to adminPanelNotifierActor(${adminPanelNotifierActor.path})")
      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends at ${ev.endedAt}. Cause: Timed out.")
      context.become(gamePlayIsBarredState)

  }

  def gamePlayIsBarredState: Receive = LoggingReceive.withLabel("gamePlayIsBarredState") {

    case ev: HuddleGame.EvGameFinishedAndScored =>

      val playerPerformanceRecordSP = PlayerPerformanceRecordSP(
        ev.computedGameSession.companyID,
        ev.computedGameSession.departmentID,
        ev.computedGameSession.playerID,
        ev.computedGameSession.gameID,
        ev.computedGameSession.groupID,
        ev.computedGameSession.finishedAt,
        ev.computedGameSession.timezoneApplicable,
        ev.computedGameSession.totalPointsObtained,
        ev.computedGameSession.timeTakenToFinish
      )

      lrsExchangeActor ! UpdateLRSGamePlayed(playerPerformanceRecordSP)

      gameSessionRecordDBButlerActor ! ev.computedGameSession

      var expectedConfirmationsFromActors = Set(gameSessionRecordDBButlerActor)

      playerPerformanceDBButlerActor ! playerPerformanceRecordSP
      expectedConfirmationsFromActors = expectedConfirmationsFromActors + playerPerformanceDBButlerActor

      // Only sessions which have been ended by a Player, are considered for further computation and
      // for being offered to Liveboard
      if (ev.computedGameSession.endedBecauseOf == GameSessionEndedByPlayer.toString) {

        val infoForLiveBoard = LiveboardConsumableData(
          gameSessionInfo.companyID,
          gameSessionInfo.departmentID,
          gameSessionInfo.gameID,
          gameSessionInfo.playerID,
          gameSessionInfo.groupID,
          gameSessionInfo.gameSessionUUID,
          ev.computedGameSession.totalPointsObtained
        )

        liveBoardInformerActor ! infoForLiveBoard
        expectedConfirmationsFromActors = expectedConfirmationsFromActors +  liveBoardInformerActor

      }

      context.become(expectingConfirmationsFromDownstreamServicesState(expectedConfirmationsFromActors))

  }

  private def expectingConfirmationsFromDownstreamServicesState(allTheseConfirmations: Set[ActorRef]): Receive =
    LoggingReceive.withLabel("expectingConfirmationsFromDBLBServicesState") {

    case dbActionStatus: DBHatch.DBActionInsertSuccess =>

      val originalSender = sender()

      log.info(s"GameSession records insertion status: Success, from ${originalSender.path.name}")

      val updatedConfirmations = allTheseConfirmations.filter(e => e != originalSender)

      if (updatedConfirmations.isEmpty) {
        self ! CloseGameSession
        context.become(gettingReadyToCloseSessionState)
      }
      else
        context.become(expectingConfirmationsFromDownstreamServicesState(updatedConfirmations))

    case DespatchedToLiveboardAcknowledgement =>
      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, update sent to Liveboard Service")

      val updatedConfirmations = allTheseConfirmations.filter(e => e != DespatchedToLiveboardAcknowledgement.toString)

      if (updatedConfirmations.isEmpty) {
        self ! CloseGameSession
        context.become(gettingReadyToCloseSessionState)

      }
      else
        context.become(expectingConfirmationsFromDownstreamServicesState(updatedConfirmations))

    case m: Any  =>
      log.debug(s"Unknown message, $m, while expecting confirmation from DB and Liveboard intimation Service.")
      context.become(expectingConfirmationsFromDownstreamServicesState(allTheseConfirmations))

  }

  def gettingReadyToCloseSessionState: Receive =
    LoggingReceive.withLabel("gettingReadyToCloseSessionState") {

    case CloseGameSession =>

      this.gameSessionStateHolderActor ! HuddleGame.EvGameSessionTerminationIndicated

    case Terminated(thisActor) =>

      if (thisActor == this.gameSessionStateHolderActor)
        log.info(s"StateHolder ${thisActor.path.name} has terminated.")
      else
        log.warning(s"Unwatched actor ${thisActor.path.name} has terminated")

      context.stop(self)

  }

  override def receive = LoggingReceive.withLabel("Entry Point") {

    case ev: HuddleGame.EvInitiated =>

      val originalSender = sender
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]
      confirmation.onComplete {
          case Success(d) =>
            originalSender ! HuddleRESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, d),Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID)))
          case Failure(e) =>
            originalSender ! HuddleRESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      context.become(gamePlayIsOnState)

  }

}

object GameSessionCustodianActor {
  def apply(gameSessionInfo: GameSession,
            redisHost: String,
            redisPort: Int,
            maxGameSessionLifetime: FiniteDuration,
            liveBoardInformerActor: ActorRef,
            adminPanelNotifierActor: ActorRef,
            lrsExchangeActor: ActorRef,
            dbAccessURL: String): Props =
    Props(new GameSessionCustodianActor(
      gameSessionInfo,
      redisHost,
      redisPort,
      maxGameSessionLifetime,
      liveBoardInformerActor,
      adminPanelNotifierActor,
      lrsExchangeActor,
      dbAccessURL
    ))
}
