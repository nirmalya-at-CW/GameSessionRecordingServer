package example.org.nirmalya.experiments

import java.time.{Instant, ZoneId, ZoneOffset}

import akka.actor.{ActorLogging, FSM, LoggingFSM, Props}
import GameSessionHandlingServiceProtocol.{HuddleGame, NonExistingCompleteGamePlaySessionHistory, RedisRecordingStatus, _}
import GameSessionHandlingServiceProtocol.HuddleGame.{EvGamePlayRecordSoFarRequired, _}
import example.org.nirmalya.experiments.RedisAware.RedisButlerGameSessionRecording

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by nirmalya on 5/6/17.
  */
class GameSessionStateHolderActor(val cleanDataOnExit: Boolean,
                                  val seededWithSession: GameSession,
                                  val redisHost: String,
                                  val redisPort: Int,
                                  val maxGameSessionLifetime: FiniteDuration
                           ) extends LoggingFSM [HuddleGameSessionState, HuddleGameFSMData] with ActorLogging {

  val redisButler = new RedisButlerGameSessionRecording(redisHost, redisPort)

  // It is possible that even after a GameSession is created, the Player never plays. We don't want the GameSession to hang around,
  // needlessly. So, to deal with such a case, we schedule a reminder, so that after a expiration of a maximum timeout duration,
  // the Actor is destroyed.
  val gameNeverStartedIndicator = context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveStartedByNow)

   startWith(GameSessionYetToStartState, DataToBeginWith)

   when (HuddleGame.GameSessionYetToStartState) {

     case Event(gameInitiated: HuddleGame.EvInitiated, _) =>

          this.gameNeverStartedIndicator.cancel
          sender ! redisButler.recordInitiationOfTheGame(gameInitiated.startedAt, seededWithSession).details

          // We need to set up a separate timer, to indicate when the maximum time for this session is up
          context.system.scheduler.scheduleOnce(this.maxGameSessionLifetime, self, HuddleGame.EvGameShouldHaveEndedByNow)
          goto (HuddleGame.GameSessionIsBeingPreparedState) // using DataToEndWith(gameInitiated.gameSession)

     case Event(HuddleGame.EvGameShouldHaveStartedByNow, _ ) =>

       val sessionEndsAt = System.currentTimeMillis
       redisButler.recordEndOfTheGame(sessionEndsAt, GameSessionCreatedButNotStarted, -1, seededWithSession)
       log.info(s"GameSession = ${seededWithSession.gameSessionUUID}, not started after ${maxGameSessionLifetime} seconds, preparing for termination.")
       context.parent ! HuddleGame.EvEndedByTimeout(sessionEndsAt)
       self ! HuddleGame.EvSessionCleanupIndicated(sessionEndsAt,GameSessionCreatedButNotStarted)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsBeingPreparedState) {

     case Event(setOfQuestions: EvQuizIsFinalized, _)   =>

       sender ! redisButler.recordPreparationOfTheGame(
                               setOfQuestions.finalizedAt,
                               setOfQuestions.questionMetadata,
                               seededWithSession
                             ).details
       goto (HuddleGame.GameSessionHasStartedState)

   }

   when (HuddleGame.GameSessionHasStartedState) {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

       sender ! redisButler.recordThatAQuestionIsAnswered(
                      questionAnswered.receivedAt,
                      questionAnswered.questionAndAnswer,
                      seededWithSession
                ).details
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! redisButler.recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, seededWithSession).details
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)                     =>

       sender ! redisButler.recordAPauseOfTheGame(paused.pausedAt, seededWithSession).details
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEndedByPlayer, _)           =>

       sender ! redisButler.recordEndOfTheGame(
                               ended.endedAt,
                               ended.reasonWhySessionEnds,
                               ended.totalTimeTakenByPlayer,
                               seededWithSession
                             ).details
       self ! HuddleGame.EvSessionCleanupIndicated(ended.endedAt, ended.reasonWhySessionEnds)
       goto (HuddleGame.GameSessionIsWrappingUpState)

   }

   when (HuddleGame.GameSessionIsContinuingState)  {

     case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

       printf(s" ***Sender ${sender}")
       sender ! redisButler.recordThatAQuestionIsAnswered(
         questionAnswered.receivedAt,
         questionAnswered.questionAndAnswer,
         seededWithSession
       ).details

       stay

     case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

       sender ! redisButler.recordThatClipIsPlayed(
                             aboutToPlayClip.beganPlayingAt,
                             aboutToPlayClip.clipName,
                             seededWithSession
                           ).details
       goto (HuddleGame.GameSessionIsContinuingState)

     case Event(paused: HuddleGame.EvPaused, _)          =>

       sender ! redisButler.recordAPauseOfTheGame(paused.pausedAt, seededWithSession).details
       goto (HuddleGame.GameSessionIsPausedState)

     case Event(ended: HuddleGame.EvEndedByPlayer, _)    =>

       sender ! redisButler.recordEndOfTheGame(
                             ended.endedAt,
                             ended.reasonWhySessionEnds,
                             ended.totalTimeTakenByPlayer,
                             seededWithSession
                           ).details
       self ! HuddleGame.EvSessionCleanupIndicated(ended.endedAt, ended.reasonWhySessionEnds)
       goto (HuddleGame.GameSessionIsWrappingUpState)
   }

   when (HuddleGame.GameSessionIsPausedState)  {

      case Event(questionAnswered: HuddleGame.EvQuestionAnswered, _) =>

        sender ! redisButler.recordThatAQuestionIsAnswered(
          questionAnswered.receivedAt,
          questionAnswered.questionAndAnswer,
          seededWithSession
        ).details
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(aboutToPlayClip: HuddleGame.EvPlayingClip, _)  =>

        sender ! redisButler.recordThatClipIsPlayed(aboutToPlayClip.beganPlayingAt, aboutToPlayClip.clipName, seededWithSession).details
        goto (HuddleGame.GameSessionIsContinuingState)

      case Event(ended: HuddleGame.EvEndedByPlayer, _)                     =>

        log.debug(s"EndedByPlayer ${ended.reasonWhySessionEnds} ....")
        sender ! redisButler.recordEndOfTheGame(
                                ended.endedAt,
                                ended.reasonWhySessionEnds,
                                ended.totalTimeTakenByPlayer,
                                seededWithSession
                             ).details
        self ! HuddleGame.EvSessionCleanupIndicated(ended.endedAt, ended.reasonWhySessionEnds)
        goto (HuddleGame.GameSessionIsWrappingUpState)
    }

   when (HuddleGame.GameSessionIsWrappingUpState) {

      case Event(cleanUpRequired: EvSessionCleanupIndicated, _) =>

        val entireGameSessionRecord = redisButler.retrieveSessionHistory(seededWithSession,"SessionHistory")

        val totalScoreAndTime = (entireGameSessionRecord.elems.foldLeft((0,0)){ (accumulator,nextElem) =>

              val (scoreForQ,timeTakenForQ) =

                      nextElem match {

                        case GamePlayedTupleInREDIS(t,qNa) =>
                                (if (qNa.isCorrect) qNa.points else 0, qNa.timeTakenToAnswerAtFE)
                        case _   =>
                                (0,0)

                      }

          (accumulator._1 + scoreForQ, accumulator._2 + timeTakenForQ)
        })

        val sessionEndedAtTimezoneApplied =
          Instant.ofEpochMilli(cleanUpRequired.endedAt).atZone(ZoneId.of(seededWithSession.playedInTimezone))

        printf(s" *** Parent ${context.parent}")
        // parent == custodian of this state-holder
        context.parent ! EvGameFinishedAndScored(
                            ComputedGameSession(
                                seededWithSession.companyID,
                                seededWithSession.departmentID,
                                seededWithSession.playerID,
                                seededWithSession.gameID,
                                seededWithSession.gameType,
                                seededWithSession.gameSessionUUID,
                                seededWithSession.groupID,
                                sessionEndedAtTimezoneApplied,
                                seededWithSession.playedInTimezone,
                                totalScoreAndTime._1,
                                totalScoreAndTime._2,
                                cleanUpRequired.endingReason.toString

                            ))

        goto (HuddleGame.GameSessionIsWaitingForInstructionToClose)

   }

   when (HuddleGame.GameSessionIsWaitingForInstructionToClose) {

     case Event(HuddleGame.EvGameSessionTerminationIndicated,_) =>
         //TODO: Replace the if-check below, with a HOF
         if (!this.cleanDataOnExit) redisButler.removeGameSessionFromREDIS(seededWithSession)
         stop(FSM.Normal, DataToEndWith(System.currentTimeMillis()))

     case m: Any => log.info(s"Unknown message $m, received while waiting for instruction to close.")
                    stay
   }

  whenUnhandled {


    case Event(m, d) =>

      m match  {

        case EvGamePlayRecordSoFarRequired =>
          sender ! redisButler.extractCurrentGamePlayRecord(seededWithSession).details
          stay

        case m:EvForceEndedByManager =>

          val endAction = redisButler.recordEndOfTheGame(m.endedAt, m.reasonWhySessionEnds, -1, seededWithSession)

          if (endAction.details == "Ended") {
            log.info(s"GameSession ($seededWithSession), at ${m.endedAt}, forced to end by manager ${m.managerName}")
            sender ! endAction.details
            self ! EvSessionCleanupIndicated(m.endedAt,GameSessionEndedByManager)
            goto (GameSessionIsWrappingUpState)
          }
          else {
            log.info(s"GameSession ($seededWithSession), at (${m.endedAt}), failed to end, was instructed by manager (${m.managerName})")
            sender ! endAction.details
            stay
          }

        case EvGameShouldHaveEndedByNow =>

          val sessionEndsAt = System.currentTimeMillis
          redisButler.recordEndOfTheGame(sessionEndsAt, GameSessionEndedByTimeOut, -1, seededWithSession)

          // parent == custodian of this state-holder
          context.parent ! EvEndedByTimeout(sessionEndsAt)
          self ! HuddleGame.EvSessionCleanupIndicated(sessionEndsAt,GameSessionEndedByTimeOut)
          goto (HuddleGame.GameSessionIsWrappingUpState)

        case _  =>
          log.info(s"Unknown message of type ${m.getClass}, in ${this.stateName}")
          stay
      }

    case m: Any => log.info(s"Unknown message of type ${m.getClass}, in ${this.stateName}")
      stay


  }
  onTransition {

    case HuddleGame.GameSessionYetToStartState -> HuddleGame.GameSessionHasStartedState =>
      log.info("Transition from GameYetToStart to GameHasStarted")
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      this.redisButler.releaseConnection
      log.info(s"GameSession ${seededWithSession}, now rests in peace.")
  }

  override def postStop(): Unit = {
    super.postStop()

  }

}

object GameSessionStateHolderActor {
  def apply(shouldCleanUpREDIS: Boolean,
            sessionID: GameSession,
            redisHost: String,
            redisPort: Int,
            mxTTLGameSession: FiniteDuration
           ): Props =
    Props(new GameSessionStateHolderActor(shouldCleanUpREDIS, sessionID, redisHost, redisPort, mxTTLGameSession))
}
