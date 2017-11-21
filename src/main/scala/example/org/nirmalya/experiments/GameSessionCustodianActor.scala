package example.org.nirmalya.experiments

import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.DBHatch.{DBActionGameSessionRecord, DBActionInsert, DBActionInsertSuccess, DBActionOutcome}
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.ExternalAPIParams._
import example.org.nirmalya.experiments.GameSessionHandlingServiceProtocol.{DBHatch, DespatchedToLeaderboardAcknowledgement, GameSession, GameSessionEndedByManager, GameSessionEndedByPlayer, GameSessionEndedByTimeOut, HuddleGame, LeaderboardConsumableData}
import example.org.nirmalya.experiments.MariaDBAware.GameSessionDBButlerActor

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
                                  val leaderBoardInformerActor: ActorRef,
                                  val dbAccessURL: String

      ) extends Actor with ActorLogging {

  implicit val askTimeOutDuration:Timeout = Duration(
    context.system.settings.config.
      getConfig("GameSession.maxResponseTimeLimit").
      getString("duration").
      toInt,
      "seconds")

  // TODO: Make this a construction parameter
  val applicableZoneDesc =  "Asia/Calcutta"

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
  val gameSessionRecordDBButler = context.system.actorOf(GameSessionDBButlerActor(context.dispatcher))
  context.watch(gameSessionStateHolderActor)

  def gamePlayIsOnState: Receive = LoggingReceive.withLabel("gamePlayIsOnState") {

    case ev: HuddleGame.EvQuizIsFinalized =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvQuestionAnswered =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvPlayingClip  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvPaused  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

    case ev: HuddleGame.EvEndedByPlayer  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      log.debug(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends. Cause: Player finished.")
      context.become(gamePlayIsBarredState)

    case ev: HuddleGame.EvForceEndedByManager  =>

      val originalSender = sender()
      val confirmation = (gameSessionStateHolderActor ? ev).mapTo[String]

      confirmation.onComplete {
        case Success(d) => originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2200, d))
        case Failure(e) => originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends. Cause: Manager forced termination.")
      context.become(gamePlayIsBarredState)


    case ev: HuddleGame.EvEndedByTimeout =>

      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, ends at ${ev.endedAt}. Cause: Timed out.")
      context.become(gamePlayIsBarredState)

  }

  def gamePlayIsBarredState: Receive = LoggingReceive.withLabel("gamePlayIsBarredState") {

    case ev: HuddleGame.EvGameFinishedAndScored =>

      gameSessionRecordDBButler ! ev.computedGameSession

      var expectedConfirmations: Set[String] = Set(DBHatch.DBActionInsertSuccess.toString)


      // Only sessions which have been ended by a Player, are considered for further computation and
      // for being offered to Leaderboard
      if (ev.computedGameSession.endedBecauseOf == GameSessionEndedByPlayer.toString) {

        println(s" #### emitter actor ${this.leaderBoardInformerActor}")
        val infoForLeaderBoard = LeaderboardConsumableData(
          gameSessionInfo.companyID,
          gameSessionInfo.departmentID,
          gameSessionInfo.gameID,
          gameSessionInfo.playerID,
          gameSessionInfo.groupID,
          gameSessionInfo.gameSessionUUID,
          ev.computedGameSession.totalPointsObtained
        )

        leaderBoardInformerActor ! infoForLeaderBoard
        expectedConfirmations = expectedConfirmations +  DespatchedToLeaderboardAcknowledgement.toString

      }

      context.become(expectingConfirmationsFromDBLBServicesState(expectedConfirmations))

  }

  def expectingConfirmationsFromDBLBServicesState(allTheseConfirmations: Set[String]): Receive =
    LoggingReceive.withLabel("expectingConfirmationsFromDBLBServicesState") {

    case o: DBHatch.DBActionInsertSuccess =>
      log.info(s"${o.i} GameSession records inserted in DB")


      val updatedConfirmations = allTheseConfirmations.filter(e => e != DBHatch.DBActionInsertSuccess.toString())
      println(s" **** updatedConfirmations $updatedConfirmations")

      if (updatedConfirmations.isEmpty) {


        //self ! CloseGameSession
        context.become(gettingReadyToCloseSessionState)
        self ! CloseGameSession
      }
      else
        context.become(expectingConfirmationsFromDBLBServicesState(updatedConfirmations))

    case DespatchedToLeaderboardAcknowledgement =>
      log.info(s"GameSession ${gameSessionInfo.gameSessionUUID}, update sent to Leaderboard Service")

      val updatedConfirmations = allTheseConfirmations.filter(e => e != DespatchedToLeaderboardAcknowledgement.toString)
      println(s" **** updatedConfirmations $updatedConfirmations")
      if (updatedConfirmations.isEmpty) {
        //self ! CloseGameSession
        context.become(gettingReadyToCloseSessionState)
        self ! CloseGameSession
      }
      else
        context.become(expectingConfirmationsFromDBLBServicesState(updatedConfirmations))

    case m: Any  =>
      log.debug(s"Unknown message, $m, while expecting confirmation from DB and Leaderboard intimation Service.")
      context.become(expectingConfirmationsFromDBLBServicesState(allTheseConfirmations))

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
            originalSender ! RESPGameSessionBodyWhenSuccessful(ExpandedMessage(2100, d),Some(Map("gameSessionID" -> gameSessionInfo.gameSessionUUID)))
          case Failure(e) =>
            originalSender ! RESPGameSessionBodyWhenFailed(ExpandedMessage(1200, e.getMessage))
      }

      context.become(gamePlayIsOnState)

  }

}

object GameSessionCustodianActor {
  def apply(gameSessionInfo: GameSession,
            redisHost: String,
            redisPort: Int,
            maxGameSessionLifetime: FiniteDuration,
            sessionCompletionEvReceiverActor: ActorRef,
            dbAccessURL: String): Props =
    Props(new GameSessionCustodianActor(
      gameSessionInfo,
      redisHost,
      redisPort,
      maxGameSessionLifetime,
      sessionCompletionEvReceiverActor,
      dbAccessURL
    ))
}
