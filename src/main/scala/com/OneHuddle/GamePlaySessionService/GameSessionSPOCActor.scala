package com.OneHuddle.GamePlaySessionService

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.EmittedEvents.{DataSharedWithAdminPanel, EvGameSessionLaunched}
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.ExternalAPIParams._
import com.OneHuddle.GamePlaySessionService.GameSessionHandlingServiceProtocol.{ExternalAPIParams, GameSession, HuddleGame, LiveBoardSnapshot, LiveBoardSnapshotBunch, QuestionAnswerTuple}
import com.OneHuddle.GamePlaySessionService.MariaDBAware.{GameSessionDBButlerActor, LiveBoardSnapshotDBButlerActor}
import com.OneHuddle.xAPI.ScormInformationExchangeActor

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}


/**
  * Created by nirmalya on 20/6/17.
  */
class GameSessionSPOCActor(gameSessionFinishedEventEmitter: ActorRef) extends Actor with ActorLogging {

  case object ShutYourself
  case class  InitiateLiveBoardSaveAction(l: List[LiveBoardSnapshot])


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

  val endpoint = context.system.settings.config.getConfig("LRS").getString("endpoint")
  val user     = context.system.settings.config.getConfig("LRS").getString("username")
  val password = context.system.settings.config.getConfig("LRS").getString("password")

  val lrsExchangeActor = context.actorOf(ScormInformationExchangeActor(endpoint,"",user,password),"LRSExchange")

  val dbAccessURL = context.system.settings.config.getConfig("GameSession.externalServices").getString("dbAccessURL")

  val adminPanelNotifierActor = context.actorOf(AdminPanelNotifierActor(), "AdminPanelNotifier-by-SPOC")

  val liveBoardSnapshotSavingActor = context.actorOf(LiveBoardSnapshotDBButlerActor(dbAccessURL, executionContext))

  // TODO: arrange for a specific dispatcher for the actors, accessing databases.
  val gameSessionRecordDBButler =
       context.actorOf(
         GameSessionDBButlerActor(
           dbAccessURL,
           context.system.dispatcher))

  var activeGameSessionCustodians: Map[String, ActorRef] = Map.empty

  implicit val askTimeOutDuration:Timeout = Duration(
    context.system.settings.config.
      getConfig("GameSession.maxResponseTimeLimit").
      getString("duration").
      toInt,
    "seconds")

  def receive = LoggingReceive.withLabel("SPOC") {

    case l: LiveBoardSnapshotBunch  =>
      val originalSender = sender
      log.info(s"LiveBoardSnapShotBunch.entryCount=${l.bunch.length} received.")
      self ! InitiateLiveBoardSaveAction(l.bunch)
      originalSender ! HuddleRESPLeaderboardSnapshotsReceivedAlright(
                            ExpandedMessage(200, s"${l.bunch.length} records received"),opSuccess=true
                       )


    case bunch: InitiateLiveBoardSaveAction =>

      Future{
        bunch.l.foreach (entry => liveBoardSnapshotSavingActor ! entry)
      }.onComplete {

        case Success(r)  => log.info(s"LeaderboardSnapshots, saving=success, recordsCount=${bunch.l.length}.")
        case Failure(ex) => log.info(s"LeaderboardSnapshots, saving=failure,, recordsCount=${bunch.l.length}, some or all may not have been saved. Refer to logs.")
      }

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
                  adminPanelNotifierActor,
                  lrsExchangeActor,
                  dbAccessURL
              ),
              gameSessionInfo.toString
        )
        context.watch(child) // Because we want to restart failing actors
                             // TODO: implement logic to restart!

        this.activeGameSessionCustodians = this.activeGameSessionCustodians + Tuple2(r.gameSessionUUID,child)

        adminPanelNotifierActor ! EvGameSessionLaunched(
                                        DataSharedWithAdminPanel(
                                          gameSessionInfo.companyID,
                                          gameSessionInfo.departmentID,
                                          gameSessionInfo.playerID,
                                          gameSessionInfo.gameSessionUUID)
                                  )

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
      log.debug(s"Still active sessions: ${activeGameSessionCustodians.mkString(" | ")}")
      // context stop(self)

    case (m: Any) =>

      log.info(s"Unknown message=$m, received!")
  }

}

object GameSessionSPOCActor {
  def apply(gameSessionFinishEmitter: ActorRef): Props = Props(new GameSessionSPOCActor(gameSessionFinishEmitter))
}
